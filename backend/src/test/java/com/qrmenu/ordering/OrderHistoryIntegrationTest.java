package com.qrmenu.ordering;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sipariş görünürlüğü audit: (1) regression coverage for the order-number lookup
 * collision bug (orderNumber resets to 1 per branch per day - OrderNumberGenerator -
 * so two orders can legitimately share a number once a branch has been open more than
 * a day; the old single-result derived query threw IncorrectResultSizeDataAccessException
 * on the second row instead of resolving the ambiguity), and (2) the new /history
 * endpoint that keeps COMPLETED/REJECTED_BY_STORE orders reachable after they drop off
 * the Kasa board.
 */
class OrderHistoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String lastTrackingToken;

    @Test
    void searchByOrderNumberResolvesToTheMostRecentOrderWhenTheDailyCounterHasRecycled() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Collision Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "collision-admin@example.com");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);
        CheckedInVisit visit = payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Çay", 1000, 1);

        JsonNode todaysOrder = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/pending-acceptance", adminCookie))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0);
        String todaysOrderId = todaysOrder.get("orderId").asText();
        int orderNumber = todaysOrder.get("orderNumber").asInt();

        // Simulate a colliding order from a previous day: same branch, same recycled
        // order_number, but an earlier created_at - the exact scenario OrderNumberGenerator's
        // per-branch-per-day counter allows and the old findByBranchIdAndOrderNumber
        // (a single-result derived query) could not handle without throwing.
        jdbcTemplate.update(
                "INSERT INTO customer_order (id, business_id, branch_id, table_visit_id, status, "
                        + "tracking_token_hash, total_minor_units, order_number, created_at, last_activity_at) "
                        + "SELECT gen_random_uuid(), business_id, branch_id, table_visit_id, status, "
                        + "md5(tracking_token_hash || 'stale'), total_minor_units, order_number, "
                        + "created_at - INTERVAL '1 day', last_activity_at - INTERVAL '1 day' "
                        + "FROM customer_order WHERE id = ?::uuid",
                todaysOrderId);

        mockMvc.perform(staffGet(branchId, "/search?orderNumber=" + orderNumber, adminCookie))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.orderId").value(todaysOrderId));
    }

    @Test
    void historyEndpointListsCompletedAndRejectedOrdersWithRefundStatusButNotActiveOnes() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "History Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "history-admin@example.com");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);

        // Order A: goes all the way to COMPLETED.
        CheckedInVisit visitA = payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Pizza", 10000, 1);
        String orderIdA = pendingOrderId(branchId, adminCookie);
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/accept", branchId, orderIdA).cookie(adminCookie(adminCookie)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/ready", branchId, orderIdA).cookie(adminCookie(adminCookie)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/complete", branchId, orderIdA).cookie(adminCookie(adminCookie)))
                .andExpect(status().isOk());

        // Order B: rejected by the store, which also triggers an automatic full refund.
        CheckedInVisit visitB = payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Simit", 2000, 1);
        String orderIdB = pendingOrderId(branchId, adminCookie);
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderIdB)
                        .cookie(adminCookie(adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isOk());

        // Order C: stays active (AWAITING_STORE_ACCEPTANCE) - must not show up in history.
        payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Su", 500, 1);

        // The branch has no configured timezone, so getOrderHistory resolves its
        // calendar-day range via TenantService.resolveBranchTimeZone, which falls back
        // to the business's defaultTimeZone ("Europe/Istanbul" - Business's single-arg
        // constructor). Computing "today" the same way (rather than the JVM/server's
        // default zone, which may not be Europe/Istanbul at all) keeps this assertion
        // correct regardless of where the test happens to run - this is the exact bug
        // that used to make this test flaky for about an hour after local midnight.
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"));
        JsonNode history = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/history?from=" + today + "&to=" + today, adminCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(history).hasSize(2);

        JsonNode completedRow = findByOrderId(history, orderIdA);
        assertThat(completedRow.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completedRow.get("tableLabel").asText()).isEqualTo("Masa 1");
        assertThat(completedRow.get("items").get(0).get("productName").asText()).isEqualTo("Pizza");
        assertThat(completedRow.get("latestRefundStatus").isNull()).isTrue();

        JsonNode rejectedRow = findByOrderId(history, orderIdB);
        assertThat(rejectedRow.get("status").asText()).isEqualTo("REJECTED_BY_STORE");
        assertThat(rejectedRow.get("rejectionReasonCode").asText()).isEqualTo("OUT_OF_STOCK");
        assertThat(rejectedRow.get("latestRefundStatus").asText()).isEqualTo("COMPLETED");

        // status filter narrows to just the rejected/refunded one.
        JsonNode rejectedOnly = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/history?status=REJECTED_BY_STORE&from=" + today + "&to=" + today, adminCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(rejectedOnly).hasSize(1);
        assertThat(rejectedOnly.get(0).get("orderId").asText()).isEqualTo(orderIdB);

        // A date range that excludes today returns nothing.
        LocalDate farPast = today.minusDays(30);
        JsonNode empty = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/history?from=" + farPast + "&to=" + farPast, adminCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(empty).isEmpty();
    }

    /**
     * Root-cause regression for the original flake: an order created at 01:30
     * Europe/Istanbul time is 22:30 the PREVIOUS calendar day in UTC (Turkey has had no
     * DST since 2016, so the +03:00 offset is fixed year-round). Querying
     * /history?from=to=<Istanbul's today> must still find it - if getOrderHistory ever
     * regressed back to resolving the branch's calendar day in UTC instead of via
     * TenantService.resolveBranchTimeZone (branch has no explicit timezone here, so
     * this exercises the Business.defaultTimeZone "Europe/Istanbul" fallback), the
     * order's UTC-previous-day created_at would fall outside a UTC-computed window and
     * silently disappear from "today"'s history. Anchored to a fixed offset from the
     * real "today" rather than a hardcoded date, so this passes deterministically no
     * matter when the suite actually runs - it doesn't rely on catching the bug during
     * the ~1-hour daily window the original flake only failed in.
     */
    @Test
    void historyIncludesAnOrderCreatedJustAfterEuropeIstanbulMidnightEvenThoughItsStillYesterdayInUtc() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Midnight Boundary Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String adminCookie = StaffFixtures.bootstrapBusinessAdminAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchId, "midnight-admin@example.com");
        String tableId = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, "Masa 1");
        String qrToken = TenantFixtures.createQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, tableId);

        CheckedInVisit visit = payAndAwaitStoreAcceptance(businessId, branchId, qrToken, "Gece Yarısı Ürünü", 1000, 1);
        String orderId = pendingOrderId(branchId, adminCookie);
        mockMvc.perform(post("/api/staff/branches/{branchId}/orders/{orderId}/reject", branchId, orderId)
                        .cookie(adminCookie(adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
                .andExpect(status().isOk());

        ZoneId istanbul = ZoneId.of("Europe/Istanbul");
        LocalDate istanbulToday = LocalDate.now(istanbul);
        Instant justAfterIstanbulMidnight = istanbulToday.atTime(1, 30).atZone(istanbul).toInstant();
        jdbcTemplate.update(
                "UPDATE customer_order SET created_at = ? WHERE id = ?",
                Timestamp.from(justAfterIstanbulMidnight), java.util.UUID.fromString(orderId));

        JsonNode history = objectMapper.readTree(mockMvc.perform(
                        staffGet(branchId, "/history?from=" + istanbulToday + "&to=" + istanbulToday, adminCookie))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("orderId").asText()).isEqualTo(orderId);
    }

    private JsonNode findByOrderId(JsonNode array, String orderId) {
        for (JsonNode node : array) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node;
            }
        }
        throw new AssertionError("Order not found in history response: " + orderId);
    }

    private String pendingOrderId(String branchId, String staffCookie) throws Exception {
        JsonNode pending = objectMapper.readTree(mockMvc.perform(staffGet(branchId, "/pending-acceptance", staffCookie))
                .andReturn()
                .getResponse()
                .getContentAsString());
        return pending.get(pending.size() - 1).get("orderId").asText();
    }

    private CheckedInVisit payAndAwaitStoreAcceptance(
            String businessId, String branchId, String qrToken, String productName, long priceMinorUnits, int quantity) throws Exception {
        String categoryId = TenantFixtures.createMenuCategory(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Kategori");
        String productId = TenantFixtures.createProduct(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, categoryId, productName, priceMinorUnits, 10);
        TenantFixtures.upsertBranchProduct(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchId, productId, "AVAILABLE", null);
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, qrToken);

        MvcResult cartResult = mockMvc.perform(withCookie(post("/api/table-visits/{tableVisitId}/cart/items", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        lastTrackingToken = objectMapper.readTree(cartResult.getResponse().getContentAsString()).get("orderTrackingToken").asText();

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

    private MockHttpServletRequestBuilder staffGet(String branchId, String path, String staffCookie) {
        return get("/api/staff/branches/{branchId}/orders" + path, branchId).cookie(adminCookie(staffCookie));
    }

    private MockCookie adminCookie(String staffCookie) {
        return new MockCookie(StaffCookieSupport.COOKIE_NAME, staffCookie);
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie("qrmenu_session", visit.sessionCookieValue()));
    }
}
