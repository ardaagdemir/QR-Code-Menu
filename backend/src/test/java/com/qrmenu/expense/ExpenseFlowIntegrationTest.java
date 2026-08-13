package com.qrmenu.expense;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #10 (product-requirements.md Section 16): expense DRAFT->SUBMITTED->
 * APPROVED/REJECTED state machine + immutability after approval, EXPENSE_VIEW/MANAGE/
 * APPROVE permission gates, branch-scoped access for BRANCH_MANAGER, recurring template
 * scheduler idempotency, and the Section 17 operating-result figure.
 */
class ExpenseFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RecurringExpenseScheduler scheduler;

    @Test
    void draftSubmitApproveIsImmutableAfterward() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "expense-admin-1@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String categoryId = createCategory(cookie, "Kira");
        String expenseId = createExpense(cookie, branchId, categoryId, 150000);

        mockMvc.perform(post("/api/staff/expenses/{expenseId}/submit", expenseId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("SUBMITTED"));

        JsonNode approved = objectMapper.readTree(mockMvc.perform(post("/api/staff/expenses/{expenseId}/approve", expenseId).cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("approvedByStaffUserId")).isNotNull();

        // APPROVED is immutable: editing must fail.
        mockMvc.perform(post("/api/staff/expenses/{expenseId}", expenseId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":999,\"incurredAt\":\""
                                + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void kitchenStaffCannotAccessExpenses() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String kitchenEmail = "expense-kitchen-2@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String kitchenCookie = StaffFixtures.login(mockMvc, kitchenEmail);
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenCookie);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/staff/expenses")
                        .param("branchId", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchManagerCannotApproveOrCreateBusinessLevelExpense() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String otherBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Diğer Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "expense-admin-3@example.com");
        MockCookie adminCookieObj = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(adminCookieObj, "Malzeme");

        String managerEmail = "expense-manager-3@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + managerEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BRANCH_MANAGER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        MockCookie managerCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, managerEmail));

        // Business-level (no branchId) expense creation is BUSINESS_ADMIN-only.
        mockMvc.perform(post("/api/staff/expenses")
                        .cookie(managerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":1000,\"incurredAt\":\""
                                + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isForbidden());

        // Own-branch expense creation and submission are allowed.
        String expenseId = createExpense(managerCookie, branchId, categoryId, 5000);
        mockMvc.perform(post("/api/staff/expenses/{expenseId}/submit", expenseId).cookie(managerCookie))
                .andExpect(status().isOk());

        // Approval requires EXPENSE_APPROVE (BUSINESS_ADMIN-only).
        mockMvc.perform(post("/api/staff/expenses/{expenseId}/approve", expenseId).cookie(managerCookie))
                .andExpect(status().isForbidden());

        // Another branch is out of scope.
        mockMvc.perform(post("/api/staff/expenses")
                        .cookie(managerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + otherBranchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":1000,\"incurredAt\":\"" + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recurringTemplateSchedulerDraftsExactlyOnceForTheDuePeriod() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 4");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "expense-admin-4@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Elektrik");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult templateResult = mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":25000,\"dayOfMonth\":" + today.getDayOfMonth()
                                + ",\"startDate\":\"" + today.minusDays(1) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = objectMapper.readTree(templateResult.getResponse().getContentAsString()).get("id").asText();

        scheduler.generateDueDrafts();
        scheduler.generateDueDrafts();

        JsonNode expenses = objectMapper.readTree(mockMvc.perform(get("/api/staff/expenses")
                        .param("branchId", branchId)
                        .param("from", today.minusDays(1).toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        long generatedCount = 0;
        for (JsonNode expense : expenses) {
            if (templateId.equals(expense.path("sourceTemplateId").asText(null))) {
                generatedCount++;
                assertThat(expense.get("status").asText()).isEqualTo("DRAFT");
                assertThat(expense.get("amountMinorUnits").asLong()).isEqualTo(25000);
            }
        }
        assertThat(generatedCount).isEqualTo(1);
    }

    @Test
    void operatingResultSubtractsApprovedExpensesFromNetSales() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 5");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "expense-admin-5@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Temizlik");
        String expenseId = createExpense(cookie, branchId, categoryId, 4000);
        mockMvc.perform(post("/api/staff/expenses/{expenseId}/submit", expenseId).cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/expenses/{expenseId}/approve", expenseId).cookie(cookie)).andExpect(status().isOk());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        JsonNode result = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/{branchId}/reports/operating-result", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(result.get("approvedExpensesMinorUnits").asLong()).isEqualTo(4000);
        assertThat(result.get("netOperatingResultMinorUnits").asLong())
                .isEqualTo(result.get("netSalesMinorUnits").asLong() - 4000);
    }

    @Test
    void receiptIsOnlyDownloadableThroughTheAuthenticatedTenantScopedEndpoint() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 fake receipt".getBytes(StandardCharsets.US_ASCII);

        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 6");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String otherBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Diğer Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessId, "expense-admin-6@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Kira");

        JsonNode uploadResult = objectMapper.readTree(mockMvc.perform(multipart("/api/staff/media/receipts")
                        .file(new MockMultipartFile("file", "fis.pdf", "application/pdf", pdfBytes))
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String receiptUrl = uploadResult.get("url").asText();

        MvcResult createResult = mockMvc.perform(post("/api/staff/expenses")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":15000,\"incurredAt\":\"" + LocalDate.now(ZoneOffset.UTC)
                                + "\",\"receiptImageUrl\":\"" + receiptUrl + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String expenseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        // Not reachable on the public /media/** path.
        mockMvc.perform(get(receiptUrl.replaceFirst("^https?://[^/]+", "")).cookie(cookie)).andExpect(status().isNotFound());

        // Owner can download it through the authenticated endpoint.
        mockMvc.perform(get("/api/staff/expenses/{expenseId}/receipt", expenseId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdfBytes));

        // A staff member scoped to a different branch of the same business cannot.
        String otherManagerEmail = "expense-manager-6@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + otherManagerEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BRANCH_MANAGER\",\"branchIds\":[\"" + otherBranchId + "\"]}"))
                .andExpect(status().isCreated());
        MockCookie otherManagerCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, otherManagerEmail));
        mockMvc.perform(get("/api/staff/expenses/{expenseId}/receipt", expenseId).cookie(otherManagerCookie))
                .andExpect(status().isForbidden());

        // A different business/tenant entirely cannot, either (404s rather than 403s to avoid tenant enumeration).
        String otherBusinessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 7");
        String otherAdminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, otherBusinessId, "expense-admin-7@example.com");
        mockMvc.perform(get("/api/staff/expenses/{expenseId}/receipt", expenseId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, otherAdminCookie)))
                .andExpect(status().isNotFound());
    }

    private String createCategory(MockCookie cookie, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/expense-categories")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createExpense(MockCookie cookie, String branchId, String categoryId, long amountMinorUnits) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/expenses")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":"
                                + amountMinorUnits + ",\"incurredAt\":\"" + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
