package com.qrmenu.menu.web;

import com.qrmenu.menu.BranchProduct;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuService;
import com.qrmenu.menu.Product;
import com.qrmenu.menu.ProductOption;
import com.qrmenu.menu.ProductOptionGroup;
import com.qrmenu.menu.web.dto.BranchProductAdminResponse;
import com.qrmenu.menu.web.dto.CreateMenuCategoryRequest;
import com.qrmenu.menu.web.dto.CreateOptionGroupRequest;
import com.qrmenu.menu.web.dto.CreateOptionRequest;
import com.qrmenu.menu.web.dto.CreateProductRequest;
import com.qrmenu.menu.web.dto.MenuCategoryAdminResponse;
import com.qrmenu.menu.web.dto.OptionAdminResponse;
import com.qrmenu.menu.web.dto.OptionGroupAdminResponse;
import com.qrmenu.menu.web.dto.ProductAdminResponse;
import com.qrmenu.menu.web.dto.UpsertBranchProductRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal, PLATFORM_ADMIN-only bootstrap API for the Business-level catalog and
 * branch-level BranchProduct opt-in - no dedicated staff-web admin UI yet (that's
 * Milestone 8's BUSINESS_ADMIN menu management screen, Section 4). Guarded by the same
 * InternalAdminAuthFilter as the tenant module's /internal/** endpoints.
 */
@RestController
@RequestMapping("/internal/businesses/{businessId}")
class InternalMenuController {

    private final MenuService menuService;

    InternalMenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @PostMapping("/menu-categories")
    ResponseEntity<MenuCategoryAdminResponse> createCategory(
            @PathVariable UUID businessId, @Valid @RequestBody CreateMenuCategoryRequest request) {
        MenuCategory category = menuService.createCategory(businessId, request.name(), request.displayOrderOrDefault(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(category));
    }

    @PostMapping("/products")
    ResponseEntity<ProductAdminResponse> createProduct(
            @PathVariable UUID businessId, @Valid @RequestBody CreateProductRequest request) {
        Product product = menuService.createProduct(
                businessId,
                request.categoryId(),
                request.name(),
                request.description(),
                request.imageUrl(),
                request.basePriceMinorUnits(),
                request.displayOrderOrDefault(),
                request.activeOrDefault(),
                request.estimatedPreparationMinutes(),
                request.allergensOrEmpty(),
                null,
                null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @PostMapping("/products/{productId}/option-groups")
    ResponseEntity<OptionGroupAdminResponse> createOptionGroup(
            @PathVariable UUID businessId,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateOptionGroupRequest request) {
        ProductOptionGroup group = menuService.createOptionGroup(
                businessId,
                productId,
                request.name(),
                request.selectionType(),
                request.requiredOrDefault(),
                request.displayOrderOrDefault(),
                null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(group));
    }

    @PostMapping("/products/{productId}/option-groups/{optionGroupId}/options")
    ResponseEntity<OptionAdminResponse> createOption(
            @PathVariable UUID businessId,
            @PathVariable UUID productId,
            @PathVariable UUID optionGroupId,
            @Valid @RequestBody CreateOptionRequest request) {
        ProductOption option = menuService.createOption(
                businessId, optionGroupId, request.name(), request.priceDeltaOrDefault(), request.displayOrderOrDefault(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(option));
    }

    @PutMapping("/branches/{branchId}/products/{productId}")
    ResponseEntity<BranchProductAdminResponse> upsertBranchProduct(
            @PathVariable UUID businessId,
            @PathVariable UUID branchId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpsertBranchProductRequest request) {
        BranchProduct branchProduct = menuService.upsertBranchProduct(
                businessId, branchId, productId, request.availability(), request.priceOverrideMinorUnits(), null);
        return ResponseEntity.ok(toResponse(branchProduct));
    }

    private MenuCategoryAdminResponse toResponse(MenuCategory category) {
        return new MenuCategoryAdminResponse(
                category.getId(), category.getBusinessId(), category.getName(), category.getDisplayOrder());
    }

    private ProductAdminResponse toResponse(Product product) {
        return new ProductAdminResponse(
                product.getId(),
                product.getBusinessId(),
                product.getCategoryId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getBasePriceMinorUnits(),
                product.getDisplayOrder(),
                product.isActive(),
                product.getEstimatedPreparationMinutes(),
                product.getAllergens());
    }

    private OptionGroupAdminResponse toResponse(ProductOptionGroup group) {
        return new OptionGroupAdminResponse(
                group.getId(),
                group.getBusinessId(),
                group.getProductId(),
                group.getName(),
                group.getSelectionType().name(),
                group.isRequired(),
                group.getDisplayOrder());
    }

    private OptionAdminResponse toResponse(ProductOption option) {
        return new OptionAdminResponse(
                option.getId(),
                option.getBusinessId(),
                option.getOptionGroupId(),
                option.getName(),
                option.getPriceDeltaMinorUnits(),
                option.getDisplayOrder());
    }

    private BranchProductAdminResponse toResponse(BranchProduct branchProduct) {
        return new BranchProductAdminResponse(
                branchProduct.getId(),
                branchProduct.getBusinessId(),
                branchProduct.getBranchId(),
                branchProduct.getProductId(),
                branchProduct.getAvailability().name(),
                branchProduct.getPriceOverrideMinorUnits());
    }
}
