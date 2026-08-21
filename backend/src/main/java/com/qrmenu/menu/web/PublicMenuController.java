package com.qrmenu.menu.web;

import com.qrmenu.menu.BranchProduct;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuService;
import com.qrmenu.menu.Product;
import com.qrmenu.menu.ProductOption;
import com.qrmenu.menu.ProductOptionGroup;
import com.qrmenu.menu.web.dto.MenuCategoryResponse;
import com.qrmenu.menu.web.dto.MenuOptionGroupResponse;
import com.qrmenu.menu.web.dto.MenuOptionResponse;
import com.qrmenu.menu.web.dto.MenuProductResponse;
import com.qrmenu.menu.web.dto.MenuResponse;
import com.qrmenu.menu.web.dto.PopularProductsResponse;
import com.qrmenu.ordering.OrderingService;
import com.qrmenu.tenant.TenantService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, anonymous, customer-facing menu for one branch (Section 4, customer-web
 * screen #3). Only products with a BranchProduct row for this branch are included -
 * the opt-in rule from Section 5 is enforced here, not just at write time: a product
 * with no row is omitted from the response entirely, not merely hidden client-side.
 * No auth/cookie required to view a menu - the QR risk model (Section 5) is about
 * placing orders under someone else's table, not about browsing a public menu.
 */
@RestController
@RequestMapping("/api/branches")
public class PublicMenuController {

    private final TenantService tenantService;
    private final MenuService menuService;
    private final OrderingService orderingService;

    public PublicMenuController(TenantService tenantService, MenuService menuService, OrderingService orderingService) {
        this.tenantService = tenantService;
        this.menuService = menuService;
        this.orderingService = orderingService;
    }

    @GetMapping("/{branchId}/menu")
    public MenuResponse getMenu(@PathVariable UUID branchId) {
        UUID businessId = tenantService.requireBusinessIdForBranch(branchId);

        List<MenuCategory> categories = menuService.getCategoriesForBusiness(businessId);
        List<UUID> categoryIds = categories.stream().map(MenuCategory::getId).toList();

        List<Product> products = menuService.getProductsForCategories(categoryIds);
        List<UUID> productIds = products.stream().map(Product::getId).toList();

        Map<UUID, BranchProduct> branchProductByProductId = menuService.getBranchProducts(branchId, productIds).stream()
                .collect(Collectors.toMap(BranchProduct::getProductId, bp -> bp));

        Map<UUID, List<ProductOptionGroup>> optionGroupsByProductId = menuService
                .getOptionGroupsForProducts(productIds)
                .stream()
                .collect(Collectors.groupingBy(ProductOptionGroup::getProductId));

        List<UUID> optionGroupIds =
                optionGroupsByProductId.values().stream().flatMap(List::stream).map(ProductOptionGroup::getId).toList();
        Map<UUID, List<ProductOption>> optionsByGroupId = menuService.getOptionsForGroups(optionGroupIds).stream()
                .collect(Collectors.groupingBy(ProductOption::getOptionGroupId));

        // active=false is a business-level kill switch (Section 3.2) - it hides a product
        // from every branch's menu even if a BranchProduct opt-in row still exists there.
        Map<UUID, List<Product>> optedInProductsByCategoryId = products.stream()
                .filter(product -> product.isActive() && branchProductByProductId.containsKey(product.getId()))
                .collect(Collectors.groupingBy(Product::getCategoryId));

        List<MenuCategoryResponse> categoryResponses = categories.stream()
                .map(category -> toCategoryResponse(
                        category,
                        optedInProductsByCategoryId.getOrDefault(category.getId(), List.of()),
                        branchProductByProductId,
                        optionGroupsByProductId,
                        optionsByGroupId))
                .filter(category -> !category.products().isEmpty())
                .toList();

        return new MenuResponse(branchId, categoryResponses);
    }

    /**
     * Customer-web "En Çok Tercih Edilenler" widget: real accepted-order sales from the
     * trailing 30 days, ranked by quantity, filtered down to products still visible on
     * this branch's live menu (a since-discontinued or opted-out product is dropped
     * rather than shown with stale data). Empty when there's no sales history yet - no
     * fake/heuristic fallback ranking.
     */
    @GetMapping("/{branchId}/menu/popular-products")
    public PopularProductsResponse getPopularProducts(@PathVariable UUID branchId) {
        MenuResponse menu = getMenu(branchId);
        Set<UUID> visibleProductIds = menu.categories().stream()
                .flatMap(category -> category.products().stream())
                .map(MenuProductResponse::id)
                .collect(Collectors.toSet());

        List<UUID> productIds = orderingService.findTopSellingProductIds(branchId, 30, 6).stream()
                .filter(visibleProductIds::contains)
                .toList();
        return new PopularProductsResponse(branchId, productIds);
    }

    private MenuCategoryResponse toCategoryResponse(
            MenuCategory category,
            List<Product> products,
            Map<UUID, BranchProduct> branchProductByProductId,
            Map<UUID, List<ProductOptionGroup>> optionGroupsByProductId,
            Map<UUID, List<ProductOption>> optionsByGroupId) {
        List<MenuProductResponse> productResponses = products.stream()
                .map(product -> toProductResponse(
                        product,
                        branchProductByProductId.get(product.getId()),
                        optionGroupsByProductId.getOrDefault(product.getId(), List.of()),
                        optionsByGroupId))
                .toList();
        return new MenuCategoryResponse(category.getId(), category.getName(), productResponses);
    }

    private MenuProductResponse toProductResponse(
            Product product,
            BranchProduct branchProduct,
            List<ProductOptionGroup> optionGroups,
            Map<UUID, List<ProductOption>> optionsByGroupId) {
        long effectivePrice = branchProduct.getPriceOverrideMinorUnits() != null
                ? branchProduct.getPriceOverrideMinorUnits()
                : product.getBasePriceMinorUnits();

        List<MenuOptionGroupResponse> optionGroupResponses = optionGroups.stream()
                .map(group -> new MenuOptionGroupResponse(
                        group.getId(),
                        group.getName(),
                        group.getSelectionType().name(),
                        optionsByGroupId.getOrDefault(group.getId(), List.of()).stream()
                                .map(option -> new MenuOptionResponse(
                                        option.getId(), option.getName(), option.getPriceDeltaMinorUnits()))
                                .toList()))
                .toList();

        return new MenuProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                effectivePrice,
                product.getTaxRatePercent(),
                branchProduct.getAvailability().name(),
                product.getEstimatedPreparationMinutes(),
                product.getAllergens(),
                optionGroupResponses);
    }
}
