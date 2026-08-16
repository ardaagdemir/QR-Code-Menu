package com.qrmenu.chain;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gap-analysis #16: no current user-facing role has chain-comparison access. */
class ChainComparisonFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void platformCanViewComparisonWhileBusinessAdminCannot() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Compare Isolation");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        String platformCookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "compare-platform@example.com", "PLATFORM_ADMIN");
        String cookie = StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "compare-admin@example.com", "BUSINESS_ADMIN");

        JsonNode result = objectMapper.readTree(mockMvc.perform(get("/api/staff/branches/comparison")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, platformCookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(result).hasSize(2);
        assertThat(result).extracting(row -> row.get("branchId").asText()).containsExactlyInAnyOrder(branchId, branchB);

        mockMvc.perform(get("/api/staff/branches/comparison")
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, cookie)))
                .andExpect(status().isForbidden());
    }
}
