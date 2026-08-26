package com.qrmenu.menu;

import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.CategoryHasProductsException;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.menu.repository.BranchProductRepository;
import com.qrmenu.menu.repository.MenuCategoryRepository;
import com.qrmenu.menu.repository.ProductOptionGroupRepository;
import com.qrmenu.menu.repository.ProductOptionRepository;
import com.qrmenu.menu.repository.ProductRepository;
import com.qrmenu.shared.media.MediaStoragePort;
import com.qrmenu.tenant.Branch;
import com.qrmenu.tenant.TenantService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final MediaStoragePort mediaStoragePort;

    public MenuService(
            TenantService tenantService,
            MenuCategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductOptionGroupRepository optionGroupRepository,
            ProductOptionRepository optionRepository,
            BranchProductRepository branchProductRepository,
            AuditService auditService,
            MediaStoragePort mediaStoragePort) {
        this.tenantService = tenantService;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionRepository = optionRepository;
        this.branchProductRepository = branchProductRepository;
        this.auditService = auditService;
        this.mediaStoragePort = mediaStoragePort;
    }

    @Transactional
    public MenuCategory createCategory(UUID businessId, String name, int displayOrder, UUID actorStaffUserId) {
        MenuCategory category = categoryRepository.save(new MenuCategory(businessId, name, displayOrder));
        auditService.record(businessId, actorStaffUserId, "MenuCategory", category.getId(), "CREATED", Map.of("name", name));
        return category;
    }

    @Transactional
    public MenuCategory renameCategory(UUID businessId, UUID categoryId, String name, UUID actorStaffUserId) {
        MenuCategory category = categoryRepository
                .findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu category not found for business: " + categoryId));
        category.rename(name);
        MenuCategory saved = categoryRepository.save(category);
        auditService.record(businessId, actorStaffUserId, "MenuCategory", saved.getId(), "RENAMED", Map.of("name", name));
        return saved;
    }

    /**
     * Caller sends the full, current set of the business's category ids in the desired
     * order; displayOrder is assigned as the position in that list (0-based). Rejecting
     * anything short of the exact current set (see validateReorderIds) means a stale
     * client can never silently drop or duplicate a sibling's ordering.
     */
    @Transactional
    public List<MenuCategory> reorderCategories(UUID businessId, List<UUID> orderedIds, UUID actorStaffUserId) {
        List<MenuCategory> categories = categoryRepository.findAllByBusinessIdOrderByDisplayOrderAsc(businessId);
        Map<UUID, MenuCategory> byId = categories.stream().collect(Collectors.toMap(MenuCategory::getId, c -> c));
        validateReorderIds(orderedIds, byId.keySet(), "category");
        List<MenuCategory> reordered = new ArrayList<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            MenuCategory category = byId.get(orderedIds.get(index));
            category.updateDisplayOrder(index);
            reordered.add(categoryRepository.save(category));
        }
        auditService.record(
                businessId, actorStaffUserId, "Business", businessId, "MENU_CATEGORIES_REORDERED",
                Map.of("count", orderedIds.size()));
        return reordered;
    }

    /** Gap-analysis "kategori silme": rejected (409) while any Product still references it - see CategoryHasProductsException. */
    @Transactional
    public void deleteCategory(UUID businessId, UUID categoryId, UUID actorStaffUserId) {
        MenuCategory category = categoryRepository
                .findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu category not found for business: " + categoryId));
        if (productRepository.existsByCategoryId(categoryId)) {
            throw new CategoryHasProductsException(
                    "Cannot delete category \"" + category.getName() + "\": it still has products");
        }
        categoryRepository.delete(category);
        auditService.record(businessId, actorStaffUserId, "MenuCategory", categoryId, "DELETED", Map.of("name", category.getName()));
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

    /** Gap-analysis "Product alanları" - full detail edit (staff-web menu screen), same field set as createProduct. */
    @Transactional
    public Product updateProductDetails(
            UUID businessId,
            UUID productId,
            String name,
            String description,
            long basePriceMinorUnits,
            int taxRatePercent,
            boolean active,
            Integer estimatedPreparationMinutes,
            Set<Allergen> allergens,
            String imageUrl,
            UUID actorStaffUserId) {
        Product product = productRepository
                .findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for business: " + productId));
        product.updateDetails(
                name, description, basePriceMinorUnits, taxRatePercent, active, estimatedPreparationMinutes, allergens, imageUrl);
        Product saved = productRepository.save(product);
        auditService.record(
                businessId, actorStaffUserId, "Product", saved.getId(), "UPDATED", Map.of("active", String.valueOf(active)));
        return saved;
    }

    /**
     * Caller sends the full, current set of product ids under this category in the
     * desired order - same "exact current set or reject" discipline as
     * reorderCategories.
     */
    @Transactional
    public List<Product> reorderProducts(UUID businessId, UUID categoryId, List<UUID> orderedIds, UUID actorStaffUserId) {
        MenuCategory category = categoryRepository
                .findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu category not found for business: " + categoryId));
        List<Product> products = productRepository.findAllByCategoryIdInOrderByDisplayOrderAsc(List.of(category.getId()));
        Map<UUID, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
        validateReorderIds(orderedIds, byId.keySet(), "product");
        List<Product> reordered = new ArrayList<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            Product product = byId.get(orderedIds.get(index));
            product.updateDisplayOrder(index);
            reordered.add(productRepository.save(product));
        }
        auditService.record(
                businessId, actorStaffUserId, "MenuCategory", categoryId, "PRODUCTS_REORDERED",
                Map.of("count", orderedIds.size()));
        return reordered;
    }

    /**
     * Hard delete (gap-analysis "ürün silme"): safe with respect to order history -
     * OrderItem.productId carries no FK (V5, deliberate snapshot design), so past orders
     * keep their frozen name/price regardless. Cascades at the DB level (V36) to this
     * product's own option groups/options and every branch's BranchProduct opt-in row -
     * all "belongs-to" catalog children with no meaning once the product is gone. Also
     * removes the product's own image file, if one was uploaded through
     * StaffMediaUploadController, so a deleted product doesn't leave an orphaned file on
     * disk. The file delete itself is deferred to after this transaction commits (see
     * deleteMediaKeyAfterCommit) - the DB delete is reversible via rollback, the file
     * delete is not, so running it eagerly could destroy a still-referenced image if the
     * transaction later rolled back for any reason (e.g. the audit write below failing).
     */
    @Transactional
    public void deleteProduct(UUID businessId, UUID productId, UUID actorStaffUserId) {
        Product product = productRepository
                .findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for business: " + productId));
        String imageUrl = product.getImageUrl();
        String name = product.getName();
        productRepository.delete(product);
        if (imageUrl != null) {
            mediaStoragePort.resolveKeyFromUrl(imageUrl).ifPresent(this::deleteMediaKeyAfterCommit);
        }
        auditService.record(businessId, actorStaffUserId, "Product", productId, "DELETED", Map.of("name", name));
    }

    private void deleteMediaKeyAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mediaStoragePort.delete(key);
            }
        });
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
    public ProductOptionGroup updateOptionGroup(
            UUID businessId, UUID optionGroupId, String name, SelectionType selectionType, UUID actorStaffUserId) {
        ProductOptionGroup group = optionGroupRepository
                .findByIdAndBusinessId(optionGroupId, businessId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Product option group not found for business: " + optionGroupId));
        group.update(name, selectionType);
        ProductOptionGroup saved = optionGroupRepository.save(group);
        auditService.record(
                businessId, actorStaffUserId, "ProductOptionGroup", saved.getId(), "UPDATED", Map.of("name", name));
        return saved;
    }

    /** Same "exact current set or reject" discipline as reorderCategories/reorderProducts. */
    @Transactional
    public List<ProductOptionGroup> reorderOptionGroups(
            UUID businessId, UUID productId, List<UUID> orderedIds, UUID actorStaffUserId) {
        Product product = productRepository
                .findByIdAndBusinessId(productId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for business: " + productId));
        List<ProductOptionGroup> groups =
                optionGroupRepository.findAllByProductIdInOrderByDisplayOrderAsc(List.of(product.getId()));
        Map<UUID, ProductOptionGroup> byId = groups.stream().collect(Collectors.toMap(ProductOptionGroup::getId, g -> g));
        validateReorderIds(orderedIds, byId.keySet(), "option group");
        List<ProductOptionGroup> reordered = new ArrayList<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            ProductOptionGroup group = byId.get(orderedIds.get(index));
            group.updateDisplayOrder(index);
            reordered.add(optionGroupRepository.save(group));
        }
        auditService.record(
                businessId, actorStaffUserId, "Product", productId, "OPTION_GROUPS_REORDERED",
                Map.of("count", orderedIds.size()));
        return reordered;
    }

    /** Hard delete, cascades to this group's own options at the DB level (V36) - same history-safety reasoning as deleteProduct. */
    @Transactional
    public void deleteOptionGroup(UUID businessId, UUID optionGroupId, UUID actorStaffUserId) {
        ProductOptionGroup group = optionGroupRepository
                .findByIdAndBusinessId(optionGroupId, businessId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Product option group not found for business: " + optionGroupId));
        optionGroupRepository.delete(group);
        auditService.record(
                businessId, actorStaffUserId, "ProductOptionGroup", optionGroupId, "DELETED", Map.of("name", group.getName()));
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

    @Transactional
    public ProductOption updateOption(
            UUID businessId, UUID optionId, String name, long priceDeltaMinorUnits, UUID actorStaffUserId) {
        ProductOption option = optionRepository
                .findByIdAndBusinessId(optionId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product option not found for business: " + optionId));
        option.update(name, priceDeltaMinorUnits);
        ProductOption saved = optionRepository.save(option);
        auditService.record(businessId, actorStaffUserId, "ProductOption", saved.getId(), "UPDATED", Map.of("name", name));
        return saved;
    }

    /** Same "exact current set or reject" discipline as reorderCategories/reorderProducts/reorderOptionGroups. */
    @Transactional
    public List<ProductOption> reorderOptions(
            UUID businessId, UUID optionGroupId, List<UUID> orderedIds, UUID actorStaffUserId) {
        ProductOptionGroup group = optionGroupRepository
                .findByIdAndBusinessId(optionGroupId, businessId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Product option group not found for business: " + optionGroupId));
        List<ProductOption> options = optionRepository.findAllByOptionGroupIdInOrderByDisplayOrderAsc(List.of(group.getId()));
        Map<UUID, ProductOption> byId = options.stream().collect(Collectors.toMap(ProductOption::getId, o -> o));
        validateReorderIds(orderedIds, byId.keySet(), "option");
        List<ProductOption> reordered = new ArrayList<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            ProductOption option = byId.get(orderedIds.get(index));
            option.updateDisplayOrder(index);
            reordered.add(optionRepository.save(option));
        }
        auditService.record(
                businessId, actorStaffUserId, "ProductOptionGroup", optionGroupId, "OPTIONS_REORDERED",
                Map.of("count", orderedIds.size()));
        return reordered;
    }

    /** Hard delete - no children, safe with respect to order history for the same reason as deleteProduct. */
    @Transactional
    public void deleteOption(UUID businessId, UUID optionId, UUID actorStaffUserId) {
        ProductOption option = optionRepository
                .findByIdAndBusinessId(optionId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Product option not found for business: " + optionId));
        optionRepository.delete(option);
        auditService.record(businessId, actorStaffUserId, "ProductOption", optionId, "DELETED", Map.of("name", option.getName()));
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
     * New-branch provisioning: every active business-catalog product gets a default
     * AVAILABLE, no-price-override BranchProduct row for the branch, so a freshly
     * created branch's customer menu isn't empty until staff manually opts products
     * in/out later. Idempotent - only inserts rows for (branch, product) pairs that
     * don't already have one; never touches an existing row (an already-provisioned
     * branch, a repeat call, or a branch where staff already customized availability/
     * price keeps exactly what it had). Branches created before this method existed
     * are backfilled once via the V35 migration's equivalent SQL, not through this
     * method. See BranchProductAutoProvisioningIntegrationTest.
     */
    @Transactional
    public void assignActiveCatalogToBranch(UUID businessId, UUID branchId, UUID actorStaffUserId) {
        tenantService.assertBranchBelongsToBusiness(businessId, branchId);
        List<Product> activeProducts = productRepository.findAllByBusinessIdAndActiveTrue(businessId);
        if (activeProducts.isEmpty()) {
            return;
        }
        Set<UUID> alreadyAssignedProductIds = branchProductRepository.findAllByBranchId(branchId).stream()
                .map(BranchProduct::getProductId)
                .collect(Collectors.toSet());
        List<BranchProduct> newRows = activeProducts.stream()
                .filter(product -> !alreadyAssignedProductIds.contains(product.getId()))
                .map(product -> new BranchProduct(
                        businessId, branchId, product.getId(), BranchProductAvailability.AVAILABLE, null))
                .toList();
        if (newRows.isEmpty()) {
            return;
        }
        branchProductRepository.saveAll(newRows);
        auditService.record(
                businessId, actorStaffUserId, "Branch", branchId, "CATALOG_DEFAULTED",
                Map.of("productCount", newRows.size()));
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

    /**
     * Same lookup as getProductForBusiness, but Optional instead of throwing - used by
     * OrderingService.beginPaymentForDraftOrder to re-check a draft cart's items right
     * before payment starts (a product may have been hard-deleted since it was added to
     * the cart), where "not found" is a 409 cart-conflict, not a 404.
     */
    @Transactional(readOnly = true)
    public Optional<Product> findProductForBusiness(UUID businessId, UUID productId) {
        return productRepository.findByIdAndBusinessId(productId, businessId);
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

    /**
     * Shared reorder-request validation: the caller must supply exactly the current
     * sibling set, no more, no less, no repeats, and no id belonging to a different
     * parent/business - a stale or foreign id fails loudly (400) instead of silently
     * reordering a subset or being ignored.
     */
    private static void validateReorderIds(List<UUID> orderedIds, Set<UUID> actualIds, String entityLabel) {
        Set<UUID> orderedSet = new HashSet<>(orderedIds);
        if (orderedSet.size() != orderedIds.size()) {
            throw new IllegalArgumentException("Duplicate " + entityLabel + " id in reorder request");
        }
        if (!orderedSet.equals(actualIds)) {
            throw new IllegalArgumentException(
                    "Reorder request must include exactly the current set of " + entityLabel + " ids");
        }
    }
}
