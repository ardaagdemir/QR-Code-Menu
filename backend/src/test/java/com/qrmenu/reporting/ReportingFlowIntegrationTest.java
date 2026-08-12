package com.qrmenu.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #8 (product-requirements.md Section 13): branch/chain sales reports -
 * gross/net/refund math, product/category breakdown from acceptedQuantity, and the
 * REPORT_VIEW/REPORT_CHAIN_VIEW permission boundaries.
 */
class ReportingFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void branchReportComputesGrossNetRefundAndProductCategoryBreakdown() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Business 1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "report-admin-1@example.com");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "İçecekler");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Kahve", 3000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        // Order A: paid, cashier ACCEPT, kitchen accepts both units -> counts fully toward gross/net/product breakdown.
        String tableA = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrA = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableA);
        CheckedInVisit visitA = payAndAwaitStoreAcceptance(businessId, productId, qrA, 2);
        String orderIdA = acceptAndDecideFullyInKitchen(branchId, adminCookie, visitA, productId);

        // Order B: paid, cashier REJECT -> full refund; still counts toward gross+orderCount+rejectedOrderCount,
        // but nets back out via refundTotal and never reaches the product breakdown (acceptedQuantity stays 0).
        String tableB = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 2");
        String qrB = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableB);
        CheckedInVisit visitB = payAndAwaitStoreAcceptance(businessId, productId, qrB, 1);
        rejectOrder(branchId, adminCookie, visitB);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        JsonNode report = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/{branchId}/reports", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        // gross = 2*3000 (order A) + 1*3000 (order B, later refunded) = 9000; refund = 3000; net = 6000.
        assertThat(report.get("grossSalesMinorUnits").asLong()).isEqualTo(9000);
        assertThat(report.get("refundTotalMinorUnits").asLong()).isEqualTo(3000);
        assertThat(report.get("netSalesMinorUnits").asLong()).isEqualTo(6000);
        assertThat(report.get("orderCount").asInt()).isEqualTo(2);
        assertThat(report.get("acceptedOrderCount").asInt()).isEqualTo(1);
        assertThat(report.get("rejectedOrderCount").asInt()).isEqualTo(1);
        assertThat(report.get("averageOrderValueMinorUnits").asLong()).isEqualTo(4500);

        JsonNode products = report.get("productBreakdown");
        assertThat(products).hasSize(1);
        assertThat(products.get(0).get("productId").asText()).isEqualTo(productId);
        assertThat(products.get(0).get("quantitySold").asInt()).isEqualTo(2);
        assertThat(products.get(0).get("revenueMinorUnits").asLong()).isEqualTo(6000);

        JsonNode categories = report.get("categoryBreakdown");
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).get("categoryId").asText()).isEqualTo(categoryId);
        assertThat(categories.get(0).get("revenueMinorUnits").asLong()).isEqualTo(6000);

        JsonNode hourly = report.get("hourlyDistribution");
        assertThat(hourly).hasSize(24);
        int currentHour = java.time.Instant.now().atZone(ZoneOffset.UTC).getHour();
        assertThat(hourly.get(currentHour).get("orderCount").asInt()).isEqualTo(2);
    }

    @Test
    void chainReportAggregatesAcrossBranchesAndIsAdminOnly() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Business 2");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "report-admin-2@example.com");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Çay", 1000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchA, productId, "AVAILABLE", null);

        String tableA = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchA, "Masa 1");
        String qrA = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableA);
        CheckedInVisit visitA = payAndAwaitStoreAcceptance(businessId, productId, qrA, 1);
        acceptAndDecideFullyInKitchen(branchA, adminCookie, visitA, productId);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        JsonNode chain = objectMapper.readTree(mockMvc.perform(get("/api/staff/reports/chain")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(chain.get("totalGrossSalesMinorUnits").asLong()).isEqualTo(1000);
        assertThat(chain.get("totalOrderCount").asInt()).isEqualTo(1);
        assertThat(chain.get("branches")).hasSize(2);
        for (JsonNode branch : chain.get("branches")) {
            if (branch.get("branchId").asText().equals(branchA)) {
                assertThat(branch.get("grossSalesMinorUnits").asLong()).isEqualTo(1000);
            } else {
                assertThat(branch.get("branchId").asText()).isEqualTo(branchB);
                assertThat(branch.get("grossSalesMinorUnits").asLong()).isEqualTo(0);
            }
        }

        // BRANCH_MANAGER has REPORT_VIEW but not REPORT_CHAIN_VIEW - business-wide comparison stays admin-only.
        String managerEmail = "report-manager-2@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + managerEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BRANCH_MANAGER\",\"branchIds\":[\"" + branchA + "\"]}"))
                .andExpect(status().isCreated());
        String managerCookie = StaffFixtures.login(mockMvc, managerEmail);
        mockMvc.perform(get("/api/staff/reports/chain")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, managerCookie)))
                .andExpect(status().isForbidden());

        // ... but that same BRANCH_MANAGER can see its own branch's report.
        mockMvc.perform(get("/api/staff/branches/{branchId}/reports", branchA)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, managerCookie)))
                .andExpect(status().isOk());

        // ... but not branch B's, which it isn't assigned to.
        mockMvc.perform(get("/api/staff/branches/{branchId}/reports", branchB)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, managerCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void kitchenStaffCannotViewReports() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String kitchenEmail = "report-kitchen-3@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String kitchenCookie = StaffFixtures.login(mockMvc, kitchenEmail);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mockMvc.perform(get("/api/staff/branches/{branchId}/reports", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenCookie)))
                .andExpect(status().isForbidden());
    }

    /**
     * Gap-analysis #14 (Section 11 💡): the kitchen financial summary is gated by its own
     * REPORT_FINANCIAL_SUMMARY_VIEW permission, not plain REPORT_VIEW - a CASHIER has
     * REPORT_VIEW (full reports) but must NOT see this, while BUSINESS_ADMIN/BRANCH_MANAGER
     * (who both hold KITCHEN_DECIDE too) do.
     */
    @Test
    void kitchenFinancialSummaryIsGatedToItsOwnPermissionNotPlainReportView() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Business 4");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "report-admin-4@example.com");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Kahve", 1500, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = payAndAwaitStoreAcceptance(businessId, productId, qrToken, 1);
        acceptAndDecideFullyInKitchen(branchId, adminCookie, visit, productId);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        JsonNode summary = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/{branchId}/reports/kitchen-summary", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(summary.get("grossSalesMinorUnits").asLong()).isEqualTo(1500);
        assertThat(summary.get("orderCount").asInt()).isEqualTo(1);

        String cashierEmail = "report-cashier-4@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String cashierCookie = StaffFixtures.login(mockMvc, cashierEmail);

        // CASHIER has REPORT_VIEW (can see the full report) but not REPORT_FINANCIAL_SUMMARY_VIEW.
        mockMvc.perform(get("/api/staff/branches/{branchId}/reports", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/branches/{branchId}/reports/kitchen-summary", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie)))
                .andExpect(status().isForbidden());
    }

    private CheckedInVisit payAndAwaitStoreAcceptance(String businessId, String productId, String qrToken, int quantity)
            throws Exception {
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);
        mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated());

        MvcResult intentResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/payments", visit.tableVisitId()), visit))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).get("paymentId").asText();
        mockMvc.perform(withCookie(
                        post("/api/table-visits/{tableVisitId}/payments/{paymentId}/mock-outcome", visit.tableVisitId(), paymentId), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCEEDED\"}"))
                .andExpect(status().isAccepted());

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult statusResult = mockMvc.perform(withCookie(
                            get("/api/table-visits/{tableVisitId}/payments/{paymentId}", visit.tableVisitId(), paymentId), visit))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode statusBody = objectMapper.readTree(statusResult.getResponse().getContentAsString());
            if ("AWAITING_STORE_ACCEPTANCE".equals(statusBody.get("orderStatus").asText())) {
                return visit;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for order to reach AWAITING_STORE_ACCEPTANCE");
    }

    private String acceptAndDecideFullyInKitchen(String branchId, String staffCookie, CheckedInVisit visit, String productId)
            throws Exception {
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);
        JsonNode pending = objectMapper.readTree(mockMvc.perform(
                        get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchId).cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(pending).hasSize(1);
        String orderId = pending.get(0).get("orderId").asText();
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(cookie))
                .andExpect(status().isOk());

        JsonNode queue = objectMapper.readTree(mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders", branchId).cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode order = null;
        for (JsonNode candidate : queue) {
            if (candidate.get("orderId").asText().equals(orderId)) {
                order = candidate;
                break;
            }
        }
        assertThat(order).isNotNull();
        JsonNode item = order.get("items").get(0);
        String orderItemId = item.get("id").asText();
        int orderedQuantity = item.get("orderedQuantity").asInt();
        mockMvc.perform(post("/api/kitchen/branches/{branchId}/order-items/{orderItemId}/decide", branchId, orderItemId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedQuantity\":" + orderedQuantity + "}"))
                .andExpect(status().isOk());
        return orderId;
    }

    private void rejectOrder(String branchId, String staffCookie, CheckedInVisit visit) throws Exception {
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);
        JsonNode pending = objectMapper.readTree(mockMvc.perform(
                        get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchId).cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(pending).hasSize(1);
        String orderId = pending.get(0).get("orderId").asText();
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
