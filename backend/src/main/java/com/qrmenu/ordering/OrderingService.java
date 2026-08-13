package com.qrmenu.ordering;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.ProductNotOrderableException;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.customersession.CustomerSessionService;
import com.qrmenu.customersession.TableVisit;
import com.qrmenu.menu.BranchProduct;
import com.qrmenu.menu.BranchProductAvailability;
import com.qrmenu.menu.MenuService;
import com.qrmenu.menu.Product;
import com.qrmenu.menu.ProductOption;
import com.qrmenu.menu.ProductOptionGroup;
import com.qrmenu.menu.SelectionType;
import com.qrmenu.notification.OrderStatusNotifier;
import com.qrmenu.notification.OrderStatusUpdate;
import com.qrmenu.ordering.repository.OrderItemOptionRepository;
import com.qrmenu.ordering.repository.OrderItemRepository;
import com.qrmenu.ordering.repository.OrderRepository;
import com.qrmenu.ordering.web.dto.AddCartItemRequest;
import com.qrmenu.shared.Money;
import com.qrmenu.shared.outbox.OutboxEventWriter;
import com.qrmenu.tenant.DeliveryModel;
import com.qrmenu.tenant.RestaurantTable;
import com.qrmenu.tenant.TenantService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the ordering module (Section 3: "Sepet ayrı bir modül değil" - the
 * DRAFT Order itself is the cart). Every mutation re-validates product/branch-product/
 * option data straight from the menu module - the client-sent price is never trusted
 * (Section 9, Milestone 4: "Product+BranchProduct birleşik revalidasyon").
 */
@Service
public class OrderingService {

    private static final List<OrderStatus> PAYABLE_STATUSES = List.of(OrderStatus.DRAFT, OrderStatus.PAYMENT_FAILED);

    /**
     * Gap-analysis #8 reporting (Section 13.4: "Payment + immutable Order/OrderItem
     * snapshots"): every order that reached a status only reachable after a successful
     * payment webhook - including REJECTED_BY_STORE, whose paid amount still counts
     * toward gross sales and is offset by the cashier-triggered full refund, same
     * "gross sales, then subtract refund" split as Section 13.1's brüt/net satış pair.
     * DRAFT/AWAITING_PAYMENT/PAYMENT_FAILED/CANCELLED never took a payment, so they're
     * excluded rather than passed in by the caller - same "status logic stays inside
     * OrderingService" discipline as countOrdersSince.
     */
    private static final List<OrderStatus> PAID_ORDER_STATUSES = List.of(
            OrderStatus.AWAITING_STORE_ACCEPTANCE,
            OrderStatus.IN_KITCHEN,
            OrderStatus.READY,
            OrderStatus.COMPLETED,
            OrderStatus.REJECTED_BY_STORE);

    private final CustomerSessionService customerSessionService;
    private final MenuService menuService;
    private final TenantService tenantService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemOptionRepository orderItemOptionRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderStatusNotifier orderStatusNotifier;
    private final AuditService auditService;

    public OrderingService(
            CustomerSessionService customerSessionService,
            MenuService menuService,
            TenantService tenantService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderItemOptionRepository orderItemOptionRepository,
            OutboxEventWriter outboxEventWriter,
            OrderNumberGenerator orderNumberGenerator,
            OrderStatusNotifier orderStatusNotifier,
            AuditService auditService) {
        this.customerSessionService = customerSessionService;
        this.menuService = menuService;
        this.tenantService = tenantService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemOptionRepository = orderItemOptionRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.orderNumberGenerator = orderNumberGenerator;
        this.orderStatusNotifier = orderStatusNotifier;
        this.auditService = auditService;
    }

    @Transactional
    public CartView addItem(UUID tableVisitId, UUID callerSessionId, AddCartItemRequest request) {
        TableVisit visit = customerSessionService.getOwnedTableVisit(tableVisitId, callerSessionId);

        Product product = menuService.getProductForBusiness(visit.getBusinessId(), request.productId());
        if (!product.isActive()) {
            throw new ProductNotOrderableException("Product is not orderable: " + product.getId());
        }
        BranchProduct branchProduct = menuService
                .getBranchProduct(visit.getBranchId(), product.getId())
                .filter(bp -> bp.getAvailability() == BranchProductAvailability.AVAILABLE)
                .orElseThrow(() ->
                        new ProductNotOrderableException("Product is not orderable at this branch: " + product.getId()));

        List<ProductOption> selectedOptions = resolveAndValidateOptions(product.getId(), request.selectedOptionIdsOrEmpty());

        long effectiveBasePrice = branchProduct.getPriceOverrideMinorUnits() != null
                ? branchProduct.getPriceOverrideMinorUnits()
                : product.getBasePriceMinorUnits();
        Money unitPrice = selectedOptions.stream()
                .map(option -> Money.ofMinorUnits(option.getPriceDeltaMinorUnits()))
                .reduce(Money.ofMinorUnits(effectiveBasePrice), Money::plus);
        Money lineTotal = unitPrice.multipliedBy(request.quantity());

        CreateOrReuseDraftResult draftResult = createOrReuseDraftOrder(visit);
        CustomerOrder order = draftResult.order();

        OrderItem item = orderItemRepository.save(new OrderItem(
                order.getId(),
                product.getId(),
                product.getName(),
                unitPrice.amountMinorUnits(),
                request.quantity(),
                lineTotal.amountMinorUnits()));
        for (ProductOption option : selectedOptions) {
            orderItemOptionRepository.save(
                    new OrderItemOption(item.getId(), option.getId(), option.getName(), option.getPriceDeltaMinorUnits()));
        }

        recalculateOrderTotal(order);
        return buildCartView(order, draftResult.newlyIssuedTrackingToken());
    }

    @Transactional
    public CartView removeItem(UUID tableVisitId, UUID callerSessionId, UUID orderItemId) {
        customerSessionService.getOwnedTableVisit(tableVisitId, callerSessionId);
        CustomerOrder order = orderRepository
                .findByTableVisitIdAndStatus(tableVisitId, OrderStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("No active cart for this table visit: " + tableVisitId));
        OrderItem item = orderItemRepository
                .findByIdAndOrderId(orderItemId, order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + orderItemId));

        orderItemOptionRepository.deleteAllByOrderItemId(item.getId());
        orderItemRepository.delete(item);
        recalculateOrderTotal(order);
        return buildCartView(order, null);
    }

    @Transactional(readOnly = true)
    public Optional<CartView> getCart(UUID tableVisitId, UUID callerSessionId) {
        customerSessionService.getOwnedTableVisit(tableVisitId, callerSessionId);
        return orderRepository
                .findByTableVisitIdAndStatus(tableVisitId, OrderStatus.DRAFT)
                .map(order -> buildCartView(order, null));
    }

    /**
     * Entry point for the payment module (Section 9, Milestone 5): resolves the
     * caller's payable order (DRAFT, or PAYMENT_FAILED for a retry), runs the
     * authoritative pre-payment checks - non-empty cart, branch ordering-enabled +
     * opening hours (TenantService.assertOrderingCurrentlyAllowed) - and transitions it
     * to AWAITING_PAYMENT. The order's own price/availability data was already
     * backend-computed at add-to-cart time (Section 9, Milestone 4); this method does
     * not re-run that revalidation, only the ordering-allowed gate that is new in this
     * milestone.
     */
    @Transactional
    public CustomerOrder beginPaymentForDraftOrder(UUID tableVisitId, UUID callerSessionId) {
        customerSessionService.getOwnedTableVisit(tableVisitId, callerSessionId);
        CustomerOrder order = orderRepository
                .findFirstByTableVisitIdAndStatusIn(tableVisitId, PAYABLE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("No payable cart for this table visit: " + tableVisitId));
        if (orderItemRepository.findAllByOrderId(order.getId()).isEmpty()) {
            throw new IllegalStateException("Cannot start payment for an empty cart: " + order.getId());
        }
        tenantService.assertOrderingCurrentlyAllowed(order.getBusinessId(), order.getBranchId());
        order.markAwaitingPayment();
        return orderRepository.save(order);
    }

    /**
     * Loads an order and proves the caller's TableVisit owns it - used by the payment
     * module to scope payment-status reads/mock-outcome triggers the same way cart
     * operations are scoped (Section 5: cookie-based ownership, 404 on mismatch).
     */
    @Transactional(readOnly = true)
    public CustomerOrder getOwnedOrder(UUID tableVisitId, UUID callerSessionId, UUID orderId) {
        customerSessionService.getOwnedTableVisit(tableVisitId, callerSessionId);
        CustomerOrder order =
                orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getTableVisitId().equals(tableVisitId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return order;
    }

    /**
     * Called by the payment module's webhook handling once a payment SUCCEEDED (Section
     * 2, mock flow step 6). Gap-analysis #1: no longer moves straight to IN_KITCHEN -
     * assigns the readable order number and lands the order in
     * AWAITING_STORE_ACCEPTANCE, where it waits for an explicit cashier ACCEPT/REJECT
     * (acceptOrder/rejectOrder below). The status transition and the outbox write
     * happen in the same transaction as the caller's
     * (PaymentWebhookService.handleIncomingWebhook is itself @Transactional).
     */
    @Transactional
    public void markOrderAwaitingStoreAcceptance(UUID orderId) {
        CustomerOrder order =
                orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        order.markAwaitingStoreAcceptance();
        int orderNumber = orderNumberGenerator.nextOrderNumber(order.getBranchId(), LocalDate.now(ZoneOffset.UTC));
        order.assignOrderNumber(orderNumber);
        orderRepository.save(order);
        outboxEventWriter.write(
                "Order",
                order.getId(),
                "OrderPaid",
                new OrderPaidEvent(
                        order.getId(), order.getBusinessId(), order.getBranchId(), order.getTableVisitId(), order.getTotalMinorUnits()));
        notifyOrderStatusChanged(order);
    }

    @Transactional
    public void markOrderPaymentFailed(UUID orderId) {
        CustomerOrder order =
                orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        order.markPaymentFailed();
        orderRepository.save(order);
    }

    /**
     * Section 6: kasa ACCEPT - AWAITING_STORE_ACCEPTANCE -> IN_KITCHEN. Every in-progress
     * queue entry point (getKitchenQueue, below) only ever reads IN_KITCHEN orders, so
     * this is the one and only door into that queue now. Product decision: there is no
     * separate item-level kitchen decision anymore - accepting the order auto-accepts
     * the full ordered quantity of every item (OrderItem.acceptFully), since the cashier
     * ACCEPT is the single decision point in the flow (Section 6:
     * AWAITING_STORE_ACCEPTANCE -> ACCEPT -> PREPARING -> READY -> COMPLETED).
     */
    @Transactional
    public CustomerOrder acceptOrder(UUID branchId, UUID orderId, UUID actorStaffUserId) {
        CustomerOrder order = requireOrderInBranch(branchId, orderId);
        order.markInKitchen();
        orderRepository.save(order);
        for (OrderItem item : orderItemRepository.findAllByOrderId(order.getId())) {
            item.acceptFully();
            orderItemRepository.save(item);
        }
        auditService.record(order.getBusinessId(), actorStaffUserId, "Order", order.getId(), "ACCEPTED_BY_STORE", null);
        notifyOrderStatusChanged(order);
        return order;
    }

    /**
     * Section 6: kasa REJECT - AWAITING_STORE_ACCEPTANCE -> REJECTED_BY_STORE. Does NOT
     * itself issue the refund (see RefundService.requestFullRefund) - kept as two
     * separate calls, orchestrated by the controller (OrderControlController), so
     * ordering never has to depend on refund (which already depends on ordering -
     * avoids the ordering->refund->payment->ordering cycle, same reasoning as the
     * Milestone 7 refund-on-reject decision this supersedes) and so a refund failure is
     * visible as its own state rather than silently rolling back the rejection.
     */
    @Transactional
    public CustomerOrder rejectOrder(UUID branchId, UUID orderId, String reasonCode, String note, UUID actorStaffUserId) {
        CustomerOrder order = requireOrderInBranch(branchId, orderId);
        order.rejectByStore(reasonCode, note);
        orderRepository.save(order);
        auditService.record(
                order.getBusinessId(), actorStaffUserId, "Order", order.getId(), "REJECTED_BY_STORE",
                Map.of("reasonCode", reasonCode == null ? "" : reasonCode));
        notifyOrderStatusChanged(order);
        return order;
    }

    /** Section 10.1: kasa dashboard'un "yeni ödenmiş/onay bekleyen siparişler" listesi. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<KitchenQueueOrderView> getPendingStoreAcceptanceOrders(UUID branchId) {
        tenantService.requireBusinessIdForBranch(branchId);
        List<CustomerOrder> orders =
                orderRepository.findAllByBranchIdAndStatusOrderByOrderNumberAsc(branchId, OrderStatus.AWAITING_STORE_ACCEPTANCE);
        return buildKitchenQueueViews(orders);
    }

    /**
     * KDS initial load (Section 3: "KDS sorgu ... modeli") - every IN_KITCHEN order for
     * a branch, oldest first. REPEATABLE_READ (not the default READ COMMITTED): this
     * method issues several separate SELECTs (orders, then each order's items/options)
     * that together form one logical snapshot - under READ COMMITTED, a concurrent
     * kitchen mutation could commit in the gap between two of those SELECTs and this
     * method would return an order and its items from two different points in time
     * (e.g. order still IN_KITCHEN from before, items already showing READY from
     * after). REPEATABLE_READ pins the whole transaction to one consistent snapshot
     * (found live via manual testing - see getOrderTrackingView, which had the exact
     * same bug).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<KitchenQueueOrderView> getKitchenQueue(UUID branchId) {
        tenantService.requireBusinessIdForBranch(branchId);
        List<CustomerOrder> orders = orderRepository.findAllByBranchIdAndStatusOrderByOrderNumberAsc(branchId, OrderStatus.IN_KITCHEN);
        return buildKitchenQueueViews(orders);
    }

    /**
     * Section 6/8: Kasa'nın "Hazır - Teslim Bekliyor" bölümü - branch teslimat
     * modelinden bağımsız (WAITER_DELIVERY dahil) her READY sipariş, item detayıyla
     * birlikte. Kasa'nın kabul ettiği bir sipariş PREPARING -> READY olunca
     * getKitchenQueue'dan (yalnızca IN_KITCHEN) düşer - bu metot olmadan READY bir
     * sipariş, teslim/tamamlama işaretlenene kadar Kasa ekranında hiçbir yerde
     * görünmezdi.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<KitchenQueueOrderView> getReadyOrders(UUID branchId) {
        tenantService.requireBusinessIdForBranch(branchId);
        List<CustomerOrder> orders = orderRepository.findAllByBranchIdAndStatusOrderByOrderNumberAsc(branchId, OrderStatus.READY);
        return buildKitchenQueueViews(orders);
    }

    /**
     * Section 4, screen #8 "Pickup board": every READY order for a CUSTOMER_PICKUP
     * branch, oldest first - only what a kiosk display needs (order numbers), never
     * item/price detail. Public/unauthenticated like the menu endpoint (Section 5's
     * threat model is about placing orders under someone else's table, not about a
     * branchId-scoped read of order numbers already announced on-screen); returns an
     * empty list for a WAITER_DELIVERY branch rather than an error, so a kiosk pointed
     * at the wrong branch just shows nothing instead of failing outright.
     */
    @Transactional(readOnly = true)
    public List<CustomerOrder> getPickupBoard(UUID branchId) {
        if (tenantService.getDeliveryModel(branchId) != DeliveryModel.CUSTOMER_PICKUP) {
            return List.of();
        }
        return orderRepository.findAllByBranchIdAndStatusOrderByOrderNumberAsc(branchId, OrderStatus.READY);
    }

    /**
     * IN_KITCHEN -> READY (Section 6/8): a single order-level action, not an item-by-item
     * rollup - product decision removed the separate per-item ready/served steps, so the
     * cashier marks the whole order ready at once and every item moves with it.
     */
    @Transactional
    public KitchenQueueOrderView markOrderReady(UUID branchId, UUID orderId) {
        CustomerOrder order = requireOrderInBranch(branchId, orderId);
        for (OrderItem item : orderItemRepository.findAllByOrderId(order.getId())) {
            item.markReady();
            orderItemRepository.save(item);
        }
        order.markReady();
        orderRepository.save(order);
        notifyOrderStatusChanged(order);
        return buildKitchenQueueView(order);
    }

    /**
     * READY -> COMPLETED (Section 6, Milestone 9): staff confirms the order was
     * delivered (WAITER_DELIVERY) or picked up (CUSTOMER_PICKUP). Removes it from the
     * pickup board and the branch's active-orders views. Every item moves to SERVED in
     * lockstep (same "single order-level action" reasoning as markOrderReady) so
     * customer-facing tracking (OrderTrackingController) stays accurate without a
     * separate per-item staff step.
     */
    @Transactional
    public CustomerOrder completeOrder(UUID branchId, UUID orderId) {
        CustomerOrder order = requireOrderInBranch(branchId, orderId);
        for (OrderItem item : orderItemRepository.findAllByOrderId(order.getId())) {
            item.markServed();
            orderItemRepository.save(item);
        }
        order.markCompleted();
        orderRepository.save(order);
        notifyOrderStatusChanged(order);
        return order;
    }

    /**
     * Section 5: read-only, cookie/session-independent order lookup by
     * orderTrackingToken - the raw token is never stored, only its hash (Section 2).
     * REPEATABLE_READ: this reads the order and its items as two separate SELECTs:
     * under the default READ COMMITTED, a concurrent order-level mutation committing in
     * the gap between them (e.g. markOrderReady) could be reflected in one SELECT but
     * not the other, producing an internally inconsistent response - an order still
     * reported IN_KITCHEN whose item is already READY. Confirmed live while manually
     * testing the SSE-driven tracking page: the customer-facing status badge lagged
     * behind the item-level status it was rendered alongside in the very same response.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderTrackingView getOrderTrackingView(String rawToken) {
        String tokenHash = OrderTrackingTokenGenerator.hash(rawToken);
        CustomerOrder order = orderRepository
                .findByTrackingTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for tracking token"));
        return new OrderTrackingView(order, orderItemRepository.findAllByOrderId(order.getId()));
    }

    /**
     * Milestone 7: staff-facing order lookup by its readable order number (Section 4,
     * screen #6 - "tam/kısmi iade başlatma" needs a human-usable way to find the order
     * to refund; the order number, already shown on the KDS and the customer's
     * tracking page/receipt, is what staff would actually have on hand).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderTrackingView getOrderByNumber(UUID branchId, int orderNumber) {
        CustomerOrder order = orderRepository
                .findByBranchIdAndOrderNumber(branchId, orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for number: " + orderNumber));
        return new OrderTrackingView(order, orderItemRepository.findAllByOrderId(order.getId()));
    }

    /**
     * Used by the refund module to price a manual refund line from the OrderItem's own
     * immutable snapshot (Section 9: "backend'de hesaplanır" - the refund amount for a
     * given quantity is never trusted from the caller, same discipline as add-to-cart).
     */
    @Transactional(readOnly = true)
    public OrderItem getOrderItem(UUID orderId, UUID orderItemId) {
        OrderItem item = orderItemRepository
                .findByIdAndOrderId(orderItemId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + orderItemId));
        return item;
    }

    /** Read-only order lookup by id, scoped to a branch - used by the refund module before issuing a refund. */
    @Transactional(readOnly = true)
    public CustomerOrder getOrderInBranch(UUID branchId, UUID orderId) {
        return requireOrderInBranch(branchId, orderId);
    }

    /** Same as getOrderByNumber, but looked up by id - used to re-render the staff order view after an action (e.g. completeOrder). */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderTrackingView getOrderTrackingViewInBranch(UUID branchId, UUID orderId) {
        CustomerOrder order = requireOrderInBranch(branchId, orderId);
        return new OrderTrackingView(order, orderItemRepository.findAllByOrderId(order.getId()));
    }

    private CustomerOrder requireOrderInBranch(UUID branchId, UUID orderId) {
        CustomerOrder order =
                orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return order;
    }

    private void notifyOrderStatusChanged(CustomerOrder order) {
        orderStatusNotifier.notifyOrderStatusChanged(
                new OrderStatusUpdate(order.getId(), order.getBranchId(), order.getStatus().name(), order.getOrderNumber()));
    }

    private List<KitchenQueueOrderView> buildKitchenQueueViews(List<CustomerOrder> orders) {
        return orders.stream().map(this::buildKitchenQueueView).toList();
    }

    private KitchenQueueOrderView buildKitchenQueueView(CustomerOrder order) {
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        List<UUID> itemIds = items.stream().map(OrderItem::getId).toList();
        Map<UUID, List<OrderItemOption>> optionsByItemId = orderItemOptionRepository
                .findAllByOrderItemIdIn(itemIds)
                .stream()
                .collect(Collectors.groupingBy(OrderItemOption::getOrderItemId));
        return new KitchenQueueOrderView(order, items, optionsByItemId, resolveTableLabel(order));
    }

    /** Bölüm 19.3 kasa/KDS kartlarındaki masa etiketi - iki hop'luk zincir çözülemezse null döner. */
    private String resolveTableLabel(CustomerOrder order) {
        return customerSessionService
                .findTableVisit(order.getTableVisitId())
                .flatMap(visit -> tenantService.findTable(order.getBusinessId(), visit.getTableId()))
                .map(RestaurantTable::getLabel)
                .orElse(null);
    }

    /**
     * Every ProductOptionGroup on the product must contribute exactly one selection if
     * SINGLE (a mandatory choice, e.g. size), or any subset (including none) if
     * MULTIPLE. Selected option ids must belong to one of this product's own groups -
     * mixing in another product's option id is rejected, not silently ignored.
     */
    private List<ProductOption> resolveAndValidateOptions(UUID productId, List<UUID> selectedOptionIds) {
        List<ProductOptionGroup> groups = menuService.getOptionGroupsForProducts(List.of(productId));
        List<UUID> groupIds = groups.stream().map(ProductOptionGroup::getId).toList();
        Map<UUID, ProductOption> optionsById = menuService.getOptionsForGroups(groupIds).stream()
                .collect(Collectors.toMap(ProductOption::getId, option -> option));

        List<ProductOption> selectedOptions = new ArrayList<>();
        for (UUID optionId : selectedOptionIds) {
            ProductOption option = optionsById.get(optionId);
            if (option == null) {
                throw new IllegalArgumentException("Option does not belong to this product: " + optionId);
            }
            selectedOptions.add(option);
        }

        Map<UUID, List<ProductOption>> selectedByGroupId =
                selectedOptions.stream().collect(Collectors.groupingBy(ProductOption::getOptionGroupId));
        for (ProductOptionGroup group : groups) {
            int selectedCount = selectedByGroupId.getOrDefault(group.getId(), List.of()).size();
            if (group.getSelectionType() == SelectionType.SINGLE && selectedCount != 1) {
                throw new IllegalArgumentException(
                        "Exactly one option must be selected for group \"" + group.getName() + "\"");
            }
        }
        return selectedOptions;
    }

    private record CreateOrReuseDraftResult(CustomerOrder order, String newlyIssuedTrackingToken) {
    }

    private CreateOrReuseDraftResult createOrReuseDraftOrder(TableVisit visit) {
        Optional<CustomerOrder> existingDraft =
                orderRepository.findByTableVisitIdAndStatus(visit.getId(), OrderStatus.DRAFT);
        if (existingDraft.isPresent()) {
            return new CreateOrReuseDraftResult(existingDraft.get(), null);
        }
        String rawToken = OrderTrackingTokenGenerator.generateToken();
        String tokenHash = OrderTrackingTokenGenerator.hash(rawToken);
        CustomerOrder order = orderRepository.save(
                new CustomerOrder(visit.getBusinessId(), visit.getBranchId(), visit.getId(), tokenHash));
        return new CreateOrReuseDraftResult(order, rawToken);
    }

    /** Gap-analysis #7 chain comparison: non-financial order volume per branch since a cutoff. */
    @Transactional(readOnly = true)
    public long countOrdersSince(UUID branchId, Instant since) {
        return orderRepository.countByBranchIdAndCreatedAtAfterAndStatusNotIn(
                branchId, since, List.of(OrderStatus.DRAFT, OrderStatus.CANCELLED));
    }

    /** Gap-analysis #8 reporting: paid orders + items for a branch within a selectable date range. */
    @Transactional(readOnly = true)
    public List<ReportOrderView> findOrdersForReport(UUID branchId, Instant from, Instant to) {
        List<CustomerOrder> orders =
                orderRepository.findAllByBranchIdAndCreatedAtBetweenAndStatusIn(branchId, from, to, PAID_ORDER_STATUSES);
        List<UUID> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        Map<UUID, List<OrderItem>> itemsByOrderId = orderItemRepository.findAllByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return orders.stream()
                .map(order -> new ReportOrderView(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    private void recalculateOrderTotal(CustomerOrder order) {
        long total = orderItemRepository.findAllByOrderId(order.getId()).stream()
                .mapToLong(OrderItem::getLineTotalMinorUnits)
                .sum();
        order.recalculateTotal(total);
        orderRepository.save(order);
    }

    private CartView buildCartView(CustomerOrder order, String newlyIssuedTrackingToken) {
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        List<UUID> itemIds = items.stream().map(OrderItem::getId).toList();
        Map<UUID, List<OrderItemOption>> optionsByItemId = orderItemOptionRepository
                .findAllByOrderItemIdIn(itemIds)
                .stream()
                .collect(Collectors.groupingBy(OrderItemOption::getOrderItemId));
        return new CartView(order, items, optionsByItemId, newlyIssuedTrackingToken);
    }
}
