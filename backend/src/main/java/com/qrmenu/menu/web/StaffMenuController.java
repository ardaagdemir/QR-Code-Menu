package com.qrmenu.menu.web;

import com.qrmenu.menu.BranchProduct;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuService;
import com.qrmenu.menu.Product;
import com.qrmenu.menu.ProductOption;
import com.qrmenu.menu.ProductOptionGroup;
import com.qrmenu.menu.web.dto.BranchProductAdminResponse;
import com.qrmenu.menu.web.dto.BulkAssignBranchesRequest;
import com.qrmenu.menu.web.dto.CreateMenuCategoryRequest;
import com.qrmenu.menu.web.dto.CreateOptionGroupRequest;
import com.qrmenu.menu.web.dto.CreateOptionRequest;
import com.qrmenu.menu.web.dto.CreateProductRequest;
import com.qrmenu.menu.web.dto.MenuCategoryAdminResponse;
import com.qrmenu.menu.web.dto.OptionAdminResponse;
import com.qrmenu.menu.web.dto.OptionGroupAdminResponse;
import com.qrmenu.menu.web.dto.ProductAdminResponse;
import com.qrmenu.menu.web.dto.UpdateProductDetailsRequest;
import com.qrmenu.menu.web.dto.UpsertBranchProductRequest;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Section 4, staff-web screen "menü yönetimi" (BUSINESS_ADMIN, Permission.MENU_MANAGE).
 * Mirrors InternalMenuController's shape but is session-scoped to the caller's own
 * business, same reasoning as StaffTenantController/StaffUserController - no
 * businessId path variable, no trusting a caller-supplied one.
 */
@RestController
@RequestMapping("/api/staff")
public class StaffMenuController {

    private final MenuService menuService;
    private final StaffAuthService staffAuthService;

    public StaffMenuController(MenuService menuService, StaffAuthService staffAuthService) {
        this.menuService = menuService;
        this.staffAuthService = staffAuthService;
    }

    @GetMapping("/menu-categories")
    public List<MenuCategoryAdminResponse> listCategories(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = requireMenuManage(sessionCookie);
        return menuService.getCategoriesForBusiness(context.businessId()).stream().map(this::toResponse).toList();
    }

    @GetMapping("/menu-categories/{categoryId}/products")
    public List<ProductAdminResponse> listProducts(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID categoryId) {
        StaffContext context = requireMenuManage(sessionCookie);
        return menuService.getProductsForCategory(context.businessId(), categoryId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/products/{productId}/option-groups")
    public List<OptionGroupAdminResponse> listOptionGroups(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID productId) {
        StaffContext context = requireMenuManage(sessionCookie);
        return menuService.getOptionGroupsForProduct(context.businessId(), productId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/option-groups/{optionGroupId}/options")
    public List<OptionAdminResponse> listOptions(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID optionGroupId) {
        StaffContext context = requireMenuManage(sessionCookie);
        return menuService.getOptionsForGroup(context.businessId(), optionGroupId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/branches/{branchId}/branch-products")
    public List<BranchProductAdminResponse> listBranchProducts(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId) {
        StaffContext context = requireMenuManage(sessionCookie);
        return menuService.getBranchProductsForBranch(context.businessId(), branchId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/menu-categories")
    public ResponseEntity<MenuCategoryAdminResponse> createCategory(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateMenuCategoryRequest request) {
        StaffContext context = requireMenuManage(sessionCookie);
        MenuCategory category = menuService.createCategory(
                context.businessId(), request.name(), request.displayOrderOrDefault(), context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(category));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductAdminResponse> createProduct(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateProductRequest request) {
        StaffContext context = requireMenuManage(sessionCookie);
        Product product = menuService.createProduct(
                context.businessId(),
                request.categoryId(),
                request.name(),
                request.description(),
                request.imageUrl(),
                request.basePriceMinorUnits(),
                request.taxRatePercent(),
                request.displayOrderOrDefault(),
                request.activeOrDefault(),
                request.estimatedPreparationMinutes(),
                request.allergensOrEmpty(),
                context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @PatchMapping("/products/{productId}")
    public ResponseEntity<ProductAdminResponse> updateProductDetails(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductDetailsRequest request) {
        StaffContext context = requireMenuManage(sessionCookie);
        Product product = menuService.updateProductDetails(
                context.businessId(),
                productId,
                request.active(),
                request.estimatedPreparationMinutes(),
                request.allergensOrEmpty(),
                request.imageUrl(),
                context.staffUserId());
        return ResponseEntity.ok(toResponse(product));
    }

    @PostMapping("/products/{productId}/option-groups")
    public ResponseEntity<OptionGroupAdminResponse> createOptionGroup(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateOptionGroupRequest request) {
        StaffContext context = requireMenuManage(sessionCookie);
        ProductOptionGroup group = menuService.createOptionGroup(
                context.businessId(),
                productId,
                request.name(),
                request.selectionType(),
                request.displayOrderOrDefault(),
                context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(group));
    }

    @PostMapping("/products/{productId}/option-groups/{optionGroupId}/options")
    public ResponseEntity<OptionAdminResponse> createOption(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID productId,
            @PathVariable UUID optionGroupId,
            @Valid @RequestBody CreateOptionRequest request) {
        StaffContext context = requireMenuManage(sessionCookie);
        ProductOption option = menuService.createOption(
                context.businessId(),
                optionGroupId,
                request.name(),
                request.priceDeltaOrDefault(),
                request.displayOrderOrDefault(),
                context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(option));
    }

    @PutMapping("/branches/{branchId}/products/{productId}")
    public ResponseEntity<BranchProductAdminResponse> upsertBranchProduct(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID branchId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpsertBranchProductRequest request) {
        StaffContext context = requireMenuManage(sessionCookie);
        BranchProduct branchProduct = menuService.upsertBranchProduct(
                context.businessId(),
                branchId,
                productId,
                request.availability(),
                request.priceOverrideMinorUnits(),
                context.staffUserId());
        return ResponseEntity.ok(toResponse(branchProduct));
    }

    /** Gap-analysis #7: "tüm şubelere ata" / "seçili şubelere ata" bulk assignment. */
    @PostMapping("/products/{productId}/branch-assignments")
    public List<BranchProductAdminResponse> bulkAssignBranches(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID productId,
            @Valid @RequestBody BulkAssignBranchesRequest request) {
        StaffContext context = requireMenuManage(sessionCookie);
        return menuService
                .bulkAssignProductToBranches(
                        context.businessId(), productId, request.target(), request.branchIdsOrEmpty(), context.staffUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private StaffContext requireMenuManage(String sessionCookie) {
        return staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie), Permission.MENU_MANAGE);
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
                product.getTaxRatePercent(),
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
