package com.qrmenu.reporting;

import com.qrmenu.customersession.CustomerSessionService;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuService;
import com.qrmenu.menu.Product;
import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.OrderItem;
import com.qrmenu.ordering.OrderStatus;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.ordering.ReportOrderView;
import com.qrmenu.refund.RefundService;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gap-analysis #8 read-only sales reporting (product-requirements.md Section 13),
 * deliberately built with no persistence of its own - same "compose the existing public
 * facades" shape as ChainComparisonService (gap-analysis #7), just over financial data:
 * OrderingService for paid orders/items, RefundService for completed refunds,
 * MenuService for product/category names, TenantService for branch name+timezone,
 * CustomerSessionService for table-visit volume.
 */
@Service
public class ReportingService {

    private static final List<OrderStatus> ACCEPTED_STATUSES =
            List.of(OrderStatus.IN_KITCHEN, OrderStatus.READY, OrderStatus.COMPLETED);

    private final OrderingService orderingService;
    private final RefundService refundService;
    private final MenuService menuService;
    private final TenantService tenantService;
    private final CustomerSessionService customerSessionService;

    public ReportingService(
            OrderingService orderingService,
            RefundService refundService,
            MenuService menuService,
            TenantService tenantService,
            CustomerSessionService customerSessionService) {
        this.orderingService = orderingService;
        this.refundService = refundService;
        this.menuService = menuService;
        this.tenantService = tenantService;
        this.customerSessionService = customerSessionService;
    }

    @Transactional(readOnly = true)
    public BranchSalesReportView getBranchReport(UUID businessId, UUID branchId, LocalDate from, LocalDate to) {
        Branch branch = tenantService.getBranch(businessId, branchId);
        return buildReport(branch, from, to);
    }

    @Transactional(readOnly = true)
    public ChainSalesReportView getChainReport(UUID businessId, LocalDate from, LocalDate to) {
        List<Branch> branches = tenantService.listBranches(businessId);
        List<BranchSalesReportView> reports =
                branches.stream().map(branch -> buildReport(branch, from, to)).toList();

        long totalGross = reports.stream().mapToLong(BranchSalesReportView::grossSalesMinorUnits).sum();
        long totalNet = reports.stream().mapToLong(BranchSalesReportView::netSalesMinorUnits).sum();
        long totalRefund = reports.stream().mapToLong(BranchSalesReportView::refundTotalMinorUnits).sum();
        int totalOrders = reports.stream().mapToInt(BranchSalesReportView::orderCount).sum();

        return new ChainSalesReportView(businessId, from, to, totalGross, totalNet, totalRefund, totalOrders, reports);
    }

    private BranchSalesReportView buildReport(Branch branch, LocalDate from, LocalDate to) {
        ZoneId zone = resolveZone(branch);
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<ReportOrderView> orders = orderingService.findOrdersForReport(branch.getId(), fromInstant, toInstant);

        long grossSales = orders.stream().mapToLong(o -> o.order().getTotalMinorUnits()).sum();
        int orderCount = orders.size();
        int acceptedOrderCount =
                (int) orders.stream().filter(o -> ACCEPTED_STATUSES.contains(o.order().getStatus())).count();
        int rejectedOrderCount =
                (int) orders.stream().filter(o -> o.order().getStatus() == OrderStatus.REJECTED_BY_STORE).count();
        long averageOrderValue = orderCount == 0 ? 0 : Math.round((double) grossSales / orderCount);

        List<UUID> orderIds = orders.stream().map(o -> o.order().getId()).toList();
        long refundTotal = refundService.sumCompletedRefundAmount(orderIds);
        long netSales = grossSales - refundTotal;

        long tableVisitCount = customerSessionService.countTableVisitsBetween(branch.getId(), fromInstant, toInstant);

        List<ProductSalesView> productBreakdown = buildProductBreakdown(orders);
        List<CategorySalesView> categoryBreakdown = buildCategoryBreakdown(orders, branch.getBusinessId());
        List<HourlySalesView> hourlyDistribution = buildHourlyDistribution(orders, zone);

        return new BranchSalesReportView(
                branch.getId(),
                branch.getName(),
                from,
                to,
                grossSales,
                netSales,
                refundTotal,
                orderCount,
                acceptedOrderCount,
                rejectedOrderCount,
                averageOrderValue,
                tableVisitCount,
                productBreakdown,
                categoryBreakdown,
                hourlyDistribution);
    }

    /** Section 13.1's "satılan adet"/ciro use acceptedQuantity - what the kitchen actually kept, not what was ordered. */
    private List<ProductSalesView> buildProductBreakdown(List<ReportOrderView> orders) {
        Map<UUID, ProductAggregate> byProduct = new LinkedHashMap<>();
        for (ReportOrderView view : orders) {
            for (OrderItem item : view.items()) {
                if (item.getAcceptedQuantity() <= 0) {
                    continue;
                }
                ProductAggregate aggregate =
                        byProduct.computeIfAbsent(item.getProductId(), id -> new ProductAggregate(item.getProductNameSnapshot()));
                aggregate.quantity += item.getAcceptedQuantity();
                aggregate.revenueMinorUnits += (long) item.getAcceptedQuantity() * item.getUnitPriceMinorUnits();
            }
        }
        return byProduct.entrySet().stream()
                .map(e -> new ProductSalesView(e.getKey(), e.getValue().name, e.getValue().quantity, e.getValue().revenueMinorUnits))
                .sorted(Comparator.comparingLong(ProductSalesView::revenueMinorUnits).reversed())
                .toList();
    }

    private List<CategorySalesView> buildCategoryBreakdown(List<ReportOrderView> orders, UUID businessId) {
        Map<UUID, Long> productRevenue = new LinkedHashMap<>();
        Map<UUID, String> productName = new LinkedHashMap<>();
        for (ReportOrderView view : orders) {
            for (OrderItem item : view.items()) {
                if (item.getAcceptedQuantity() <= 0) {
                    continue;
                }
                long revenue = (long) item.getAcceptedQuantity() * item.getUnitPriceMinorUnits();
                productRevenue.merge(item.getProductId(), revenue, Long::sum);
                productName.putIfAbsent(item.getProductId(), item.getProductNameSnapshot());
            }
        }
        if (productRevenue.isEmpty()) {
            return List.of();
        }

        List<Product> products = menuService.getProductsByIds(new ArrayList<>(productRevenue.keySet()));
        Map<UUID, UUID> categoryIdByProduct = new LinkedHashMap<>();
        for (Product product : products) {
            categoryIdByProduct.put(product.getId(), product.getCategoryId());
        }
        Map<UUID, String> categoryNameById = new LinkedHashMap<>();
        for (MenuCategory category : menuService.getCategoriesForBusiness(businessId)) {
            categoryNameById.put(category.getId(), category.getName());
        }

        Map<UUID, Long> revenueByCategory = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> entry : productRevenue.entrySet()) {
            UUID categoryId = categoryIdByProduct.get(entry.getKey());
            if (categoryId == null) {
                continue;
            }
            revenueByCategory.merge(categoryId, entry.getValue(), Long::sum);
        }

        return revenueByCategory.entrySet().stream()
                .map(e -> new CategorySalesView(e.getKey(), categoryNameById.getOrDefault(e.getKey(), "Bilinmeyen kategori"), e.getValue()))
                .sorted(Comparator.comparingLong(CategorySalesView::revenueMinorUnits).reversed())
                .toList();
    }

    private List<HourlySalesView> buildHourlyDistribution(List<ReportOrderView> orders, ZoneId zone) {
        long[] orderCounts = new long[24];
        long[] revenue = new long[24];
        for (ReportOrderView view : orders) {
            int hour = view.order().getCreatedAt().atZone(zone).getHour();
            orderCounts[hour]++;
            revenue[hour] += view.order().getTotalMinorUnits();
        }
        List<HourlySalesView> hourly = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(new HourlySalesView(hour, (int) orderCounts[hour], revenue[hour]));
        }
        return hourly;
    }

    private static ZoneId resolveZone(Branch branch) {
        return branch.getTimezone() != null ? ZoneId.of(branch.getTimezone()) : ZoneOffset.UTC;
    }

    private static final class ProductAggregate {
        private final String name;
        private int quantity;
        private long revenueMinorUnits;

        private ProductAggregate(String name) {
            this.name = name;
        }
    }
}
