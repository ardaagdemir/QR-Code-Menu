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
import com.qrmenu.tenant.TenantService;
import java.util.List;
import java.util.Map;
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

    public PublicMenuController(TenantService tenantService, MenuService menuService) {
        this.tenantService = tenantService;
        this.menuService = menuService;
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

        Map<UUID, List<Product>> optedInProductsByCategoryId = products.stream()
                .filter(product -> branchProductByProductId.containsKey(product.getId()))
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
                optionGroupResponses);
    }
}
