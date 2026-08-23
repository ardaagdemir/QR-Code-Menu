package com.qrmenu.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #8 (product-requirements.md Section 13): branch/chain sales reports -
 * gross/net/refund math, product/category breakdown from acceptedQuantity, and the
 * REPORT_VIEW/REPORT_CHAIN_VIEW permission boundaries.
 */
class ReportingFlowIntegrationTest extends AbstractIntegrationTest {

    // None of these fixtures set an explicit Branch.timezone, so TenantService.
    // resolveBranchTimeZone falls back to Business.defaultTimeZone - which
    // TenantFixtures.createBusiness leaves at its own default ("Europe/Istanbul", see
    // Business's single-arg constructor). "Today"/"this hour" here must be computed in
    // that same zone, not UTC, or these assertions race the real UTC/Europe-Istanbul
    // offset near midnight exactly like the original OrderHistoryIntegrationTest flake.
    private static final ZoneId BUSINESS_DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void branchReportComputesGrossNetRefundAndProductCategoryBreakdown() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Business 1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "report-admin-1@example.com");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "İçecekler");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Kahve", 3000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        // Order A: paid, cashier ACCEPT auto-accepts both units -> counts fully toward gross/net/product breakdown.
        String tableA = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrA = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableA);
        CheckedInVisit visitA = payAndAwaitStoreAcceptance(businessId, productId, qrA, 2);
        String orderIdA = acceptOrder(branchId, adminCookie, visitA);
        mockMvc.perform(post("/api/staff/orders/{orderId}/ready", orderIdA)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/orders/{orderId}/complete", orderIdA)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isOk());
        Instant readyAt = Instant.now();
        jdbcTemplate.update(
                "UPDATE customer_order SET preparation_started_at = ?, ready_at = ? WHERE id = ?",
                Timestamp.from(readyAt.minusSeconds(600)), Timestamp.from(readyAt), UUID.fromString(orderIdA));
        // Gap-analysis #17: only visitA records a headcount - visitB stays unset (never
        // defaulted to 1), so guestCountTotal must reflect just the one recorded visit.
        mockMvc.perform(withCookie(patch("/api/table-visits/{tableVisitId}/guest-count", visitA.tableVisitId()), visitA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":3}"))
                .andExpect(status().isOk());

        // Order B: paid, cashier REJECT -> full refund; still counts toward gross+orderCount+rejectedOrderCount,
        // but nets back out via refundTotal and never reaches the product breakdown (acceptedQuantity stays 0).
        String tableB = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 2");
        String qrB = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableB);
        CheckedInVisit visitB = payAndAwaitStoreAcceptance(businessId, productId, qrB, 1);
        rejectOrder(branchId, adminCookie, visitB);

        LocalDate today = LocalDate.now(BUSINESS_DEFAULT_ZONE);
        JsonNode report = objectMapper.readTree(mockMvc.perform(get("/api/staff/reports")
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
        // Gap-analysis #17: tableVisitCount (2 visits) and guestCountTotal (only visitA's 3) are separate metrics.
        assertThat(report.get("tableVisitCount").asLong()).isEqualTo(2);
        assertThat(report.get("guestCountTotal").asLong()).isEqualTo(3);
        assertThat(report.get("guestCountRecordedVisitCount").asLong()).isEqualTo(1);
        assertThat(report.get("averagePreparationSeconds").asLong()).isEqualTo(600);
        assertThat(report.get("completedOrderCount").asLong()).isEqualTo(1);

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
        int currentHour = java.time.Instant.now().atZone(BUSINESS_DEFAULT_ZONE).getHour();
        assertThat(hourly.get(currentHour).get("orderCount").asInt()).isEqualTo(2);
    }

    /**
     * Same root-cause bug class as OrderHistoryIntegrationTest's midnight-boundary
     * regression, exercised through the reports endpoint instead: an order created at
     * 01:30 Europe/Istanbul is still 22:30 the previous UTC calendar day (Turkey has had
     * no DST since 2016). getBranchReport must resolve "today" via the branch's own
     * timezone (here, its business's defaultTimeZone fallback - no explicit branch
     * timezone is set), not UTC, or this order would silently fall outside a
     * UTC-computed report window. Anchored to a fixed offset from the real "today"
     * rather than a hardcoded date, so it's deterministic regardless of when the suite
     * actually runs.
     */
    @Test
    void reportIncludesAnOrderCreatedJustAfterEuropeIstanbulMidnightEvenThoughItsStillYesterdayInUtc() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Midnight Boundary Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "report-midnight-admin@example.com");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 2000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        String table = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qr = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, table);
        CheckedInVisit visit = payAndAwaitStoreAcceptance(businessId, productId, qr, 1);
        String orderId = acceptOrder(branchId, adminCookie, visit);

        LocalDate istanbulToday = LocalDate.now(BUSINESS_DEFAULT_ZONE);
        Instant justAfterIstanbulMidnight = istanbulToday.atTime(1, 30).atZone(BUSINESS_DEFAULT_ZONE).toInstant();
        jdbcTemplate.update(
                "UPDATE customer_order SET created_at = ? WHERE id = ?",
                Timestamp.from(justAfterIstanbulMidnight), UUID.fromString(orderId));

        JsonNode report = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/{branchId}/reports", branchId)
                        .param("from", istanbulToday.toString())
                        .param("to", istanbulToday.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(report.get("grossSalesMinorUnits").asLong()).isEqualTo(2000);
        assertThat(report.get("orderCount").asInt()).isEqualTo(1);
    }

    @Test
    void currentStaffRolesCannotAccessChainReportsOrAnotherBranch() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Business 2");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        String adminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA, "report-admin-2@example.com", "BUSINESS_ADMIN");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Çay", 1000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchA, productId, "AVAILABLE", null);

        String tableA = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchA, "Masa 1");
        String qrA = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableA);
        CheckedInVisit visitA = payAndAwaitStoreAcceptance(businessId, productId, qrA, 1);
        acceptOrder(branchA, adminCookie, visitA);

        LocalDate today = LocalDate.now(BUSINESS_DEFAULT_ZONE);
        String platformCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA, "report-platform-2@example.com", "PLATFORM_ADMIN");
        JsonNode chain = objectMapper.readTree(mockMvc.perform(get("/api/staff/reports/chain")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, platformCookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(chain.get("totalGrossSalesMinorUnits").asLong()).isEqualTo(1000);
        assertThat(chain.get("branches")).hasSize(2);

        mockMvc.perform(get("/api/staff/reports/chain")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isForbidden());

        // BRANCH_MANAGER has REPORT_VIEW but no business-wide comparison permission.
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

    /**
     * Gap-analysis #14 (Section 11 💡): the kitchen financial summary is gated by its own
     * REPORT_FINANCIAL_SUMMARY_VIEW permission, not plain REPORT_VIEW - a CASHIER has
     * REPORT_VIEW (full reports) but must NOT see this, while BUSINESS_ADMIN/BRANCH_MANAGER
     * (who both hold ORDER_PREPARE too) do.
     */
    @Test
    void kitchenFinancialSummaryIsGatedToItsOwnPermissionNotPlainReportView() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Report Business 4");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "report-admin-4@example.com");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Kahve", 1500, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = payAndAwaitStoreAcceptance(businessId, productId, qrToken, 1);
        acceptOrder(branchId, adminCookie, visit);

        LocalDate today = LocalDate.now(BUSINESS_DEFAULT_ZONE);

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

    /** Accept auto-accepts every item in full now - no separate item-level decision step. */
    private String acceptOrder(String branchId, String staffCookie, CheckedInVisit visit) throws Exception {
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
