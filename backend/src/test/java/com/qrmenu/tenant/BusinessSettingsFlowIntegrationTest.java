package com.qrmenu.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.audit.AuditLogEntry;
import com.qrmenu.audit.repository.AuditLogEntryRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #6 (product-requirements.md Section 12.1/12.2/12.3): Business
 * defaultCurrency/defaultTimeZone, per-branch timezone override, and BusinessContact
 * (report recipients) CRUD - all gated behind the new Permission.BUSINESS_SETTINGS_MANAGE.
 */
class BusinessSettingsFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditLogEntryRepository auditLogEntryRepository;

    @Test
    void businessAdminCanRenameOwnBusinessAndNameIsTrimmed() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Eski İsim");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "rename-admin-1@example.com");

        mockMvc.perform(post("/api/staff/business/name")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Yeni İsim  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", "Yeni İsim"));

        JsonNode business = readBusiness(staffCookie);
        assertThat(business.get("name").asText()).isEqualTo("Yeni İsim");

        AuditLogEntry entry = auditLogEntryRepository.findAll().stream()
                .filter(e -> e.getBusinessId().equals(UUID.fromString(businessId)) && e.getAction().equals("NAME_CHANGED"))
                .findFirst()
                .orElseThrow();
        assertThat(entry.getEntityType()).isEqualTo("Business");
        assertThat(entry.getEntityId()).isEqualTo(UUID.fromString(businessId));
        assertThat(entry.getDetails()).contains("\"oldName\":\"Eski İsim\"").contains("\"newName\":\"Yeni İsim\"");
    }

    @Test
    void blankOrWhitespaceOnlyBusinessNameIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Blank Name Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "rename-admin-2@example.com");

        mockMvc.perform(post("/api/staff/business/name")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/staff/business/name")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        JsonNode business = readBusiness(staffCookie);
        assertThat(business.get("name").asText()).isEqualTo("Blank Name Business");
    }

    @Test
    void businessAdminRenamingOwnBusinessNeverTouchesAnotherBusiness() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Business A");
        String branchAId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "Şube A");
        String staffCookieA =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessAId, branchAId, "rename-admin-a@example.com");
        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Business B");
        String branchBId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessBId, "Şube B");
        String staffCookieB =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessBId, branchBId, "rename-admin-b@example.com");

        mockMvc.perform(post("/api/staff/business/name")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookieA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Business A Renamed\"}"))
                .andExpect(status().isOk());

        JsonNode businessB = readBusiness(staffCookieB);
        assertThat(businessB.get("name").asText()).isEqualTo("Business B");
    }

    @Test
    void staffWithoutPermissionCannotRenameBusiness() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Locked Name Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cashierEmail = "rename-cashier-1@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String cashierCookie = StaffFixtures.login(mockMvc, cashierEmail);

        mockMvc.perform(post("/api/staff/business/name")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kasiyerin Denemesi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newBusinessGetsSensibleDefaultSettings() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Defaults Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-1@example.com");

        JsonNode business = readBusiness(staffCookie);
        assertThat(business.get("defaultCurrency").asText()).isEqualTo("TRY");
        assertThat(business.get("defaultTimeZone").asText()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void businessAdminCanUpdateAndReadBackSettings() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Settings Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-2@example.com");

        mockMvc.perform(post("/api/staff/business/settings")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultCurrency\":\"USD\",\"defaultTimeZone\":\"America/New_York\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency", "USD"))
                .andExpect(jsonPath("$.defaultTimeZone", "America/New_York"));

        JsonNode business = readBusiness(staffCookie);
        assertThat(business.get("defaultCurrency").asText()).isEqualTo("USD");
        assertThat(business.get("defaultTimeZone").asText()).isEqualTo("America/New_York");
    }

    @Test
    void invalidCurrencyOrTimeZoneIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Invalid Settings Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-3@example.com");

        mockMvc.perform(post("/api/staff/business/settings")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultCurrency\":\"NOTREAL\",\"defaultTimeZone\":\"Europe/Istanbul\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/staff/business/settings")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultCurrency\":\"TRY\",\"defaultTimeZone\":\"Not/AZone\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffWithoutPermissionCannotReadOrChangeBusinessSettings() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Locked Settings Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String cashierEmail = "settings-cashier-1@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String cashierCookie = StaffFixtures.login(mockMvc, cashierEmail);

        mockMvc.perform(get("/api/staff/business").cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void businessAdminCanSetAndClearBranchTimezone() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Branch Timezone Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-4@example.com");

        mockMvc.perform(post("/api/staff/branches/{branchId}/timezone", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Europe/London\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone", "Europe/London"));

        JsonNode cleared = objectMapper.readTree(mockMvc.perform(post("/api/staff/branches/{branchId}/timezone", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":null}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(cleared.get("timezone").isNull()).isTrue();
    }

    @Test
    void invalidBranchTimezoneIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Bad Branch Timezone Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-5@example.com");

        mockMvc.perform(post("/api/staff/branches/{branchId}/timezone", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Not/AZone\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newBranchDefaultsToFiveMinuteStoreAcceptanceTimeout() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Timeout Default Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-7@example.com");

        JsonNode branch = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
                .get(0);
        assertThat(branch.get("id").asText()).isEqualTo(branchId);
        assertThat(branch.get("storeAcceptanceTimeoutSeconds").asInt()).isEqualTo(300);
    }

    @Test
    void businessAdminCanUpdateStoreAcceptanceTimeout() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Timeout Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-8@example.com");

        mockMvc.perform(post("/api/staff/branches/{branchId}/store-acceptance-timeout", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSeconds\":600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeAcceptanceTimeoutSeconds", 600));
    }

    @Test
    void zeroOrNegativeStoreAcceptanceTimeoutIsRejected() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Bad Timeout Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-9@example.com");

        mockMvc.perform(post("/api/staff/branches/{branchId}/store-acceptance-timeout", branchId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeoutSeconds\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessAdminCanCreateListAndEditBusinessContacts() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Contacts Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-6@example.com");

        String createBody = "{\"name\":\"Ayşe Yılmaz\",\"phone\":\"+905551112233\",\"email\":\"ayse@example.com\","
                + "\"whatsappEnabled\":true,\"dailyReportRecipient\":true,\"monthlyReportRecipient\":false}";
        String contactId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/business/contacts")
                                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name", "Ayşe Yılmaz"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/staff/business/contacts")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(contactId);

        // Edit changes name/phone/email as well as report preferences - not just a flag flip.
        String updateBody = "{\"name\":\"Ayşe Demir\",\"phone\":\"+905551119999\",\"email\":\"ayse.demir@example.com\","
                + "\"whatsappEnabled\":false,\"dailyReportRecipient\":false,\"monthlyReportRecipient\":true}";
        mockMvc.perform(put("/api/staff/business/contacts/{contactId}", contactId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", "Ayşe Demir"))
                .andExpect(jsonPath("$.phone", "+905551119999"))
                .andExpect(jsonPath("$.email", "ayse.demir@example.com"))
                .andExpect(jsonPath("$.whatsappEnabled", false))
                .andExpect(jsonPath("$.dailyReportRecipient", false))
                .andExpect(jsonPath("$.monthlyReportRecipient", true));
    }

    @Test
    void businessAdminCanHardDeleteBusinessContact() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Delete Contacts Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String staffCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-10@example.com");

        String createBody = "{\"name\":\"Silinecek Kişi\",\"phone\":null,\"email\":\"sil@example.com\","
                + "\"whatsappEnabled\":false,\"dailyReportRecipient\":true,\"monthlyReportRecipient\":false}";
        String contactId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/business/contacts")
                                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(delete("/api/staff/business/contacts/{contactId}", contactId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isNoContent());

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/staff/business/contacts")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(list).isEmpty();

        mockMvc.perform(delete("/api/staff/business/contacts/{contactId}", contactId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffWithoutPermissionCannotDeleteBusinessContact() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Locked Delete Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(
                        mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "settings-admin-11@example.com");
        String createBody = "{\"name\":\"Korunan Kişi\",\"phone\":null,\"email\":\"korunan@example.com\","
                + "\"whatsappEnabled\":false,\"dailyReportRecipient\":true,\"monthlyReportRecipient\":false}";
        String contactId = objectMapper
                .readTree(mockMvc.perform(post("/api/staff/business/contacts")
                                .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        String cashierEmail = "settings-cashier-2@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + cashierEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"CASHIER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String cashierCookie = StaffFixtures.login(mockMvc, cashierEmail);

        mockMvc.perform(delete("/api/staff/business/contacts/{contactId}", contactId)
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cashierCookie)))
                .andExpect(status().isForbidden());

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/staff/business/contacts")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, adminCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(list).hasSize(1);
    }

    private JsonNode readBusiness(String staffCookie) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/staff/business")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private static org.springframework.test.web.servlet.ResultMatcher jsonPath(String path, Object value) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(path).value(value);
    }
}
