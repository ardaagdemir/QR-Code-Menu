package com.qrmenu.menu;

import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant isolation for the Milestone 3 menu module, same discipline as
 * TenantIsolationIntegrationTest: a wrong businessId in the path must fail even when
 * the referenced child entity genuinely exists (just under a different business).
 */
class MenuIsolationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void creatingAProductUnderACategoryOwnedByAnotherBusinessIsRejected() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Menu Business A");
        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Menu Business B");
        String categoryUnderA =
                TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "İçecekler");

        mockMvc.perform(post("/internal/businesses/{businessId}/products", businessBId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryUnderA
                                + "\",\"name\":\"Kola\",\"basePriceMinorUnits\":5000,\"taxRatePercent\":10}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingAnOptionGroupUnderAProductOwnedByAnotherBusinessIsRejected() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Menu Business A2");
        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Menu Business B2");
        String categoryId =
                TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "Ana Yemekler");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, categoryId, "Pizza", 25000, 10);

        mockMvc.perform(post("/internal/businesses/{businessId}/products/{productId}/option-groups", businessBId, productId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Boyut\",\"selectionType\":\"SINGLE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void upsertingABranchProductUnderABranchOwnedByAnotherBusinessIsRejected() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Menu Business A3");
        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Menu Business B3");
        String branchUnderA =
                TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "A Şubesi");
        String categoryId =
                TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, "Tatlılar");
        String productUnderB = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, categoryId, "Baklava", 15000, 10);

        // Business B's own product, but Business A's branch id in the path.
        mockMvc.perform(put(
                                "/internal/businesses/{businessId}/branches/{branchId}/products/{productId}",
                                businessBId,
                                branchUnderA,
                                productUnderB)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"availability\":\"AVAILABLE\"}"))
                .andExpect(status().isNotFound());
    }
}
