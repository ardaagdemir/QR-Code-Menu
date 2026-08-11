package com.qrmenu.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the public GET /api/branches/{branchId}/menu endpoint, in particular the
 * BranchProduct opt-in rule from docs/product-requirements.md Section 5: no
 * BranchProduct row for a (branch, product) pair means the product must not appear in
 * the response at all - not merely be flagged unavailable.
 */
class PublicMenuIntegrationTest extends AbstractIntegrationTest {

    @Test
    void productWithoutABranchProductRowIsOmittedEntirely() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Opt-in Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Ana Yemekler");
        TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Görünmez Ürün", 10000, 10);
        // No BranchProduct row created for this product/branch pair.

        MenuFetch menu = fetchMenu(branchId);
        assertThat(menu.json.get("categories")).isEmpty();
    }

    @Test
    void availableProductIsShownWithBasePriceWhenNoOverride() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Available Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "İçecekler");
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ayran", 4000, 10);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        JsonNode product = firstProduct(fetchMenu(branchId).json);
        assertThat(product.get("id").asText()).isEqualTo(productId);
        assertThat(product.get("availability").asText()).isEqualTo("AVAILABLE");
        assertThat(product.get("priceMinorUnits").asLong()).isEqualTo(4000L);
    }

    @Test
    void priceOverrideOnBranchProductWinsOverBasePrice() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Override Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Tatlılar");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Sütlaç", 6000, 10);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", 5500L);

        JsonNode product = firstProduct(fetchMenu(branchId).json);
        assertThat(product.get("priceMinorUnits").asLong()).isEqualTo(5500L);
    }

    @Test
    void unavailableProductIsShownButMarkedUnavailable() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Unavailable Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Çorbalar");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Mercimek Çorbası", 3000, 10);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "UNAVAILABLE", null);

        JsonNode product = firstProduct(fetchMenu(branchId).json);
        assertThat(product.get("id").asText()).isEqualTo(productId);
        assertThat(product.get("availability").asText()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void optionGroupsAndOptionsAreIncludedInTheProduct() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Options Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Pizzalar");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Margherita", 20000, 10);
        String groupId = TenantFixtures.createOptionGroup(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, "Boyut", "SINGLE");
        TenantFixtures.createOption(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, groupId, "Büyük", 3000);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        JsonNode product = firstProduct(fetchMenu(branchId).json);
        JsonNode optionGroups = product.get("optionGroups");
        assertThat(optionGroups).hasSize(1);
        assertThat(optionGroups.get(0).get("name").asText()).isEqualTo("Boyut");
        assertThat(optionGroups.get(0).get("selectionType").asText()).isEqualTo("SINGLE");
        JsonNode options = optionGroups.get(0).get("options");
        assertThat(options).hasSize(1);
        assertThat(options.get(0).get("name").asText()).isEqualTo("Büyük");
        assertThat(options.get(0).get("priceDeltaMinorUnits").asLong()).isEqualTo(3000L);
    }

    @Test
    void inactiveProductIsOmittedEvenWithABranchProductRow() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Inactive Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Ana Yemekler");

        String response = mockMvc.perform(post("/internal/businesses/{businessId}/products", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"name\":\"Pasif Ürün\","
                                + "\"basePriceMinorUnits\":5000,\"taxRatePercent\":10,\"active\":false,"
                                + "\"estimatedPreparationMinutes\":15,\"allergens\":[\"GLUTEN\",\"MILK\"]}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode created = objectMapper.readTree(response);
        assertThat(created.get("active").asBoolean()).isFalse();
        assertThat(created.get("estimatedPreparationMinutes").asInt()).isEqualTo(15);
        assertThat(created.get("allergens")).hasSize(2);
        String productId = created.get("id").asText();

        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        MenuFetch menu = fetchMenu(branchId);
        assertThat(menu.json.get("categories")).isEmpty();
    }

    @Test
    void categoryWithNoOptedInProductsIsOmitted() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Empty Category Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Boş Kategori");

        MenuFetch menu = fetchMenu(branchId);
        assertThat(menu.json.get("categories")).isEmpty();
    }

    @Test
    void unknownBranchIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/branches/{branchId}/menu", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    private record MenuFetch(JsonNode json) {
    }

    private MenuFetch fetchMenu(String branchId) throws Exception {
        String response = mockMvc.perform(get("/api/branches/{branchId}/menu", branchId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new MenuFetch(objectMapper.readTree(response));
    }

    private JsonNode firstProduct(JsonNode menuJson) {
        return menuJson.get("categories").get(0).get("products").get(0);
    }
}
