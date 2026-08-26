package com.qrmenu.ordering;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.ProductNotOrderableException;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.common.web.TableInUseException;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * Platform admin panel (branch deactivate) and its own hasActiveOrders reader below -
     * the single, reused definition of "this branch still has an order the kitchen/
     * customer needs to see through to a terminal state." DRAFT (an uncommitted cart) and
     * PAYMENT_FAILED (a failed attempt, no kitchen impact) are deliberately excluded -
     * they carry no operational obligation a branch deactivation would strand.
     */
    static final List<OrderStatus> ACTIVE_ORDER_STATUSES = List.of(
            OrderStatus.AWAITING_PAYMENT, OrderStatus.AWAITING_STORE_ACCEPTANCE, OrderStatus.IN_KITCHEN, OrderStatus.READY);

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
        TableVisit visit = customerSessionService.getActiveTableVisitForOrdering(tableVisitId, callerSessionId);
        // New cart items only - a business/branch deactivated after check-in must not
        // accept new orders even from an already-established visit (Section: platform
        // admin deactivation). Existing cart contents/tracking are untouched.
        tenantService.assertBusinessAndBranchActive(visit.getBusinessId(), visit.getBranchId());

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
        customerSessionService.getActiveTableVisitForOrdering(tableVisitId, callerSessionId);
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
     * authoritative pre-payment checks - non-empty cart, every item still purchasable
     * (see requireEveryItemStillOrderable below), branch ordering-enabled + opening
     * hours (TenantService.assertOrderingCurrentlyAllowed) - and transitions it to
     * AWAITING_PAYMENT. The order's own price data was already backend-computed at
     * add-to-cart time (Section 9, Milestone 4) and is never recomputed here (an
     * OrderItem's unit price is a frozen snapshot, not re-priced at checkout); what
     * *is* re-checked is whether the product behind each snapshot is still allowed to
     * be sold at all - a menu edit made after add-to-cart but before payment (most
     * notably a staff hard-delete, Section "menü yaşam döngüsü") must not let a
     * customer pay for something no longer on the menu.
     */
    @Transactional
    public CustomerOrder beginPaymentForDraftOrder(UUID tableVisitId, UUID callerSessionId) {
        customerSessionService.getActiveTableVisitForOrdering(tableVisitId, callerSessionId);
        CustomerOrder order = orderRepository
                .findFirstByTableVisitIdAndStatusIn(tableVisitId, PAYABLE_STATUSES)
                .orElseThrow(() -> new ResourceNotFoundException("No payable cart for this table visit: " + tableVisitId));
        List<OrderItem> items = orderItemRepository.findAllByOrderId(order.getId());
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot start payment for an empty cart: " + order.getId());
        }
        requireEveryItemStillOrderable(order, items);
        tenantService.assertOrderingCurrentlyAllowed(order.getBusinessId(), order.getBranchId());
        order.markAwaitingPayment();
        return orderRepository.save(order);
    }

    /**
     * A product added to the cart earlier may since have been hard-deleted, deactivated
     * business-wide, or opted out of this branch - any of those must block payment with
     * a clear 409 (ProductNotOrderableException, same class addItem already throws for
     * the equivalent add-to-cart-time checks) rather than silently letting the frozen
     * OrderItem snapshot go through. The customer's fix is the existing removeItem
     * endpoint; this method only blocks, it never mutates the cart itself. The same
     * re-validation applies to each item's selected options (resolveAndValidateOptions'
     * own logic, re-run here): an option or its whole option group may have been
     * hard-deleted, or a group's selection-type may have changed so the item's frozen
     * selection no longer satisfies it (e.g. now SINGLE with two selections snapshotted).
     */
    private void requireEveryItemStillOrderable(CustomerOrder order, List<OrderItem> items) {
        Map<UUID, List<OrderItemOption>> selectedOptionsByItemId = orderItemOptionRepository
                .findAllByOrderItemIdIn(items.stream().map(OrderItem::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OrderItemOption::getOrderItemId));

        for (OrderItem item : items) {
            Product product = menuService
                    .findProductForBusiness(order.getBusinessId(), item.getProductId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new ProductNotOrderableException(
                            "Product is no longer available, remove it from the cart to continue: " + item.getProductId()));
            menuService
                    .getBranchProduct(order.getBranchId(), product.getId())
                    .filter(bp -> bp.getAvailability() == BranchProductAvailability.AVAILABLE)
                    .orElseThrow(() -> new ProductNotOrderableException(
                            "Product is no longer available at this branch, remove it from the cart to continue: "
                                    + product.getId()));
            requireSelectedOptionsStillValid(
                    product, selectedOptionsByItemId.getOrDefault(item.getId(), List.of()));
        }
    }

    private void requireSelectedOptionsStillValid(Product product, List<OrderItemOption> selectedOptions) {
        if (selectedOptions.isEmpty()) {
            return;
        }
        List<ProductOptionGroup> groups = menuService.getOptionGroupsForProducts(List.of(product.getId()));
        List<UUID> groupIds = groups.stream().map(ProductOptionGroup::getId).toList();
        Map<UUID, ProductOption> optionsById = menuService.getOptionsForGroups(groupIds).stream()
                .collect(Collectors.toMap(ProductOption::getId, option -> option));

        Map<UUID, List<ProductOption>> currentSelectionByGroupId = new LinkedHashMap<>();
        for (OrderItemOption selected : selectedOptions) {
            ProductOption option = optionsById.get(selected.getOptionId());
            if (option == null) {
                throw new ProductNotOrderableException(
                        "A selected option is no longer available, remove this item from the cart to continue: "
                                + selected.getOptionId());
            }
            currentSelectionByGroupId
                    .computeIfAbsent(option.getOptionGroupId(), key -> new ArrayList<>())
                    .add(option);
        }
        for (ProductOptionGroup group : groups) {
            int selectedCount = currentSelectionByGroupId.getOrDefault(group.getId(), List.of()).size();
            if (group.getSelectionType() == SelectionType.SINGLE && selectedCount != 1) {
                throw new ProductNotOrderableException("Option selection for \"" + group.getName()
                        + "\" is no longer valid, remove this item from the cart to continue: " + product.getId());
            }
        }
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
        // The daily order-number counter (OrderNumberGenerator: "per-branch, per-day")
        // must reset at the branch's own local midnight, not UTC's - otherwise the
        // first orders of a Turkish business day (00:00-03:00 Europe/Istanbul, before
        // UTC has rolled over) would keep incrementing the *previous* day's sequence
        // instead of restarting at #1, the exact class of bug getOrderHistory's
        // colliding-order-number handling exists to tolerate, not one this generator
        // should be causing on every single business day.
        com.qrmenu.tenant.Branch branch = tenantService.getBranch(order.getBusinessId(), order.getBranchId());
        int orderNumber = orderNumberGenerator.nextOrderNumber(order.getBranchId(), LocalDate.now(resolveZone(branch)));
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
     *
     * Uses the same row-locking read as getOrderInBranchForUpdate (not the plain
     * requireOrderInBranch every other transition here uses): two REJECT clicks fired
     * at nearly the same instant both start from an in-memory AWAITING_STORE_ACCEPTANCE
     * order unless one is forced to wait for the other's write. Without the lock, both
     * could pass rejectByStore()'s in-memory state check and each go on to call
     * RefundService.requestFullRefund - the FOR UPDATE lock inside that call still
     * stops it from ever double-refunding, but the second reject would still have
     * "succeeded" at the ordering layer before failing confusingly at the refund layer.
     * Locking here instead makes the second concurrent REJECT fail immediately and
     * clearly ("Cannot reject an order in status REJECTED_BY_STORE"), the same as a
     * plain sequential double-click already does.
     */
    @Transactional
    public CustomerOrder rejectOrder(UUID branchId, UUID orderId, String reasonCode, String note, UUID actorStaffUserId) {
        CustomerOrder order = getOrderInBranchForUpdate(branchId, orderId);
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
     * tracking page/receipt, is what staff would actually have on hand). orderNumber
     * resets daily per branch (OrderNumberGenerator), so more than one order can share
     * the same number once a branch has been open more than a day - staff searching by
     * number are almost always after the most recent one, so ambiguity resolves to the
     * most recently created match rather than erroring.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OrderTrackingView getOrderByNumber(UUID branchId, int orderNumber) {
        CustomerOrder order = orderRepository
                .findAllByBranchIdAndOrderNumberOrderByCreatedAtDesc(branchId, orderNumber)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order not found for number: " + orderNumber));
        return new OrderTrackingView(order, orderItemRepository.findAllByOrderId(order.getId()));
    }

    /**
     * Siparişler (order history) screen: completed/rejected orders for a branch, newest
     * first, within a branch-local calendar date range - same LocalDate-in/Instant-out
     * timezone handling as ReportingService.buildReport, so "today" means the branch's
     * own local day rather than UTC (see docs/development-progress.md's earlier UTC
     * date-boundary bugfix for why that distinction matters).
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<KitchenQueueOrderView> getOrderHistory(
            UUID branchId, List<OrderStatus> statuses, LocalDate from, LocalDate to) {
        UUID businessId = tenantService.requireBusinessIdForBranch(branchId);
        ZoneId zone = resolveZone(tenantService.getBranch(businessId, branchId));
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<CustomerOrder> orders = orderRepository.findAllByBranchIdAndStatusInAndCreatedAtBetweenOrderByLastActivityAtDesc(
                branchId, statuses, fromInstant, toInstant);
        return buildKitchenQueueViews(orders);
    }

    private ZoneId resolveZone(com.qrmenu.tenant.Branch branch) {
        // TenantService.resolveBranchTimeZone is the single source of truth (branch's
        // own timezone, else its business's defaultTimeZone - never a bare UTC guess).
        return tenantService.resolveBranchTimeZone(branch);
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

    /**
     * Refund coordination lock. All refund attempts for an order acquire this row lock
     * before reading completed RefundItem totals, so two concurrent requests cannot
     * both validate against the same remaining quantity.
     */
    @Transactional
    public CustomerOrder getOrderInBranchForUpdate(UUID branchId, UUID orderId) {
        CustomerOrder order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return order;
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

    /**
     * Platform admin panel: does this branch have any order still in flight
     * (ACTIVE_ORDER_STATUSES)? Read inside the caller's own transaction - see
     * PlatformAdminBranchService.deactivateBranch, which calls this only after locking
     * the branch row via TenantService.getBranchForUpdate, so the read is guaranteed
     * fresh with respect to any concurrent order/payment transition on this branch.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveOrders(UUID branchId) {
        return orderRepository.existsByBranchIdAndStatusIn(branchId, ACTIVE_ORDER_STATUSES);
    }

    /**
     * Masa yaşam döngüsü archive gate: bu masaya ait (herhangi bir TableVisit'i
     * üzerinden) hâlâ ACTIVE_ORDER_STATUSES'ta olan bir sipariş var mı? CustomerOrder
     * masaya doğrudan değil TableVisit üzerinden bağlı (CustomerOrder.tableVisitId), bu
     * yüzden önce CustomerSessionService'ten bu masanın tüm visit id'leri alınır.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveOrderForTable(UUID tableId) {
        List<UUID> tableVisitIds = customerSessionService.findTableVisitIdsForTable(tableId);
        if (tableVisitIds.isEmpty()) {
            return false;
        }
        return orderRepository.existsByTableVisitIdInAndStatusIn(tableVisitIds, ACTIVE_ORDER_STATUSES);
    }

    /**
     * Orchestrates the staff table-archive flow across the tenant/customer-session/
     * ordering modules - tenant cannot depend on ordering directly (would cycle back,
     * since ordering already depends on tenant), so this lives here instead, the same
     * reasoning as PlatformAdminBranchService.deactivateBranch for branch deactivate.
     *
     * <p>Everything below runs in one physical transaction: TenantService.getTableForUpdate
     * takes a PESSIMISTIC_WRITE lock on the table row first, then the active-visit and
     * active-order checks are read under that same lock, then the archive write happens -
     * so a concurrent check-in/order creation can never slip past this check, and this
     * check can never archive a table out from under a visit/order that's mid-transition.
     */
    @Transactional
    public RestaurantTable archiveTable(UUID businessId, UUID branchId, UUID tableId, UUID actorStaffUserId) {
        RestaurantTable table = tenantService.getTableForUpdate(businessId, branchId, tableId);
        if (customerSessionService.hasActiveVisitForTable(table.getId())) {
            throw new TableInUseException("Table has an active visit and cannot be archived: " + tableId);
        }
        if (hasActiveOrderForTable(table.getId())) {
            throw new TableInUseException("Table has an active order and cannot be archived: " + tableId);
        }
        return tenantService.archiveLockedTable(table, actorStaffUserId);
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

    /**
     * Customer-web "most popular" widget: product ids ranked by accepted quantity sold
     * over the trailing window, most-sold first. Real paid-order data only - no fake or
     * heuristic ranking. Callers must cross-reference against the current live menu
     * themselves (this method has no opinion on whether a product is still on sale).
     */
    @Transactional(readOnly = true)
    public List<UUID> findTopSellingProductIds(UUID branchId, int trailingDays, int limit) {
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(trailingDays));
        List<ReportOrderView> orders = findOrdersForReport(branchId, from, to);
        Map<UUID, Integer> quantityByProductId = new LinkedHashMap<>();
        for (ReportOrderView view : orders) {
            for (OrderItem item : view.items()) {
                if (item.getAcceptedQuantity() <= 0) {
                    continue;
                }
                quantityByProductId.merge(item.getProductId(), item.getAcceptedQuantity(), Integer::sum);
            }
        }
        return quantityByProductId.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Kasa KPI: orders completed inside the branch-local reporting window. */
    @Transactional(readOnly = true)
    public long countCompletedOrdersBetween(UUID branchId, Instant from, Instant to) {
        return orderRepository.countByBranchIdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                branchId, OrderStatus.COMPLETED, from, to);
    }

    /**
     * Kasa KPI: mean ACCEPTED_BY_STORE -> READY duration for orders that became ready
     * inside the branch-local reporting window. Rows without both real transition
     * timestamps are excluded instead of estimating a duration.
     */
    @Transactional(readOnly = true)
    public long averagePreparationSecondsBetween(UUID branchId, Instant from, Instant to) {
        List<Long> preparationSeconds = orderRepository
                .findAllByBranchIdAndReadyAtGreaterThanEqualAndReadyAtLessThanAndPreparationStartedAtIsNotNull(
                        branchId, from, to)
                .stream()
                .map(order -> Duration.between(order.getPreparationStartedAt(), order.getReadyAt()).getSeconds())
                .filter(seconds -> seconds >= 0)
                .toList();
        if (preparationSeconds.isEmpty()) {
            return 0L;
        }
        return Math.round(preparationSeconds.stream().mapToLong(Long::longValue).average().orElse(0));
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
