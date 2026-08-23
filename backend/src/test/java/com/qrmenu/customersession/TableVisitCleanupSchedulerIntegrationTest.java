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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Gap-Analysis #13: the scheduled job that closes a TableVisit once
 * CustomerSessionService.INACTIVITY_TIMEOUT has elapsed, and that a closed visit can no
 * longer be acted on (getOwnedTableVisit surfaces it as 404, exercised here via the cart endpoint).
 * Backdates last_activity_at directly via SQL (no production code ages a visit that fast)
 * and invokes the scheduled method directly rather than waiting for its real trigger. Not
 * @Transactional at the test-method level: TableVisitCleanupCloser uses REQUIRES_NEW per
 * visit so each one commits/rolls back for real - a shared, still-open test transaction
 * would hide that from these assertions and would also make its own uncommitted fixture
 * rows invisible to that suspended REQUIRES_NEW transaction (see PaymentTimeoutScheduler-
 * IntegrationTest's class javadoc for the full reasoning).
 */
class TableVisitCleanupSchedulerIntegrationTest extends AbstractIntegrationTest {

    private static final String SESSION_COOKIE = "qrmenu_session";

    @Autowired
    private TableVisitCleanupScheduler scheduler;

    @Autowired
    private TableVisitCleanupCloser tableVisitCleanupCloser;

    @Autowired
    private TableVisitRepository tableVisitRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void staleVisitsAreClosedButFreshOnesAreLeftAlone() throws Exception {
        TableFixture staleFixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Stale Visit");
        String staleVisitId = checkInAndGetVisitId(staleFixture.qrToken());
        backdateLastActivity(staleVisitId, Instant.now().minus(CustomerSessionService.INACTIVITY_TIMEOUT).minusSeconds(60));

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

        backdateLastActivity(visitId, Instant.now().minus(CustomerSessionService.INACTIVITY_TIMEOUT).minusSeconds(60));
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

    /**
     * Production-readiness: TableVisitCleanupCloser gives each visit its own transaction
     * (a separate bean, not a `this.` call - see PaymentTimeoutExpirer for why). A vanished
     * visit id failing here must not affect a different, real visit closed right after in
     * the same poll cycle - mirrors the try/catch loop in TableVisitCleanupScheduler.
     */
    @Test
    void aVanishedVisitFailingToCloseDoesNotAffectAnotherVisitClosedRightAfter() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Isolation Visit");
        String visitId = checkInAndGetVisitId(fixture.qrToken());
        backdateLastActivity(visitId, Instant.now().minus(CustomerSessionService.INACTIVITY_TIMEOUT).minusSeconds(60));

        UUID vanishedVisitId = UUID.randomUUID();
        assertThatThrownBy(() -> tableVisitCleanupCloser.closeVisit(vanishedVisitId))
                .isInstanceOf(com.qrmenu.common.web.ResourceNotFoundException.class);

        tableVisitCleanupCloser.closeVisit(UUID.fromString(visitId));

        assertThat(tableVisitRepository.findById(UUID.fromString(visitId)))
                .isPresent()
                .get()
                .extracting(TableVisit::isClosed)
                .isEqualTo(true);
    }

    private String checkInAndGetVisitId(String qrToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/qr/{token}/visit", qrToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("tableVisitId").asText();
    }

    private void backdateLastActivity(String tableVisitId, Instant backdatedTo) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager
                    .createNativeQuery("UPDATE table_visit SET last_activity_at = ?1 WHERE id = ?2")
                    .setParameter(1, Timestamp.from(backdatedTo))
                    .setParameter(2, UUID.fromString(tableVisitId))
                    .executeUpdate();
            entityManager.clear();
        });
    }
}
