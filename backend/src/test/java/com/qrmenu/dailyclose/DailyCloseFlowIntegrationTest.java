package com.qrmenu.dailyclose;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #9 (product-requirements.md Section 14): daily close snapshot math (fed
 * by gap-analysis #8's ReportingService, Section 13.4), FINAL immutability, REPORT_VIEW
 * permission gate, Excel export content, and the scheduler's PREVIEW-lead/FINAL-grace
 * trigger thresholds (Section 14.2).
 */
class DailyCloseFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DailyCloseScheduler scheduler;

    @Autowired
    private DailyCloseService dailyCloseService;

    @Test
    void manualFinalComputesFromOrdersAndStaysImmutableAfterward() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Close Business 1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "close-admin-1@example.com");
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Ürün", 3000, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        payAndAcceptOneOrder(businessId, branchId, adminCookie, productId, 2);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        JsonNode first = objectMapper.readTree(mockMvc.perform(
                        post("/api/staff/branches/{branchId}/daily-close/final", branchId)
                                .param("businessDate", today.toString())
                                .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(first.get("status").asText()).isEqualTo("FINAL");
        assertThat(first.get("grossSalesMinorUnits").asLong()).isEqualTo(6000);
        assertThat(first.get("netSalesMinorUnits").asLong()).isEqualTo(6000);
        assertThat(first.get("orderCount").asInt()).isEqualTo(1);
        assertThat(first.get("acceptedOrderCount").asInt()).isEqualTo(1);

        // A second paid+accepted order lands after the day was already closed - FINAL must not move.
        payAndAcceptOneOrder(businessId, branchId, adminCookie, productId, 1);
        JsonNode second = objectMapper.readTree(mockMvc.perform(
                        post("/api/staff/branches/{branchId}/daily-close/final", branchId)
                                .param("businessDate", today.toString())
                                .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(second.get("grossSalesMinorUnits").asLong()).isEqualTo(6000);
        assertThat(second.get("orderCount").asInt()).isEqualTo(1);
        assertThat(second.get("generatedAt").asText()).isEqualTo(first.get("generatedAt").asText());
    }

    @Test
    void kitchenStaffCannotAccessDailyClose() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Close Business 2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String kitchenEmail = "close-kitchen-2@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String kitchenCookie = StaffFixtures.login(mockMvc, kitchenEmail);
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenCookie);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/staff/branches/{branchId}/daily-close", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/branches/{branchId}/daily-close/final", branchId)
                        .param("businessDate", today.toString())
                        .cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void kitchenStaffCannotExportDailyCloseExcel() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Export Auth Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String kitchenEmail = "export-auth-kitchen@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String kitchenCookie = StaffFixtures.login(mockMvc, kitchenEmail);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/staff/branches/{branchId}/daily-close/excel", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenCookie)))
                .andExpect(status().isForbidden());
    }

    /** BRANCH_MANAGER has REPORT_VIEW (branch-scoped) but not REPORT_CHAIN_VIEW - the chain export must still reject it. */
    @Test
    void branchManagerCannotExportChainDailyCloseExcel() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Chain Export Auth Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String managerEmail = "export-auth-manager@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + managerEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BRANCH_MANAGER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String managerCookie = StaffFixtures.login(mockMvc, managerEmail);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/staff/daily-close/excel")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, managerCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void excelExportContainsTheGeneratedRow() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Close Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Excel Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "close-admin-3@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(post("/api/staff/branches/{branchId}/daily-close/final", branchId)
                        .param("businessDate", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/staff/branches/{branchId}/daily-close/excel", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        byte[] workbookBytes = result.getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Şube");
            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("Excel Şube");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("FINAL");
        }
    }

    @Test
    void schedulerRespectsPreviewLeadAndFinalGraceThresholds() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Close Business 4");
        String previewBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Preview Şube");
        String finalBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Final Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "close-admin-4@example.com");

        // Branches default to UTC (no timezone configured) - hours must be set in UTC wall-clock time to match.
        // Closing in 3 minutes: inside the 10-minute preview lead, but not yet past the 5-minute final grace.
        setTodayHours(previewBranchId, adminCookie, LocalTime.now(ZoneOffset.UTC).plusMinutes(3));
        // Closed 6 minutes ago: already past closing + 5-minute grace -> should go straight to FINAL.
        setTodayHours(finalBranchId, adminCookie, LocalTime.now(ZoneOffset.UTC).minusMinutes(6));

        scheduler.generateDueSnapshots();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var previewReports = dailyCloseService.listForBranch(java.util.UUID.fromString(previewBranchId), today, today);
        assertThat(previewReports).hasSize(1);
        assertThat(previewReports.get(0).getStatus()).isEqualTo(DailyCloseStatus.PREVIEW);

        var finalReports = dailyCloseService.listForBranch(java.util.UUID.fromString(finalBranchId), today, today);
        assertThat(finalReports).hasSize(1);
        assertThat(finalReports.get(0).getStatus()).isEqualTo(DailyCloseStatus.FINAL);
    }

    private void setTodayHours(String branchId, String staffCookie, LocalTime closingTime) throws Exception {
        String dayOfWeek = LocalDate.now(ZoneOffset.UTC).getDayOfWeek().name();
        mockMvc.perform(post("/api/staff/branches/{branchId}/business-hours", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":[{\"dayOfWeek\":\"" + dayOfWeek + "\",\"openingTime\":\"00:00\",\"closingTime\":\""
                                + closingTime + "\",\"closed\":false}]}"))
                .andExpect(status().isOk());
    }

    private void payAndAcceptOneOrder(String businessId, String branchId, String staffCookie, String productId, int quantity)
            throws Exception {
        String table = TenantFixtures.createTable(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa-" + java.util.UUID.randomUUID());
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, table);
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

        String orderId = awaitStoreAcceptance(visit, paymentId);
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);
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
        mockMvc.perform(post("/api/kitchen/branches/{branchId}/order-items/{orderItemId}/decide", branchId, item.get("id").asText())
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptedQuantity\":" + item.get("orderedQuantity").asInt() + "}"))
                .andExpect(status().isOk());
    }

    private String awaitStoreAcceptance(CheckedInVisit visit, String paymentId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult statusResult = mockMvc.perform(withCookie(
                            get("/api/table-visits/{tableVisitId}/payments/{paymentId}", visit.tableVisitId(), paymentId), visit))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode statusBody = objectMapper.readTree(statusResult.getResponse().getContentAsString());
            if ("AWAITING_STORE_ACCEPTANCE".equals(statusBody.get("orderStatus").asText())) {
                return statusBody.get("orderId").asText();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for order to reach AWAITING_STORE_ACCEPTANCE");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCookie(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
