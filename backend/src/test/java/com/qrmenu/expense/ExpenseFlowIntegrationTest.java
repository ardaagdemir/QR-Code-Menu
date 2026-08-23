package com.qrmenu.expense;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.audit.AuditLogEntry;
import com.qrmenu.audit.repository.AuditLogEntryRepository;
import com.qrmenu.expense.repository.ExpenseRepository;
import com.qrmenu.expense.repository.RecurringExpenseTemplateRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Expense records are effective immediately. Covers branch-scoped access, immutable
 * recurring period snapshots, scheduler failure/idempotency and operating-result totals.
 */
class ExpenseFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RecurringExpenseScheduler scheduler;

    @Autowired
    private RecurringExpenseTemplateRepository templateRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @Test
    void manualExpenseIsImmediatelyReportedAndRemainsEditable() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-admin-1@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String categoryId = createCategory(cookie, "Kira");
        String expenseId = createExpense(cookie, branchId, categoryId, 150000);

        JsonNode initialResult = operatingResult(cookie, branchId, YearMonth.from(LocalDate.now(ZoneOffset.UTC)));
        assertThat(initialResult.get("manualExpensesMinorUnits").asLong()).isEqualTo(150000);
        assertThat(initialResult.get("recurringExpensesMinorUnits").asLong()).isZero();
        assertThat(initialResult.get("totalExpensesMinorUnits").asLong()).isEqualTo(150000);

        mockMvc.perform(post("/api/staff/expenses/{expenseId}", expenseId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":999,\"incurredAt\":\""
                                + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isOk());

        JsonNode updatedResult = operatingResult(cookie, branchId, YearMonth.from(LocalDate.now(ZoneOffset.UTC)));
        assertThat(updatedResult.get("manualExpensesMinorUnits").asLong()).isEqualTo(999);
        assertThat(updatedResult.get("recurringExpensesMinorUnits").asLong()).isZero();
        assertThat(updatedResult.get("totalExpensesMinorUnits").asLong()).isEqualTo(999);

        mockMvc.perform(post("/api/staff/expenses/{expenseId}/submit", expenseId).cookie(cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/expenses/{expenseId}/approve", expenseId).cookie(cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/expenses/{expenseId}/reject", expenseId).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void cashierCannotAccessExpenses() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cashierEmail = "expense-cashier-2@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String cashierCookie = StaffFixtures.login(mockMvc, cashierEmail);
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/staff/expenses")
                        .param("branchId", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void branchManagerUsesActiveBranchAndCannotTargetAnotherBranch() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String otherBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Diğer Şube");
        String adminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-admin-3@example.com", "BUSINESS_ADMIN");
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

        // No branchId means the trusted active branch; it is not a business-level expense.
        MvcResult createResult = mockMvc.perform(post("/api/staff/expenses")
                        .cookie(managerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":1000,\"incurredAt\":\""
                                + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String expenseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();
        assertThat(expenseId).isNotBlank();

        // Another branch is out of scope.
        mockMvc.perform(post("/api/staff/expenses")
                        .cookie(managerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + otherBranchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":1000,\"incurredAt\":\"" + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isForbidden());

        MockCookie otherAdminCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, otherBranchId, "expense-admin-other-3@example.com", "BUSINESS_ADMIN"));
        String otherExpenseId = objectMapper.readTree(mockMvc.perform(post("/api/staff/expenses")
                        .cookie(otherAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":2000,\"incurredAt\":\""
                                + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/staff/expenses/{expenseId}", otherExpenseId)
                        .cookie(managerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":999,\"incurredAt\":\""
                                + LocalDate.now(ZoneOffset.UTC) + "\"}"))
                .andExpect(status().isForbidden());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String otherTemplateId = objectMapper.readTree(mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(otherAdminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":2000,\"description\":\"Diğer şube kirası\",\"dayOfMonth\":1,\"startDate\":\""
                                + today + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asText();

        String updateTemplateBody = "{\"categoryId\":\"" + categoryId
                + "\",\"amountMinorUnits\":3000,\"description\":\"Yetkisiz değişiklik\",\"dayOfMonth\":2,\"startDate\":\""
                + today + "\"}";
        mockMvc.perform(post("/api/staff/recurring-expense-templates/{templateId}", otherTemplateId)
                        .cookie(managerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateTemplateBody))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/staff/recurring-expense-templates/{templateId}", otherTemplateId)
                        .cookie(managerCookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/recurring-expense-templates/{templateId}/activate", otherTemplateId)
                        .cookie(managerCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void dueRecurringTemplateCreatesAnExpenseImmediatelyAndSchedulerRemainsIdempotent() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 4");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-admin-4@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Elektrik");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int dueDay = Math.max(1, today.getDayOfMonth() - 1);
        LocalDate dueDate = YearMonth.from(today).atDay(dueDay);
        MvcResult templateResult = mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":25000,\"dayOfMonth\":" + dueDay
                                + ",\"startDate\":\"" + today + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = objectMapper.readTree(templateResult.getResponse().getContentAsString()).get("id").asText();

        scheduler.generateDueExpenses();
        scheduler.generateDueExpenses();

        JsonNode expenses = objectMapper.readTree(mockMvc.perform(get("/api/staff/expenses")
                        .param("branchId", branchId)
                        .param("from", today.minusDays(1).toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(expenses).isEmpty();

        List<Expense> generatedExpenses = expenseRepository.findAll().stream()
                .filter(expense -> templateId.equals(String.valueOf(expense.getSourceTemplateId())))
                .toList();
        assertThat(generatedExpenses).hasSize(1);
        Expense generatedExpense = generatedExpenses.get(0);
        assertThat(generatedExpense.getAmountMinorUnits()).isEqualTo(25000);
        assertThat(generatedExpense.getIncurredAt()).isEqualTo(dueDate);
        assertThat(generatedExpense.getBranchId()).isEqualTo(UUID.fromString(branchId));

        mockMvc.perform(post("/api/staff/recurring-expense-templates/{templateId}/deactivate", templateId)
                        .cookie(cookie))
                .andExpect(status().isOk());
        assertThat(templateRepository.findById(UUID.fromString(templateId)).orElseThrow().isActive()).isFalse();
        mockMvc.perform(post("/api/staff/recurring-expense-templates/{templateId}/activate", templateId)
                        .cookie(cookie))
                .andExpect(status().isOk());
        assertThat(templateRepository.findById(UUID.fromString(templateId)).orElseThrow().isActive()).isTrue();
        assertThat(expenseRepository.findById(generatedExpense.getId()).orElseThrow().getAmountMinorUnits())
                .isEqualTo(25000);

        JsonNode result = operatingResult(cookie, branchId, YearMonth.from(today));
        assertThat(result.get("manualExpensesMinorUnits").asLong()).isZero();
        assertThat(result.get("recurringExpensesMinorUnits").asLong()).isEqualTo(25000);
        assertThat(result.get("totalExpensesMinorUnits").asLong()).isEqualTo(25000);

        mockMvc.perform(post("/api/staff/expenses/{expenseId}", generatedExpense.getId())
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":999,\"incurredAt\":\""
                                + dueDate + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/staff/recurring-expense-templates/{templateId}", templateId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":99000,\"description\":\"Güncel elektrik\",\"dayOfMonth\":"
                                + dueDay + ",\"startDate\":\"" + today + "\"}"))
                .andExpect(status().isOk());

        assertThat(expenseRepository.findById(generatedExpense.getId()).orElseThrow().getAmountMinorUnits())
                .isEqualTo(25000);
        assertThat(operatingResult(cookie, branchId, YearMonth.from(today)).get("totalExpensesMinorUnits").asLong())
                .isEqualTo(25000);

        mockMvc.perform(delete("/api/staff/recurring-expense-templates/{templateId}", templateId).cookie(cookie))
                .andExpect(status().isNoContent());

        JsonNode templates = objectMapper.readTree(mockMvc.perform(get("/api/staff/recurring-expense-templates")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(templates.findValuesAsText("id")).doesNotContain(templateId);

        RecurringExpenseTemplate archivedTemplate = templateRepository.findById(UUID.fromString(templateId)).orElseThrow();
        assertThat(archivedTemplate.isDeleted()).isTrue();
        assertThat(archivedTemplate.isActive()).isFalse();
        assertThat(expenseRepository.findById(generatedExpense.getId()).orElseThrow().getAmountMinorUnits())
                .isEqualTo(25000);

        YearMonth nextPeriod = YearMonth.from(today).plusMonths(1);
        scheduler.generateDueExpensesForTemplates(List.of(UUID.fromString(templateId)), nextPeriod.atEndOfMonth());
        assertThat(expenseRepository.existsBySourceTemplateIdAndGeneratedForPeriod(
                        UUID.fromString(templateId), nextPeriod.toString()))
                .isFalse();
        assertThat(operatingResult(cookie, branchId, YearMonth.from(today)).get("totalExpensesMinorUnits").asLong())
                .isEqualTo(25000);
    }

    @Test
    void oneRecurringTemplateFailureDoesNotBlockAnotherTemplatesDuePeriods() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Failure Isolation");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-failure@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Bakım");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        YearMonth previousPeriod = YearMonth.from(today).minusMonths(1);
        YearMonth currentPeriod = YearMonth.from(today);

        RecurringExpenseTemplate validTemplate = templateRepository.saveAndFlush(new RecurringExpenseTemplate(
                UUID.fromString(businessId),
                UUID.fromString(branchId),
                UUID.fromString(categoryId),
                32000,
                "Servis sağlayıcı",
                null,
                1,
                previousPeriod.atDay(1),
                null));

        scheduler.generateDueExpensesForTemplates(List.of(UUID.randomUUID(), validTemplate.getId()), today);

        assertThat(expenseRepository.existsBySourceTemplateIdAndGeneratedForPeriod(
                        validTemplate.getId(), previousPeriod.toString()))
                .isTrue();
        assertThat(expenseRepository.existsBySourceTemplateIdAndGeneratedForPeriod(
                        validTemplate.getId(), currentPeriod.toString()))
                .isTrue();
    }

    @Test
    void schedulerDoesNotGenerateFutureRecurringExpensePeriods() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Future Period");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-future@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Gelecek Dönem");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate nextMonthStart = YearMonth.from(today).plusMonths(1).atDay(1);

        MvcResult templateResult = mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":18000,\"dayOfMonth\":1,\"startDate\":\""
                                + nextMonthStart + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID templateId = UUID.fromString(
                objectMapper.readTree(templateResult.getResponse().getContentAsString()).get("id").asText());

        scheduler.generateDueExpensesForTemplates(List.of(templateId), today);

        assertThat(expenseRepository.existsBySourceTemplateIdAndGeneratedForPeriod(
                        templateId, YearMonth.from(nextMonthStart).toString()))
                .isFalse();
    }

    @Test
    void operatingResultIncludesManualAndDueRecurringExpensesOnly() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 5");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String otherBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Diğer Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-admin-5@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Temizlik");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        createExpense(cookie, branchId, categoryId, 4000);
        MvcResult realizedTemplateResult = mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":6000,\"dayOfMonth\":" + today.getDayOfMonth()
                                + ",\"startDate\":\"" + today + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String realizedTemplateId = objectMapper.readTree(realizedTemplateResult.getResponse().getContentAsString())
                .get("id")
                .asText();
        LocalDate nextMonthStart = YearMonth.from(today).plusMonths(1).atDay(1);
        mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":9000,\"dayOfMonth\":1,\"startDate\":\""
                                + nextMonthStart + "\"}"))
                .andExpect(status().isCreated());

        MockCookie otherBranchCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.bootstrapAndLogin(
                mockMvc,
                TEST_ADMIN_TOKEN,
                businessId,
                otherBranchId,
                "expense-admin-other-5@example.com",
                "BUSINESS_ADMIN"));
        createExpense(otherBranchCookie, otherBranchId, categoryId, 7000);
        mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(otherBranchCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":8000,\"dayOfMonth\":" + today.getDayOfMonth()
                                + ",\"startDate\":\"" + today + "\"}"))
                .andExpect(status().isCreated());

        // Archiving the template must stop future generation without removing its
        // already realized expense from this period's report.
        mockMvc.perform(delete("/api/staff/recurring-expense-templates/{templateId}", realizedTemplateId).cookie(cookie))
                .andExpect(status().isNoContent());

        JsonNode result = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/{branchId}/reports/operating-result", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(result.get("manualExpensesMinorUnits").asLong()).isEqualTo(4000);
        assertThat(result.get("recurringExpensesMinorUnits").asLong()).isEqualTo(6000);
        assertThat(result.get("totalExpensesMinorUnits").asLong()).isEqualTo(10000);
        assertThat(result.get("netOperatingResultMinorUnits").asLong())
                .isEqualTo(result.get("netSalesMinorUnits").asLong() - 10000);
    }

    @Test
    void receiptIsOnlyDownloadableThroughTheAuthenticatedTenantScopedEndpoint() throws Exception {
        byte[] pdfBytes = "%PDF-1.4 fake receipt".getBytes(StandardCharsets.US_ASCII);

        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 6");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String otherBranchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Diğer Şube");
        String adminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-admin-6@example.com", "BUSINESS_ADMIN");
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
        String otherBusinessBranchId = TenantFixtures.createBranch(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, otherBusinessId, "Şube");
        String otherAdminCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, otherBusinessId, otherBusinessBranchId, "expense-admin-7@example.com", "BUSINESS_ADMIN");
        mockMvc.perform(get("/api/staff/expenses/{expenseId}/receipt", expenseId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, otherAdminCookie)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelledManualExpenseStaysListedAndAuditedButDropsOutOfReports() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 8");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-admin-8@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Kırtasiye");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        String expenseId = createExpense(cookie, branchId, categoryId, 5000);
        assertThat(operatingResult(cookie, branchId, YearMonth.from(today)).get("manualExpensesMinorUnits").asLong())
                .isEqualTo(5000);

        JsonNode cancelled = objectMapper.readTree(mockMvc.perform(post("/api/staff/expenses/{expenseId}/cancel", expenseId)
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(cancelled.get("cancelledAt").isNull()).isFalse();

        // Still listed - a cancelled expense is voided, not deleted.
        JsonNode expenses = objectMapper.readTree(mockMvc.perform(get("/api/staff/expenses")
                        .param("branchId", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(expenses.findValuesAsText("id")).contains(expenseId);

        // No longer counted toward Toplam Gider / Net Sonuç.
        JsonNode resultAfterCancel = operatingResult(cookie, branchId, YearMonth.from(today));
        assertThat(resultAfterCancel.get("manualExpensesMinorUnits").asLong()).isZero();
        assertThat(resultAfterCancel.get("totalExpensesMinorUnits").asLong()).isZero();

        // Audit trail records the cancellation (soft-void, not a hard delete).
        List<AuditLogEntry> auditEntries = auditLogEntryRepository.findAll().stream()
                .filter(entry -> "Expense".equals(entry.getEntityType()) && expenseId.equals(String.valueOf(entry.getEntityId())))
                .toList();
        assertThat(auditEntries).anyMatch(entry -> "CANCELLED".equals(entry.getAction()));

        // A cancelled expense can no longer be edited...
        mockMvc.perform(post("/api/staff/expenses/{expenseId}", expenseId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":999,\"incurredAt\":\"" + today + "\"}"))
                .andExpect(status().isBadRequest());

        // ...or cancelled a second time.
        mockMvc.perform(post("/api/staff/expenses/{expenseId}/cancel", expenseId).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void systemGeneratedRecurringRealizationCannotBeCancelled() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Expense Business 9");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "expense-admin-9@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Elektrik");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // dayOfMonth == today triggers RecurringExpenseGenerator to realize this period's
        // expense immediately on template creation (see ExpenseService#createTemplate).
        MvcResult templateResult = mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":12000,\"dayOfMonth\":" + today.getDayOfMonth()
                                + ",\"startDate\":\"" + today + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = objectMapper.readTree(templateResult.getResponse().getContentAsString()).get("id").asText();
        Expense generatedExpense = expenseRepository.findAll().stream()
                .filter(expense -> templateId.equals(String.valueOf(expense.getSourceTemplateId())))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/staff/expenses/{expenseId}/cancel", generatedExpense.getId()).cookie(cookie))
                .andExpect(status().isBadRequest());

        assertThat(expenseRepository.findById(generatedExpense.getId()).orElseThrow().isCancelled()).isFalse();
        assertThat(operatingResult(cookie, branchId, YearMonth.from(today)).get("recurringExpensesMinorUnits").asLong())
                .isEqualTo(12000);
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

    private JsonNode operatingResult(MockCookie cookie, String branchId, YearMonth period) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/{branchId}/reports/operating-result", branchId)
                        .param("from", period.atDay(1).toString())
                        .param("to", period.atEndOfMonth().toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }
}
