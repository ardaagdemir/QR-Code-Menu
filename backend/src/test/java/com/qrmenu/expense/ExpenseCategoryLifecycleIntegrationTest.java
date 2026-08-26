package com.qrmenu.expense;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.audit.AuditLogEntry;
import com.qrmenu.audit.repository.AuditLogEntryRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
 * Gider Kategorileri lifecycle: rename, deactivate/reactivate symmetry, case-insensitive
 * duplicate name guard, and the rule that only an active category may be newly assigned
 * to an Expense/RecurringExpenseTemplate while past records keep referencing it unharmed.
 */
class ExpenseCategoryLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @Test
    void categoryCanBeRenamedWithTrimmingAndBlankNamesAreRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Category Business 1");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "category-admin-1@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        String categoryId = createCategory(cookie, "Kira");

        JsonNode renamed = objectMapper.readTree(mockMvc.perform(post("/api/staff/expense-categories/{id}", categoryId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Kira Gideri  \"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(renamed.get("name").asText()).isEqualTo("Kira Gideri");

        mockMvc.perform(post("/api/staff/expense-categories/{id}", categoryId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        List<AuditLogEntry> auditEntries = auditLogEntryRepository.findAll().stream()
                .filter(entry -> "ExpenseCategory".equals(entry.getEntityType()) && categoryId.equals(String.valueOf(entry.getEntityId())))
                .toList();
        assertThat(auditEntries).anyMatch(entry -> "RENAMED".equals(entry.getAction()));
    }

    @Test
    void duplicateCategoryNamesAreRejectedCaseInsensitivelyOnCreateAndRename() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Category Business 2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "category-admin-2@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);

        createCategory(cookie, "Elektrik");
        String secondCategoryId = createCategory(cookie, "Su");

        mockMvc.perform(post("/api/staff/expense-categories")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"elektrik\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/staff/expense-categories/{id}", secondCategoryId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"elektrik\"}"))
                .andExpect(status().isConflict());

        // Renaming a category to its own current name (mere case change) must still succeed.
        mockMvc.perform(post("/api/staff/expense-categories/{id}", secondCategoryId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SU\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deactivateAndActivateAreSymmetricAndBothAudited() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Category Business 3");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "category-admin-3@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Bakım");

        mockMvc.perform(post("/api/staff/expense-categories/{id}/deactivate", categoryId).cookie(cookie))
                .andExpect(status().isOk());
        assertThat(findCategory(cookie, categoryId).get("active").asBoolean()).isFalse();

        mockMvc.perform(post("/api/staff/expense-categories/{id}/activate", categoryId).cookie(cookie))
                .andExpect(status().isOk());
        assertThat(findCategory(cookie, categoryId).get("active").asBoolean()).isTrue();

        List<String> actions = auditLogEntryRepository.findAll().stream()
                .filter(entry -> "ExpenseCategory".equals(entry.getEntityType()) && categoryId.equals(String.valueOf(entry.getEntityId())))
                .map(AuditLogEntry::getAction)
                .toList();
        assertThat(actions).contains("DEACTIVATED", "ACTIVATED");
    }

    @Test
    void inactiveCategoryCannotBeAssignedToNewExpenseOrTemplateButPastRecordsAreUnaffected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Category Business 4");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "category-admin-4@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(cookie, "Temizlik");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        MvcResult expenseResult = mockMvc.perform(post("/api/staff/expenses")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":3000,\"incurredAt\":\"" + today + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String expenseId = objectMapper.readTree(expenseResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/staff/expense-categories/{id}/deactivate", categoryId).cookie(cookie))
                .andExpect(status().isOk());

        // A new expense can no longer select the now-inactive category.
        mockMvc.perform(post("/api/staff/expenses")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":1000,\"incurredAt\":\"" + today + "\"}"))
                .andExpect(status().isBadRequest());

        // Nor can a new recurring template.
        mockMvc.perform(post("/api/staff/recurring-expense-templates")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":1000,\"dayOfMonth\":1,\"startDate\":\"" + today + "\"}"))
                .andExpect(status().isBadRequest());

        // Editing the pre-existing expense while keeping its (now-inactive) category still works.
        mockMvc.perform(post("/api/staff/expenses/{expenseId}", expenseId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + categoryId + "\",\"amountMinorUnits\":3500,\"incurredAt\":\"" + today + "\"}"))
                .andExpect(status().isOk());

        // Renaming the now-inactive, already-used category must not disturb the historical expense's link.
        mockMvc.perform(post("/api/staff/expense-categories/{id}", categoryId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Temizlik Malzemeleri\"}"))
                .andExpect(status().isOk());

        JsonNode expenses = objectMapper.readTree(mockMvc.perform(get("/api/staff/expenses")
                        .param("branchId", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode matched = null;
        for (JsonNode expense : expenses) {
            if (expenseId.equals(expense.get("id").asText())) {
                matched = expense;
            }
        }
        assertThat(matched).isNotNull();
        assertThat(matched.get("categoryId").asText()).isEqualTo(categoryId);
        assertThat(matched.get("categoryName").asText()).isEqualTo("Temizlik Malzemeleri");
        assertThat(matched.get("amountMinorUnits").asLong()).isEqualTo(3500);

        // Reactivating makes it selectable again.
        mockMvc.perform(post("/api/staff/expense-categories/{id}/activate", categoryId).cookie(cookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/expenses")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":\"" + branchId + "\",\"categoryId\":\"" + categoryId
                                + "\",\"amountMinorUnits\":500,\"incurredAt\":\"" + today + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void cashierCannotRenameOrReactivateCategory() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Category Business 5");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "category-admin-5@example.com");
        MockCookie adminCookieObj = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie);
        String categoryId = createCategory(adminCookieObj, "Diğer");

        String cashierEmail = "category-cashier-5@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        MockCookie cashierCookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, StaffFixtures.login(mockMvc, cashierEmail));

        mockMvc.perform(post("/api/staff/expense-categories/{id}", categoryId)
                        .cookie(cashierCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Değiştirilmiş\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/staff/expense-categories/{id}/activate", categoryId).cookie(cashierCookie))
                .andExpect(status().isForbidden());
    }

    private String createCategory(MockCookie cookie, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/expense-categories")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode findCategory(MockCookie cookie, String categoryId) throws Exception {
        JsonNode categories = objectMapper.readTree(mockMvc.perform(get("/api/staff/expense-categories").cookie(cookie))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        for (JsonNode category : categories) {
            if (categoryId.equals(category.get("id").asText())) {
                return category;
            }
        }
        throw new AssertionError("Category not found in list response: " + categoryId);
    }
}
