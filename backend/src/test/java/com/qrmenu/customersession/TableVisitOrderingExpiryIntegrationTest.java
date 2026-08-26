package com.qrmenu.customersession;

import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Customer TableVisit security hardening: order creation must be gated by a synchronous,
 * server-side check of the visit's own timestamps (CustomerSessionService.
 * getActiveTableVisitForOrdering) rather than the async closedAt flag the cleanup
 * scheduler sets up to 15 minutes late (see TableVisitCleanupSchedulerIntegrationTest for
 * that already-closed case, which still surfaces as 404). This test drives the same gate
 * through both of its independent clocks - INACTIVITY_TIMEOUT and ABSOLUTE_LIFETIME -
 * while the visit itself is never closed by the scheduler, so a 410 here specifically
 * proves the synchronous check, not the scheduler.
 *
 * Not @Transactional at the test-method level: the SQL backdating below must be visible
 * to the request thread's own separate transaction (same reasoning as
 * TableVisitCleanupSchedulerIntegrationTest).
 */
class TableVisitOrderingExpiryIntegrationTest extends AbstractIntegrationTest {

    private static final String SESSION_COOKIE = "qrmenu_session";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void anActiveVisitCanAddToCart() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Active Visit Orders");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        String productId = createOrderableProduct(fixture, "Active Visit Product");

        addItem(visit, productId).andExpect(status().isCreated());
    }

    @Test
    void inactivityExpiredVisitIsRejectedEvenThoughItWasNeverClosedByTheScheduler() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Inactivity Expired");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        String productId = createOrderableProduct(fixture, "Inactivity Expired Product");

        backdateLastActivity(visit.tableVisitId(), Instant.now().minus(CustomerSessionService.INACTIVITY_TIMEOUT).minusSeconds(60));

        addItem(visit, productId).andExpect(status().isGone());
        assertThat(tableVisitIsClosed(visit.tableVisitId())).isFalse();
    }

    @Test
    void absoluteLifetimeExpiredVisitIsRejectedEvenIfItWasTouchedRecently() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Absolute Lifetime Expired");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        String productId = createOrderableProduct(fixture, "Absolute Lifetime Expired Product");

        // lastActivityAt stays fresh (default from check-in, seconds ago) - only
        // startedAt is pushed past ABSOLUTE_LIFETIME, proving the two clocks are
        // independent: staying "active" does not extend a visit past its hard cap.
        backdateStartedAt(visit.tableVisitId(), Instant.now().minus(CustomerSessionService.ABSOLUTE_LIFETIME).minusSeconds(60));

        addItem(visit, productId).andExpect(status().isGone());
        assertThat(tableVisitIsClosed(visit.tableVisitId())).isFalse();
    }

    @Test
    void rescanningTheQrAfterExpiryStartsAFreshVisitInsteadOfContinuingTheExpiredOne() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Rescan After Expiry");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        backdateLastActivity(visit.tableVisitId(), Instant.now().minus(CustomerSessionService.INACTIVITY_TIMEOUT).minusSeconds(60));

        MvcResult rescanResult = mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken())
                        .cookie(new MockCookie(SESSION_COOKIE, visit.sessionCookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String newVisitId = objectMapper.readTree(rescanResult.getResponse().getContentAsString()).get("tableVisitId").asText();

        assertThat(newVisitId).isNotEqualTo(visit.tableVisitId());

        String newSessionCookie = extractCookieValueOrKeepExisting(rescanResult, visit.sessionCookieValue());
        String productId = createOrderableProduct(fixture, "Rescan After Expiry Product");
        mockMvc.perform(post("/api/table-visits/{tableVisitId}/cart/items", newVisitId)
                        .cookie(new MockCookie(SESSION_COOKIE, newSessionCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void removingACartItemRefreshesLastActivityAtButReadingTheCartDoesNot() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Touch On Mutation");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        String productId = createOrderableProduct(fixture, "Touch On Mutation Product");

        MvcResult addResult = addItem(visit, productId).andExpect(status().isCreated()).andReturn();
        String orderItemId = objectMapper
                .readTree(addResult.getResponse().getContentAsString())
                .get("items")
                .get(0)
                .get("id")
                .asText();

        Instant backdated = Instant.now().minusSeconds(300);
        backdateLastActivity(visit.tableVisitId(), backdated);

        mockMvc.perform(get("/api/table-visits/{tableVisitId}/cart", visit.tableVisitId())
                        .cookie(new MockCookie(SESSION_COOKIE, visit.sessionCookieValue())))
                .andExpect(status().isOk());
        assertThat(lastActivityAt(visit.tableVisitId())).isEqualTo(backdated);

        mockMvc.perform(delete(
                                "/api/table-visits/{tableVisitId}/cart/items/{orderItemId}",
                                visit.tableVisitId(),
                                orderItemId)
                        .cookie(new MockCookie(SESSION_COOKIE, visit.sessionCookieValue())))
                .andExpect(status().isOk());
        assertThat(lastActivityAt(visit.tableVisitId())).isAfter(backdated);
    }

    @Test
    void removingACartItemOnAnInactivityExpiredVisitIsRejected() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Remove After Expiry");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        String productId = createOrderableProduct(fixture, "Remove After Expiry Product");

        MvcResult addResult = addItem(visit, productId).andExpect(status().isCreated()).andReturn();
        String orderItemId = objectMapper
                .readTree(addResult.getResponse().getContentAsString())
                .get("items")
                .get(0)
                .get("id")
                .asText();

        backdateLastActivity(visit.tableVisitId(), Instant.now().minus(CustomerSessionService.INACTIVITY_TIMEOUT).minusSeconds(60));

        mockMvc.perform(delete(
                                "/api/table-visits/{tableVisitId}/cart/items/{orderItemId}",
                                visit.tableVisitId(),
                                orderItemId)
                        .cookie(new MockCookie(SESSION_COOKIE, visit.sessionCookieValue())))
                .andExpect(status().isGone());
    }

    private String extractCookieValueOrKeepExisting(MvcResult result, String fallback) {
        var cookie = result.getResponse().getCookie(SESSION_COOKIE);
        return cookie != null ? cookie.getValue() : fallback;
    }

    private String createOrderableProduct(TableFixture fixture, String name) throws Exception {
        String categoryId =
                TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), "Kategori " + name);
        String productId =
                TenantFixtures.createProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), categoryId, name, 1000);
        TenantFixtures.upsertBranchProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(), productId, "AVAILABLE", null);
        return productId;
    }

    private org.springframework.test.web.servlet.ResultActions addItem(CheckedInVisit visit, String productId) throws Exception {
        return mockMvc.perform(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId())
                .cookie(new MockCookie(SESSION_COOKIE, visit.sessionCookieValue()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"" + productId + "\",\"quantity\":1}"));
    }

    private boolean tableVisitIsClosed(String tableVisitId) {
        Boolean[] closed = new Boolean[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Object result = entityManager
                    .createNativeQuery("SELECT closed_at FROM table_visit WHERE id = ?1")
                    .setParameter(1, UUID.fromString(tableVisitId))
                    .getSingleResult();
            closed[0] = result != null;
        });
        return closed[0];
    }

    private Instant lastActivityAt(String tableVisitId) {
        Instant[] result = new Instant[1];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Object value = entityManager
                    .createNativeQuery("SELECT last_activity_at FROM table_visit WHERE id = ?1")
                    .setParameter(1, UUID.fromString(tableVisitId))
                    .getSingleResult();
            result[0] = value instanceof Timestamp timestamp ? timestamp.toInstant() : (Instant) value;
        });
        return result[0];
    }

    private void backdateLastActivity(String tableVisitId, Instant backdatedTo) {
        runSqlUpdate("UPDATE table_visit SET last_activity_at = ?1 WHERE id = ?2", backdatedTo, tableVisitId);
    }

    private void backdateStartedAt(String tableVisitId, Instant backdatedTo) {
        runSqlUpdate("UPDATE table_visit SET started_at = ?1 WHERE id = ?2", backdatedTo, tableVisitId);
    }

    private void runSqlUpdate(String sql, Instant backdatedTo, String tableVisitId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager
                    .createNativeQuery(sql)
                    .setParameter(1, Timestamp.from(backdatedTo))
                    .setParameter(2, UUID.fromString(tableVisitId))
                    .executeUpdate();
            entityManager.clear();
        });
    }
}
