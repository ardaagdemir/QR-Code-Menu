package com.qrmenu.menu;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.menu.repository.BranchProductRepository;
import com.qrmenu.menu.repository.MenuCategoryRepository;
import com.qrmenu.menu.repository.ProductOptionGroupRepository;
import com.qrmenu.menu.repository.ProductOptionRepository;
import com.qrmenu.menu.repository.ProductRepository;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the menu module. Same tenant-isolation discipline as TenantService
 * (Section 2): every write validates that the referenced parent (category/product/
 * option group/branch) actually belongs to the given businessId before touching it.
 */
@Service
public class MenuService {

    private final TenantService tenantService;
    private final MenuCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductOptionGroupRepository optionGroupRepository;
    private final ProductOptionRepository optionRepository;
    private final BranchProductRepository branchProductRepository;
    private final AuditService auditService;

    public MenuService(
            TenantService tenantService,
            MenuCategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductOptionGroupRepository optionGroupRepository,
            ProductOptionRepository optionRepository,
            BranchProductRepository branchProductRepository,
            AuditService auditService) {
        this.tenantService = tenantService;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionRepository = optionRepository;
        this.branchProductRepository = branchProductRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MenuCategory createCategory(UUID businessId, String name, int displayOrder, UUID actorStaffUserId) {
        MenuCategory category = categoryRepository.save(new MenuCategory(businessId, name, displayOrder));
        auditService.record(businessId, actorStaffUserId, "MenuCategory", category.getId(), "CREATED", Map.of("name", name));
        return category;
    }

    @Transactional
    public Product createProduct(
            UUID businessId,
            UUID categoryId,
            String name,
            String description,
            String imageUrl,
            long basePriceMinorUnits,
            int taxRatePercent,
            int displayOrder,
            boolean active,
            Integer estimatedPreparationMinutes,
            Set<Allergen> allergens,
            UUID actorStaffUserId) {
        MenuCategory category = categoryRepository
                .findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu category not found for business: " + categoryId));
        Product product = productRepository.save(new Product(
                businessId,
                category.getId(),
                name,
                description,
                imageUrl,
                basePriceMinorUnits,
                taxRatePercent,
                displayOrder,
                active,
                estimatedPreparationMinutes,
                allergens));
        auditService.record(businessId, actorStaffUserId, "Product", product.getId(), "CREATED", Map.of("name", name));
        return product;
    }

    /** Gap-analysis "Product alanları" - active/passive + prep time + allergens edit (staff-web menu screen). */
    @Transactional
    public Product updateProductDetails(
            UUID businessId,
            UUID productId,
            boolean active,
            Integer estimatedPreparationMinutes,
            Set<Allergen> allergens,
            String imageUrl,
            UUID actorStaffUserId) {
        Product product = productRepository
                .findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for business: " + productId));
        product.updateDetails(active, estimatedPreparationMinutes, allergens, imageUrl);
        Product saved = productRepository.save(product);
        auditService.record(
                businessId, actorStaffUserId, "Product", saved.getId(), "UPDATED", Map.of("active", String.valueOf(active)));
        return saved;
    }

    @Transactional
    public ProductOptionGroup createOptionGroup(
            UUID businessId, UUID productId, String name, SelectionType selectionType, int displayOrder, UUID actorStaffUserId) {
        Product product = productRepository
                .findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for business: " + productId));
        ProductOptionGroup group = optionGroupRepository.save(
                new ProductOptionGroup(businessId, product.getId(), name, selectionType, displayOrder));
        auditService.record(
                businessId, actorStaffUserId, "ProductOptionGroup", group.getId(), "CREATED", Map.of("name", name));
        return group;
    }

    @Transactional
    public ProductOption createOption(
            UUID businessId, UUID optionGroupId, String name, long priceDeltaMinorUnits, int displayOrder, UUID actorStaffUserId) {
        ProductOptionGroup group = optionGroupRepository
                .findByIdAndBusinessId(optionGroupId, businessId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Product option group not found for business: " + optionGroupId));
        ProductOption option = optionRepository.save(
                new ProductOption(businessId, group.getId(), name, priceDeltaMinorUnits, displayOrder));
        auditService.record(businessId, actorStaffUserId, "ProductOption", option.getId(), "CREATED", Map.of("name", name));
        return option;
    }

    /**
     * Opts a product in (or updates its availability/price override) for a branch -
     * Section 5's "opt-in" rule: no call here means the product stays invisible at that
     * branch. Idempotent per (branch, product): a second call updates the same row
     * rather than creating a duplicate (the DB unique constraint on
     * (branch_id, product_id) backs this up regardless of the service-layer path).
     */
    @Transactional
    public BranchProduct upsertBranchProduct(
            UUID businessId,
            UUID branchId,
            UUID productId,
            BranchProductAvailability availability,
            Long priceOverrideMinorUnits,
            UUID actorStaffUserId) {
        tenantService.assertBranchBelongsToBusiness(businessId, branchId);
        Product product = productRepository
                .findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for business: " + productId));

        BranchProduct branchProduct = branchProductRepository
                .findByBranchIdAndProductId(branchId, product.getId())
                .map(existing -> {
                    existing.update(availability, priceOverrideMinorUnits);
                    return branchProductRepository.save(existing);
                })
                .orElseGet(() -> branchProductRepository.save(
                        new BranchProduct(businessId, branchId, product.getId(), availability, priceOverrideMinorUnits)));
        auditService.record(
                businessId, actorStaffUserId, "BranchProduct", branchProduct.getId(), "UPSERTED",
                Map.of("branchId", branchId.toString(), "productId", productId.toString(), "availability", availability.name()));
        return branchProduct;
    }

    /**
     * Gap-analysis #7: "tüm şubelere ata" / "seçili şubelere ata" bulk operation
     * (product-requirements.md Section 3.3). Availability-only - sets AVAILABLE with no
     * price override, one upsertBranchProduct call per target branch so each branch keeps
     * its own opt-in row (Section 5's opt-in model is preserved, not bypassed).
     */
    @Transactional
    public List<BranchProduct> bulkAssignProductToBranches(
            UUID businessId, UUID productId, BranchAssignmentTarget target, List<UUID> selectedBranchIds, UUID actorStaffUserId) {
        productRepository
                .findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for business: " + productId));
        List<UUID> targetBranchIds = target == BranchAssignmentTarget.ALL_BRANCHES
                ? tenantService.listBranches(businessId).stream().map(Branch::getId).toList()
                : selectedBranchIds;
        if (targetBranchIds.isEmpty()) {
            throw new IllegalArgumentException("At least one target branch is required");
        }
        return targetBranchIds.stream()
                .map(branchId -> upsertBranchProduct(
                        businessId, branchId, productId, BranchProductAvailability.AVAILABLE, null, actorStaffUserId))
                .toList();
    }

    /**
     * The public, customer-facing menu for one branch: categories -> products, but only
     * products that have a BranchProduct row for this branch (opt-in - Section 5).
     * AVAILABLE products are orderable, UNAVAILABLE ones are shown as "Tükendi";
     * products with no row at all are omitted entirely, not just hidden client-side.
     */
    @Transactional(readOnly = true)
    public List<MenuCategory> getCategoriesForBusiness(UUID businessId) {
        return categoryRepository.findAllByBusinessIdOrderByDisplayOrderAsc(businessId);
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsForCategories(List<UUID> categoryIds) {
        return productRepository.findAllByCategoryIdInOrderByDisplayOrderAsc(categoryIds);
    }

    @Transactional(readOnly = true)
    public List<BranchProduct> getBranchProducts(UUID branchId, List<UUID> productIds) {
        return branchProductRepository.findAllByBranchIdAndProductIdIn(branchId, productIds);
    }

    /** Gap-analysis #8 reporting: resolves each sold OrderItem.productId to its current category for the category breakdown. */
    @Transactional(readOnly = true)
    public List<Product> getProductsByIds(List<UUID> productIds) {
        return productRepository.findAllById(productIds);
    }

    @Transactional(readOnly = true)
    public List<ProductOptionGroup> getOptionGroupsForProducts(List<UUID> productIds) {
        return optionGroupRepository.findAllByProductIdInOrderByDisplayOrderAsc(productIds);
    }

    @Transactional(readOnly = true)
    public List<ProductOption> getOptionsForGroups(List<UUID> optionGroupIds) {
        return optionRepository.findAllByOptionGroupIdInOrderByDisplayOrderAsc(optionGroupIds);
    }

    /**
     * Used by the ordering module to backend-authoritatively resolve a product before
     * adding it to a cart (Section 9, Milestone 4: "Product+BranchProduct birleşik
     * revalidasyon") - never trusts a name/price the client sends.
     */
    @Transactional(readOnly = true)
    public Product getProductForBusiness(UUID businessId, UUID productId) {
        return productRepository
                .findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for business: " + productId));
    }

    /** Admin read: products of one category, ownership-checked (Milestone 8 staff-web menu screen). */
    @Transactional(readOnly = true)
    public List<Product> getProductsForCategory(UUID businessId, UUID categoryId) {
        MenuCategory category = categoryRepository
                .findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu category not found for business: " + categoryId));
        return productRepository.findAllByCategoryIdInOrderByDisplayOrderAsc(List.of(category.getId()));
    }

    /** Admin read: option groups of one product, ownership-checked. */
    @Transactional(readOnly = true)
    public List<ProductOptionGroup> getOptionGroupsForProduct(UUID businessId, UUID productId) {
        Product product = getProductForBusiness(businessId, productId);
        return optionGroupRepository.findAllByProductIdInOrderByDisplayOrderAsc(List.of(product.getId()));
    }

    /** Admin read: options of one option group, ownership-checked. */
    @Transactional(readOnly = true)
    public List<ProductOption> getOptionsForGroup(UUID businessId, UUID optionGroupId) {
        ProductOptionGroup group = optionGroupRepository
                .findByIdAndBusinessId(optionGroupId, businessId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Product option group not found for business: " + optionGroupId));
        return optionRepository.findAllByOptionGroupIdInOrderByDisplayOrderAsc(List.of(group.getId()));
    }

    /** Admin read: every BranchProduct opt-in row for one branch, ownership-checked. */
    @Transactional(readOnly = true)
    public List<BranchProduct> getBranchProductsForBranch(UUID businessId, UUID branchId) {
        tenantService.assertBranchBelongsToBusiness(businessId, branchId);
        return branchProductRepository.findAllByBranchId(branchId);
    }

    @Transactional(readOnly = true)
    public Optional<BranchProduct> getBranchProduct(UUID branchId, UUID productId) {
        return branchProductRepository.findByBranchIdAndProductId(branchId, productId);
    }
}
