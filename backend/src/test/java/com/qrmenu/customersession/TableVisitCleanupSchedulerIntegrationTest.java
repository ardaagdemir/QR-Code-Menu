package com.qrmenu.customersession;

import com.qrmenu.customersession.repository.TableVisitRepository;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.TableFixture;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Gap-Analysis #13: the scheduled job that closes a TableVisit once
 * CustomerSessionService.VISIT_TTL has elapsed, and that a closed visit can no longer be
 * acted on (getOwnedTableVisit surfaces it as 404, exercised here via the cart endpoint).
 * Backdates last_activity_at directly via SQL (no production code ages a visit that fast)
 * and invokes the scheduled method directly rather than waiting for its real trigger.
 */
class TableVisitCleanupSchedulerIntegrationTest extends AbstractIntegrationTest {

    private static final String SESSION_COOKIE = "qrmenu_session";

    @Autowired
    private TableVisitCleanupScheduler scheduler;

    @Autowired
    private TableVisitRepository tableVisitRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void staleVisitsAreClosedButFreshOnesAreLeftAlone() throws Exception {
        TableFixture staleFixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Stale Visit");
        String staleVisitId = checkInAndGetVisitId(staleFixture.qrToken());
        backdateLastActivity(staleVisitId, Instant.now().minus(CustomerSessionService.VISIT_TTL).minusSeconds(60));

        TableFixture freshFixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Fresh Visit");
        String freshVisitId = checkInAndGetVisitId(freshFixture.qrToken());

        scheduler.closeStaleTableVisits();

        assertThat(tableVisitRepository.findById(UUID.fromString(staleVisitId)))
                .isPresent()
                .get()
                .extracting(TableVisit::isClosed)
                .isEqualTo(true);
        assertThat(tableVisitRepository.findById(UUID.fromString(freshVisitId)))
                .isPresent()
                .get()
                .extracting(TableVisit::isClosed)
                .isEqualTo(false);
    }

    @Test
    @Transactional
    void aClosedVisitCanNoLongerBeUsedToActOnItsCart() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Closed Visit Cart");
        MvcResult checkInResult = mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                .andExpect(status().isOk())
                .andReturn();
        String visitId =
                objectMapper.readTree(checkInResult.getResponse().getContentAsString()).get("tableVisitId").asText();
        String sessionCookieValue =
                checkInResult.getResponse().getCookie(SESSION_COOKIE).getValue();

        backdateLastActivity(visitId, Instant.now().minus(CustomerSessionService.VISIT_TTL).minusSeconds(60));
        scheduler.closeStaleTableVisits();

        String categoryId = TenantFixtures.createMenuCategory(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), categoryId, "Ürün", 1000, 10);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(), productId, "AVAILABLE", null);

        mockMvc.perform(post("/api/table-visits/{tableVisitId}/cart/items", visitId)
                        .cookie(new MockCookie(SESSION_COOKIE, sessionCookieValue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    private String checkInAndGetVisitId(String qrToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/qr/{token}/visit", qrToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("tableVisitId").asText();
    }

    private void backdateLastActivity(String tableVisitId, Instant backdatedTo) {
        entityManager
                .createNativeQuery("UPDATE table_visit SET last_activity_at = ?1 WHERE id = ?2")
                .setParameter(1, Timestamp.from(backdatedTo))
                .setParameter(2, UUID.fromString(tableVisitId))
                .executeUpdate();
        entityManager.clear();
    }
}
