package com.qrmenu.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers MenuService.assignActiveCatalogToBranch, called from both branch-creation entry
 * points (InternalTenantController, PlatformAdminBusinessController) right after
 * TenantService.createBranch: a newly created branch should auto-inherit the business's
 * existing active catalog as default AVAILABLE BranchProduct rows, so the customer-facing
 * QR menu isn't empty the moment the branch goes live - fixing the bug where staff saw the
 * (business-level) product list as "full" while the branch's public menu showed "Bu şube
 * için henüz menüde ürün bulunmuyor" because no BranchProduct opt-in rows existed yet.
 */
class BranchProductAutoProvisioningIntegrationTest extends AbstractIntegrationTest {

    @Test
    void secondBranchCreatedAfterCatalogExistsAutoInheritsActiveProductsAndIsVisibleThroughItsOwnQrCode() throws Exception {
        String businessId =
                TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Auto Provision Business");
        TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Merkez Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Ana Yemekler");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Izgara Köfte", 12000, 10);

        // Second branch, created after the catalog already exists - no manual BranchProduct
        // upsert call anywhere in this test.
        String secondBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Yeni Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, secondBranchId, "T1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);

        TenantFixtures.CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        assertThat(visit.branchId()).isEqualTo(secondBranchId);

        JsonNode product = firstProduct(fetchMenu(secondBranchId));
        assertThat(product.get("id").asText()).isEqualTo(productId);
        assertThat(product.get("availability").asText()).isEqualTo("AVAILABLE");
        assertThat(product.get("priceMinorUnits").asLong()).isEqualTo(12000L);
    }

    @Test
    void productCreatedBeforeBranchDoesNotLeakIntoAnUnrelatedBusinessesBranch() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Provision Business A");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "Tatlılar");
        TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, categoryId, "Baklava", 15000, 10);

        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Provision Business B");
        String businessBBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, "B Şubesi");

        assertThat(fetchMenu(businessBBranchId).get("categories")).isEmpty();
    }

    @Test
    void removingProductFromSaleInOneAutoProvisionedBranchDoesNotAffectSiblingBranch() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Sibling Branch Business");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "İçecekler");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Limonata", 5000, 10);

        // Both branches are created after the product already exists, so both auto-inherit
        // it as AVAILABLE without any manual opt-in call.
        String branchAId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchBId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");

        assertThat(firstProduct(fetchMenu(branchAId)).get("availability").asText()).isEqualTo("AVAILABLE");
        assertThat(firstProduct(fetchMenu(branchBId)).get("availability").asText()).isEqualTo("AVAILABLE");

        // Staff toggles "Satıştan Kaldır" in Şube B only.
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchBId, productId, "UNAVAILABLE", null);

        // Şube B: product stays visible - Section 5's existing "Tükendi" design - but is no
        // longer orderable. It is NOT removed from the response entirely (that only happens
        // for a business-level Product.active=false kill switch, a different mechanism).
        JsonNode branchBProduct = firstProduct(fetchMenu(branchBId));
        assertThat(branchBProduct.get("id").asText()).isEqualTo(productId);
        assertThat(branchBProduct.get("availability").asText()).isEqualTo("UNAVAILABLE");

        // Şube A is untouched - still AVAILABLE, proving the toggle is branch-scoped.
        JsonNode branchAProduct = firstProduct(fetchMenu(branchAId));
        assertThat(branchAProduct.get("availability").asText()).isEqualTo("AVAILABLE");
    }

    private JsonNode fetchMenu(String branchId) throws Exception {
        String response = mockMvc.perform(get("/api/branches/{branchId}/menu", branchId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode firstProduct(JsonNode menuJson) {
        return menuJson.get("categories").get(0).get("products").get(0);
    }
}
