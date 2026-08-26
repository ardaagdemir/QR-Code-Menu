package com.qrmenu.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.menu.repository.BranchProductRepository;
import com.qrmenu.menu.repository.ProductOptionGroupRepository;
import com.qrmenu.menu.repository.ProductOptionRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Menü yaşam döngüsü: kategori/ürün/option-group/option rename+reorder+silme
 * (product-requirements gap). Covers the reorder validation contract (exact current
 * sibling set or reject), the product hard-delete cascade (own option groups/options +
 * every branch's BranchProduct row, scoped strictly to this business), and the orphaned
 * product-image cleanup on delete.
 */
class MenuLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BranchProductRepository branchProductRepository;

    @Autowired
    private ProductOptionGroupRepository optionGroupRepository;

    @Autowired
    private ProductOptionRepository optionRepository;

    @Autowired
    private com.qrmenu.shared.media.MediaStoragePort mediaStoragePort;

    @Autowired
    private MenuService menuService;

    @Autowired
    private com.qrmenu.menu.repository.ProductRepository productRepository;

    @Autowired
    private com.qrmenu.staffaccess.repository.StaffUserRepository staffUserRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void categoryCanBeRenamed() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Rename Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "İçecekler");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "menu-rename-admin@example.com");

        mockMvc.perform(staffPatch("/api/staff/menu-categories/{categoryId}", staffCookie, categoryId)
                        .content("{\"name\":\"Sıcak İçecekler\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sıcak İçecekler"));
    }

    @Test
    void categoryReorderAcceptsExactSiblingSetAndRejectsDuplicateMissingOrForeignIds() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Reorder Category Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cat1 = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "A");
        String cat2 = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "B");
        String cat3 = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "C");
        String otherBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Other Business");
        String foreignCategoryId =
                TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, otherBusinessId, "Foreign");
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "reorder-cat-admin@example.com");

        // Duplicate id.
        mockMvc.perform(staffPatch("/api/staff/menu-categories/reorder", staffCookie, null)
                        .content("{\"orderedIds\":[\"" + cat1 + "\",\"" + cat1 + "\",\"" + cat2 + "\"]}"))
                .andExpect(status().isBadRequest());

        // Missing sibling (cat3 left out).
        mockMvc.perform(staffPatch("/api/staff/menu-categories/reorder", staffCookie, null)
                        .content("{\"orderedIds\":[\"" + cat1 + "\",\"" + cat2 + "\"]}"))
                .andExpect(status().isBadRequest());

        // Foreign id from another business.
        mockMvc.perform(staffPatch("/api/staff/menu-categories/reorder", staffCookie, null)
                        .content("{\"orderedIds\":[\"" + cat1 + "\",\"" + cat2 + "\",\"" + foreignCategoryId + "\"]}"))
                .andExpect(status().isBadRequest());

        // Valid: exact current set, reversed order.
        mockMvc.perform(staffPatch("/api/staff/menu-categories/reorder", staffCookie, null)
                        .content("{\"orderedIds\":[\"" + cat3 + "\",\"" + cat2 + "\",\"" + cat1 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cat3))
                .andExpect(jsonPath("$[0].displayOrder").value(0))
                .andExpect(jsonPath("$[1].id").value(cat2))
                .andExpect(jsonPath("$[1].displayOrder").value(1))
                .andExpect(jsonPath("$[2].id").value(cat1))
                .andExpect(jsonPath("$[2].displayOrder").value(2));
    }

    @Test
    void categoryDeleteIsRejectedWhileItHasProductsAndSucceedsOnceEmpty() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Delete Category Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Ana Yemekler");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Köfte", 15000, 10);
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "delete-cat-admin@example.com");

        mockMvc.perform(delete("/api/staff/menu-categories/{categoryId}", categoryId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/staff/products/{productId}", productId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/staff/menu-categories/{categoryId}", categoryId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isNoContent());
    }

    @Test
    void productReorderRejectsAForeignProductIdFromAnotherCategory() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Reorder Product Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori 1");
        String otherCategoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori 2");
        String p1 = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "P1", 1000, 10);
        String p2 = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "P2", 1000, 10);
        String foreignProductId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, otherCategoryId, "P3", 1000, 10);
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "reorder-prod-admin@example.com");

        mockMvc.perform(staffPatch("/api/staff/menu-categories/{categoryId}/products/reorder", staffCookie, categoryId)
                        .content("{\"orderedIds\":[\"" + p2 + "\",\"" + foreignProductId + "\"]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(staffPatch("/api/staff/menu-categories/{categoryId}/products/reorder", staffCookie, categoryId)
                        .content("{\"orderedIds\":[\"" + p2 + "\",\"" + p1 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(p2))
                .andExpect(jsonPath("$[1].id").value(p1));
    }

    @Test
    void deletingAProductCascadesItsOwnCatalogChildrenButNeverTouchesAnotherBusiness() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cascade Business A");
        String branchA1 = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "A Şube 1");
        String branchA2 = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "A Şube 2");
        String categoryAId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "Kategori A");
        String productAId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, categoryAId, "Ürün A", 10000, 10);
        String groupAId = TenantFixtures.createOptionGroup(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, productAId, "Boyut", "SINGLE");
        String optionAId =
                TenantFixtures.createOption(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, productAId, groupAId, "Büyük", 500);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, branchA1, productAId, "AVAILABLE", null);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, branchA2, productAId, "AVAILABLE", null);

        // Business B's own, independent catalog - must survive business A's product delete untouched.
        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cascade Business B");
        String branchBId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, "B Şube");
        String categoryBId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, "Kategori B");
        String productBId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, categoryBId, "Ürün B", 8000, 10);
        String groupBId = TenantFixtures.createOptionGroup(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, productBId, "Boyut", "SINGLE");
        TenantFixtures.createOption(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, productBId, groupBId, "Küçük", 0);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, branchBId, productBId, "AVAILABLE", null);

        String staffCookieA = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessAId, branchA1, "cascade-admin-a@example.com");

        mockMvc.perform(delete("/api/staff/products/{productId}", productAId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookieA)))
                .andExpect(status().isNoContent());

        // Business A's own cascade: option group, option, and both branches' opt-in rows all gone.
        assertThat(optionGroupRepository.findAllByProductIdInOrderByDisplayOrderAsc(List.of(UUID.fromString(productAId))))
                .isEmpty();
        assertThat(optionRepository.findAllByOptionGroupIdInOrderByDisplayOrderAsc(List.of(UUID.fromString(groupAId))))
                .isEmpty();
        assertThat(branchProductRepository.findByBranchIdAndProductId(UUID.fromString(branchA1), UUID.fromString(productAId)))
                .isEmpty();
        assertThat(branchProductRepository.findByBranchIdAndProductId(UUID.fromString(branchA2), UUID.fromString(productAId)))
                .isEmpty();

        // Business B: completely untouched.
        assertThat(optionGroupRepository.findAllByProductIdInOrderByDisplayOrderAsc(List.of(UUID.fromString(productBId))))
                .hasSize(1);
        assertThat(optionRepository.findAllByOptionGroupIdInOrderByDisplayOrderAsc(List.of(UUID.fromString(groupBId))))
                .hasSize(1);
        assertThat(branchProductRepository.findByBranchIdAndProductId(UUID.fromString(branchBId), UUID.fromString(productBId)))
                .isPresent();
    }

    @Test
    void deletingAProductRemovesItsOrphanedImageFile() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Media Cleanup Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Pizza", 25000, 10);
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "media-cleanup-admin@example.com");

        byte[] pngBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MvcResult uploadResult = mockMvc.perform(multipart("/api/staff/media/product-images")
                        .file(new MockMultipartFile("file", "dish.png", "image/png", pngBytes))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn();
        String imageUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("url").asText();
        String mediaKey = mediaStoragePort.resolveKeyFromUrl(imageUrl).orElseThrow();
        assertThat(mediaStoragePort.load(mediaKey)).isPresent();

        mockMvc.perform(staffPatch("/api/staff/products/{productId}", staffCookie, productId)
                        .content("{\"name\":\"Pizza\",\"description\":null,\"basePriceMinorUnits\":25000,"
                                + "\"taxRatePercent\":10,\"active\":true,\"estimatedPreparationMinutes\":null,"
                                + "\"allergens\":[],\"imageUrl\":\"" + imageUrl + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/staff/products/{productId}", productId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isNoContent());

        assertThat(mediaStoragePort.load(mediaKey)).isEmpty();
    }

    /**
     * Rollback safety: MenuService.deleteProduct deletes the product's DB row and its
     * image file in the same @Transactional method. If the media delete ran synchronously
     * before commit and the surrounding transaction then rolled back for any reason, the
     * DB row would come back (rollback) but the file would already be gone from disk -
     * an unrecoverable image loss for a product that still exists. The cleanup must only
     * happen after the transaction actually commits.
     */
    @Test
    void productImageIsNotDeletedWhenTheDeletingTransactionRollsBack() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Rollback Media Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Pizza", 25000, 10);
        String staffEmail = "rollback-media-admin@example.com";
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, staffEmail);

        byte[] pngBytes = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MvcResult uploadResult = mockMvc.perform(multipart("/api/staff/media/product-images")
                        .file(new MockMultipartFile("file", "dish.png", "image/png", pngBytes))
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn();
        String imageUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("url").asText();
        String mediaKey = mediaStoragePort.resolveKeyFromUrl(imageUrl).orElseThrow();
        assertThat(mediaStoragePort.load(mediaKey)).isPresent();

        mockMvc.perform(staffPatch("/api/staff/products/{productId}", staffCookie, productId)
                        .content("{\"name\":\"Pizza\",\"description\":null,\"basePriceMinorUnits\":25000,"
                                + "\"taxRatePercent\":10,\"active\":true,\"estimatedPreparationMinutes\":null,"
                                + "\"allergens\":[],\"imageUrl\":\"" + imageUrl + "\"}"))
                .andExpect(status().isOk());

        UUID businessUuid = UUID.fromString(businessId);
        UUID productUuid = UUID.fromString(productId);
        UUID actorStaffUserId = staffUserRepository.findByEmail(staffEmail).orElseThrow().getId();

        // Deletes through the real service method, inside a transaction that is then
        // forced to roll back instead of commit - simulates any failure surfacing later
        // in the same transaction (e.g. audit write failure) after the media delete step.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            menuService.deleteProduct(businessUuid, productUuid, actorStaffUserId);
            status.setRollbackOnly();
        });

        assertThat(productRepository.findByIdAndBusinessId(productUuid, businessUuid)).isPresent();
        assertThat(mediaStoragePort.load(mediaKey)).isPresent();
    }

    @Test
    void optionGroupCanBeRenamedReorderedAndDeletedCascadingItsOptions() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Option Group Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Burger", 20000, 10);
        String group1 =
                TenantFixtures.createOptionGroup(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, "Boyut", "SINGLE");
        String group2 =
                TenantFixtures.createOptionGroup(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, "Ekstra", "MULTIPLE");
        String optionId =
                TenantFixtures.createOption(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, group1, "Orta", 0);
        String staffCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "option-group-admin@example.com");

        mockMvc.perform(staffPatch("/api/staff/option-groups/{optionGroupId}", staffCookie, group1)
                        .content("{\"name\":\"Porsiyon\",\"selectionType\":\"SINGLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Porsiyon"));

        mockMvc.perform(staffPatch("/api/staff/products/{productId}/option-groups/reorder", staffCookie, productId)
                        .content("{\"orderedIds\":[\"" + group2 + "\",\"" + group1 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(group2));

        mockMvc.perform(delete("/api/staff/option-groups/{optionGroupId}", group1)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isNoContent());

        assertThat(optionRepository.findAllByOptionGroupIdInOrderByDisplayOrderAsc(List.of(UUID.fromString(group1))))
                .isEmpty();
        assertThat(optionRepository.findById(UUID.fromString(optionId))).isEmpty();
    }

    @Test
    void optionCanBeRenamedRepricedReorderedAndDeleted() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Option Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Kahve", 8000, 10);
        String groupId =
                TenantFixtures.createOptionGroup(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, "Boyut", "SINGLE");
        String option1 = TenantFixtures.createOption(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, groupId, "Küçük", 0);
        String option2 =
                TenantFixtures.createOption(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, groupId, "Büyük", 1000);
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "option-admin@example.com");

        mockMvc.perform(staffPatch("/api/staff/options/{optionId}", staffCookie, option2)
                        .content("{\"name\":\"Jumbo\",\"priceDeltaMinorUnits\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jumbo"))
                .andExpect(jsonPath("$.priceDeltaMinorUnits").value(1500));

        mockMvc.perform(staffPatch("/api/staff/option-groups/{optionGroupId}/options/reorder", staffCookie, groupId)
                        .content("{\"orderedIds\":[\"" + option2 + "\",\"" + option1 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(option2));

        mockMvc.perform(delete("/api/staff/options/{optionId}", option1)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isNoContent());

        assertThat(optionRepository.findAllByOptionGroupIdInOrderByDisplayOrderAsc(List.of(UUID.fromString(groupId))))
                .extracting(ProductOption::getId)
                .containsExactly(UUID.fromString(option2));
    }

    private MockHttpServletRequestBuilder staffPatch(String urlTemplate, String staffCookie, String pathVariable) {
        MockHttpServletRequestBuilder builder = pathVariable == null
                ? patch(urlTemplate)
                : patch(urlTemplate, pathVariable);
        return builder.contentType(MediaType.APPLICATION_JSON).cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie));
    }
}
