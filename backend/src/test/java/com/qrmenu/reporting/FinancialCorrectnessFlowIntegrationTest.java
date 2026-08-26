package com.qrmenu.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.payment.MockPaymentProviderAdapter;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end financial correctness regression (single flow, not per-module unit checks):
 * paid order -> gross/net sales -> a COMPLETED partial refund -> a FAILED refund attempt on
 * the same item -> an immediately-due manual expense -> an immediately-due recurring
 * expense -> template deletion. Cross-checks that /reports ("Özet"/"Raporlar"),
 * /reports/kitchen-summary ("Kasa") and /reports/operating-result ("Yönetimsel Net Sonuç")
 * all agree for the same branch/date, that only the COMPLETED refund reduced net sales, and
 * that the two accounting identities hold:
 *   Toplam Gider = Manuel Gider + Gerçekleşmiş Tekrarlayan Gider
 *   Yönetimsel Net Sonuç = Net Satış - Toplam Gider
 * even after the recurring template that produced one of the expenses is deleted.
 */
class FinancialCorrectnessFlowIntegrationTest extends AbstractIntegrationTest {

    // Matches ReportingFlowIntegrationTest's convention: no fixture here sets an explicit
    // Branch.timezone, so TenantService.resolveBranchTimeZone falls back to Business.
    // defaultTimeZone ("Europe/Istanbul" - see TenantFixtures.createBusiness).
    private static final ZoneId BUSINESS_DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    @MockitoSpyBean
    private MockPaymentProviderAdapter paymentProvider;

    @Test
    void endToEndFlowKeepsOzetKasaAndReportsConsistentAndPreservesExpenseHistory() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Financial Audit Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookieValue = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "audit-admin@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookieValue);

        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Ana Yemek");
        String productId = TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, "Köfte", 5000);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);

        // --- Paid order, accepted in full: 3 x 5000 = 15000 gross. ---
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = payAndAwaitStoreAcceptance(qrToken, productId, 3);
        JsonNode pending = readJson(get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchId).cookie(cookie));
        assertThat(pending).hasSize(1);
        String orderId = pending.get(0).get("orderId").asText();
        int orderNumber = pending.get(0).get("orderNumber").asInt();
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderId).cookie(cookie))
                .andExpect(status().isOk());

        JsonNode order = readJson(get("/api/staff/branches/{branchId}/orders/search", branchId)
                .param("orderNumber", String.valueOf(orderNumber))
                .cookie(cookie));
        assertThat(order.get("totalMinorUnits").asLong()).isEqualTo(15000);
        String orderItemId = order.get("items").get(0).get("id").asText();

        // --- A COMPLETED partial refund for 1 unit (5000) - must reduce net sales. ---
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/refunds", branchId, orderId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // --- A second refund attempt the payment provider rejects: FAILED, must NOT reduce net sales. ---
        doThrow(new IllegalStateException("Provider rejected refund"))
                .when(paymentProvider)
                .refund(anyString(), eq(5000L));
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/refunds", branchId, orderId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"orderItemId\":\"" + orderItemId + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        LocalDate zonedToday = LocalDate.now(BUSINESS_DEFAULT_ZONE);

        // --- Sales-only consistency: gross 15000, only the COMPLETED refund (5000) nets out. ---
        JsonNode ozetReport = readJson(get("/api/staff/reports")
                .param("from", zonedToday.toString())
                .param("to", zonedToday.toString())
                .cookie(cookie));
        assertThat(ozetReport.get("grossSalesMinorUnits").asLong()).isEqualTo(15000);
        assertThat(ozetReport.get("refundTotalMinorUnits").asLong()).isEqualTo(5000);
        assertThat(ozetReport.get("netSalesMinorUnits").asLong()).isEqualTo(10000);
        assertThat(ozetReport.get("orderCount").asInt()).isEqualTo(1);

        // The explicit-branchId route (used by /reports/[branchId] "Raporlar") must show
        // byte-identical numbers to the active-branch route ("Özet" dashboard) used above,
        // for the same branch/date/timezone.
        JsonNode explicitBranchReport = readJson(get("/api/staff/branches/{branchId}/reports", branchId)
                .param("from", zonedToday.toString())
                .param("to", zonedToday.toString())
                .cookie(cookie));
        assertThat(explicitBranchReport.get("grossSalesMinorUnits").asLong()).isEqualTo(15000);
        assertThat(explicitBranchReport.get("netSalesMinorUnits").asLong()).isEqualTo(10000);
        assertThat(explicitBranchReport.get("orderCount").asInt()).isEqualTo(1);

        // Kasa's small KPI subset (REPORT_FINANCIAL_SUMMARY_VIEW) must agree with Özet/Raporlar.
        JsonNode kasaSummary = readJson(get("/api/staff/reports/kitchen-summary")
                .param("from", zonedToday.toString())
                .param("to", zonedToday.toString())
                .cookie(cookie));
        assertThat(kasaSummary.get("grossSalesMinorUnits").asLong()).isEqualTo(15000);
        assertThat(kasaSummary.get("netSalesMinorUnits").asLong()).isEqualTo(10000);
        assertThat(kasaSummary.get("orderCount").asInt()).isEqualTo(1);

        // --- A manual expense: must be visible immediately, no approval/status gate. ---
        String expenseCategoryId = objectMapper.readTree(mockMvc.perform(post("/api/staff/expense-categories")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kira-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asText();
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);
        String manualExpenseId = objectMapper.readTree(mockMvc.perform(post("/api/staff/expenses")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + expenseCategoryId
                                + "\",\"amountMinorUnits\":2000,\"incurredAt\":\"" + utcToday + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asText();
        assertThat(manualExpenseId).isNotBlank();
        // No submit/approve/reject workflow exists for a manual expense - it is a straight 404.
        mockMvc.perform(post("/api/staff/expenses/{expenseId}/approve", manualExpenseId).cookie(cookie))
                .andExpect(status().isNotFound());

        // --- A recurring template due today: realized immediately as an immutable snapshot. ---
        MvcResult templateResult = mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + expenseCategoryId
                                + "\",\"amountMinorUnits\":3000,\"dayOfMonth\":" + utcToday.getDayOfMonth()
                                + ",\"startDate\":\"" + utcToday + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = objectMapper.readTree(templateResult.getResponse().getContentAsString()).get("id").asText();

        YearMonth month = YearMonth.from(utcToday);
        JsonNode operatingResultBefore = readJson(get("/api/staff/branches/{branchId}/reports/operating-result", branchId)
                .param("from", month.atDay(1).toString())
                .param("to", month.atEndOfMonth().toString())
                .cookie(cookie));
        assertThat(operatingResultBefore.get("manualExpensesMinorUnits").asLong()).isEqualTo(2000);
        assertThat(operatingResultBefore.get("recurringExpensesMinorUnits").asLong()).isEqualTo(3000);
        assertThat(operatingResultBefore.get("totalExpensesMinorUnits").asLong())
                .isEqualTo(operatingResultBefore.get("manualExpensesMinorUnits").asLong()
                        + operatingResultBefore.get("recurringExpensesMinorUnits").asLong());
        long netSalesForMonth = operatingResultBefore.get("netSalesMinorUnits").asLong();
        assertThat(operatingResultBefore.get("netOperatingResultMinorUnits").asLong())
                .isEqualTo(netSalesForMonth - operatingResultBefore.get("totalExpensesMinorUnits").asLong());

        // --- Deleting the template stops future generation but must not rewrite this already-realized period. ---
        mockMvc.perform(delete("/api/staff/recurring-expense-templates/{templateId}", templateId).cookie(cookie))
                .andExpect(status().isNoContent());

        JsonNode operatingResultAfter = readJson(get("/api/staff/branches/{branchId}/reports/operating-result", branchId)
                .param("from", month.atDay(1).toString())
                .param("to", month.atEndOfMonth().toString())
                .cookie(cookie));
        assertThat(operatingResultAfter.get("manualExpensesMinorUnits").asLong()).isEqualTo(2000);
        assertThat(operatingResultAfter.get("recurringExpensesMinorUnits").asLong()).isEqualTo(3000);
        assertThat(operatingResultAfter.get("totalExpensesMinorUnits").asLong()).isEqualTo(5000);
        assertThat(operatingResultAfter.get("netOperatingResultMinorUnits").asLong()).isEqualTo(netSalesForMonth - 5000);
    }

    private CheckedInVisit payAndAwaitStoreAcceptance(String qrToken, String productId, int quantity) throws Exception {
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

    private JsonNode readJson(MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
