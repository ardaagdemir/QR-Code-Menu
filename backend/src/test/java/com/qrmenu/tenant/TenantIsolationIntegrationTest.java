package com.qrmenu.tenant;

import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.tenant.repository.BranchRepository;
import com.qrmenu.tenant.repository.BusinessRepository;
import com.qrmenu.tenant.repository.RestaurantTableRepository;
import com.qrmenu.tenant.repository.TableQrTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the tenant-isolation and QR-token rules called out explicitly in the
 * Milestone 2 scope: business_id checks in the service layer (Section 2), and "exactly
 * one ACTIVE token per table, revoked-not-deleted, regenerate-not-rotate" (Section 5).
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private TableQrTokenRepository qrTokenRepository;

    @Test
    void internalEndpointsRejectRequestsWithoutAValidAdminToken() throws Exception {
        mockMvc.perform(post("/internal/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Auth Business\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/businesses")
                        .header("X-Internal-Admin-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wrong Token Business\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingATableUnderABranchOwnedByAnotherBusinessIsRejected() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Business A");
        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Business B");
        String branchUnderA =
                TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "A - Main Branch");

        // Business B's id in the path, but the branch actually belongs to Business A.
        mockMvc.perform(post(
                                "/internal/businesses/{businessId}/branches/{branchId}/tables", businessBId, branchUnderA)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Table 1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void regeneratingAQrTokenUnderAForeignBusinessIsRejected() throws Exception {
        String businessAId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Business A2");
        String businessBId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Business B2");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, "Branch");
        String tableId =
                TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessAId, branchId, "Table 1");

        mockMvc.perform(post("/internal/businesses/{businessId}/tables/{tableId}/qr-tokens", businessBId, tableId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void regeneratingAQrTokenRevokesThePreviousOneSoOnlyOneActiveTokenExistsPerTable() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Business C");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Branch");
        String tableId =
                TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Table 1");

        MvcResult first = mockMvc.perform(
                        post("/internal/businesses/{businessId}/tables/{tableId}/qr-tokens", businessId, tableId)
                                .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String firstToken =
                objectMapper.readTree(first.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(post("/internal/businesses/{businessId}/tables/{tableId}/qr-tokens", businessId, tableId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // The old (now revoked) token can no longer start a visit...
        mockMvc.perform(post("/api/qr/{token}/visit", firstToken)).andExpect(status().isNotFound());

        // ...but it must still exist in the DB (revoked, not deleted - Section 5).
        assertThat(qrTokenRepository.findByToken(firstToken))
                .isPresent()
                .get()
                .extracting(TableQrToken::getStatus)
                .isEqualTo(QrTokenStatus.REVOKED);
    }

    @Test
    void explicitRevokeMakesTheTokenUnusableForCheckIn() throws Exception {
        TenantFixtures.TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Revoke Case");

        MvcResult activeGet = mockMvc.perform(get(
                        "/internal/businesses/{businessId}/tables/{tableId}/qr-tokens/active",
                        fixture.businessId(),
                        fixture.tableId())
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andReturn();
        String qrTokenId =
                objectMapper.readTree(activeGet.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post(
                        "/internal/businesses/{businessId}/qr-tokens/{qrTokenId}/revoke",
                        fixture.businessId(),
                        qrTokenId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken())).andExpect(status().isNotFound());
    }

    @Test
    void databaseRejectsTwoActiveQrTokensForTheSameTableEvenWhenTheServiceLayerIsBypassed() {
        Business business = businessRepository.save(new Business("Direct DB Business"));
        Branch branch = branchRepository.save(new Branch(business.getId(), "Branch", true, null, DeliveryModel.WAITER_DELIVERY));
        RestaurantTable table = tableRepository.save(new RestaurantTable(business.getId(), branch.getId(), "Table 1"));

        qrTokenRepository.saveAndFlush(new TableQrToken(business.getId(), table.getId(), "direct-db-token-one"));

        assertThatThrownBy(() -> qrTokenRepository.saveAndFlush(
                        new TableQrToken(business.getId(), table.getId(), "direct-db-token-two")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
