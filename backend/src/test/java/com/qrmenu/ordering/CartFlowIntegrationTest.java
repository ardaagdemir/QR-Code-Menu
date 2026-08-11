package com.qrmenu.ordering;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the Milestone 4 DRAFT Order/cart flow: backend-authoritative Product +
 * BranchProduct revalidation (opt-in rule preserved from Milestone 3), option-group
 * validation, Money-computed totals, one-DRAFT-per-visit + orderTrackingToken issued
 * exactly once at creation, and TableVisit-ownership (cookie) enforcement.
 */
class CartFlowIntegrationTest extends AbstractIntegrationTest {

    private record MenuFixture(String productId, String optionGroupId, String option1Id, String option2Id) {
    }

    @Test
    void addingAnItemCreatesTheDraftOrderComputesTotalFromBackendPricesAndIssuesTrackingTokenOnce() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cart Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        MenuFixture menu = seedProductWithMandatorySingleOption(businessId, branchId, "Latte", 8000, 500);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        MvcResult first = addItem(visit, menu.productId(), 2, menu.option1Id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                // (8000 base + 500 option) * 2 = 17000
                .andExpect(jsonPath("$.totalMinorUnits").value(17000))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].options[0].name").value("Seçenek 1"))
                .andExpect(jsonPath("$.items[0].options[0].priceDeltaMinorUnits").value(500))
                .andExpect(jsonPath("$.orderTrackingToken").exists())
                .andReturn();
        String firstOrderId =
                objectMapper.readTree(first.getResponse().getContentAsString()).get("orderId").asText();

        // Adding a second line to the SAME draft must reuse the order and must NOT
        // re-issue a tracking token (Section 2: returned exactly once, at creation).
        MvcResult second = addItem(visit, menu.productId(), 1, menu.option2Id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(firstOrderId))
                // (8000 base + 1000 option) * 1 = 9000, on top of the 17000 above.
                .andExpect(jsonPath("$.totalMinorUnits").value(17000 + 9000))
                .andExpect(jsonPath("$.orderTrackingToken").doesNotExist())
                .andReturn();
        assertThat(objectMapper
                        .readTree(second.getResponse().getContentAsString())
                        .get("items"))
                .hasSize(2);
    }

    @Test
    void removingAnItemRecalculatesTheOrderTotal() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Remove Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        MenuFixture menu = seedProductWithMandatorySingleOption(businessId, branchId, "Çay", 2000, 0);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        MvcResult added = addItem(visit, menu.productId(), 1, menu.option1Id())
                .andExpect(status().isCreated())
                .andReturn();
        String orderItemId =
                objectMapper.readTree(added.getResponse().getContentAsString()).get("items").get(0).get("id").asText();

        mockMvc.perform(withCookie(delete(
                                "/api/table-visits/{tableVisitId}/cart/items/{orderItemId}", visit.tableVisitId(), orderItemId),
                        visit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinorUnits").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void gettingTheCartBeforeAnyItemIsAddedReturnsAnEmptyCart() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Empty Cart Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        mockMvc.perform(withCookie(get("/api/table-visits/{tableVisitId}/cart", visit.tableVisitId()), visit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").doesNotExist())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalMinorUnits").value(0));
    }

    @Test
    void addingAProductWithNoBranchProductRowIsRejectedWithConflict() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "No BP Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Opt-out Ürün", 5000, 10);
        // No BranchProduct row created - opt-in rule (Section 5).
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        mockMvc.perform(withCookie(
                                post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addingAnUnavailableProductIsRejectedWithConflict() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Unavailable Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Tükenen Ürün", 5000, 10);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "UNAVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        mockMvc.perform(withCookie(
                                post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    void addingAProductWithoutTheMandatorySingleOptionSelectionIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Missing Option Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        MenuFixture menu = seedProductWithMandatorySingleOption(businessId, branchId, "Boyutlu Ürün", 3000, 0);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        mockMvc.perform(withCookie(
                                post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + menu.productId() + "\",\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addingAnOptionThatBelongsToAnotherProductIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cross Product Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        MenuFixture menuA = seedProductWithMandatorySingleOption(businessId, branchId, "Ürün A", 3000, 0);
        MenuFixture menuB = seedProductWithMandatorySingleOption(businessId, branchId, "Ürün B", 4000, 0);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        mockMvc.perform(withCookie(
                                post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + menuA.productId() + "\",\"quantity\":1,\"selectedOptionIds\":[\""
                                + menuB.option1Id() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cartOperationsWithoutASessionCookieAreRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "No Cookie Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        mockMvc.perform(get("/api/table-visits/{tableVisitId}/cart", visit.tableVisitId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cartOperationsWithAnotherVisitsSessionCookieAreRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Ownership Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String tableAId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa A");
        String tableBId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa B");
        String qrTokenA = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableAId);
        String qrTokenB = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableBId);

        CheckedInVisit visitA = TenantFixtures.checkIn(mockMvc, objectMapper, qrTokenA);
        CheckedInVisit visitB = TenantFixtures.checkIn(mockMvc, objectMapper, qrTokenB);

        // Customer B's cookie must not be able to read customer A's table visit's cart.
        mockMvc.perform(get("/api/table-visits/{tableVisitId}/cart", visitA.tableVisitId())
                        .cookie(new MockCookie("qrmenu_session", visitB.sessionCookieValue())))
                .andExpect(status().isNotFound());
    }

    private MenuFixture seedProductWithMandatorySingleOption(
            String businessId, String branchId, String productName, long basePriceMinorUnits, long optionPriceDelta)
            throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, productName, basePriceMinorUnits, 10);
        String groupId = TenantFixtures.createOptionGroup(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, "Boyut", "SINGLE");
        String option1Id = TenantFixtures.createOption(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, groupId, "Seçenek 1", optionPriceDelta);
        String option2Id = TenantFixtures.createOption(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, productId, groupId, "Seçenek 2", optionPriceDelta + 500);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        return new MenuFixture(productId, groupId, option1Id, option2Id);
    }

    private org.springframework.test.web.servlet.ResultActions addItem(
            CheckedInVisit visit, String productId, int quantity, String selectedOptionId) throws Exception {
        return mockMvc.perform(withCookie(
                        post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + ",\"selectedOptionIds\":[\""
                        + selectedOptionId + "\"]}"));
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
