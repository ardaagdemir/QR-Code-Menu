package com.qrmenu.customersession;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.TableFixture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the public POST /api/qr/{token}/visit flow: resolving an active QR token,
 * issuing/continuing an AnonymousCustomerSession, and starting/continuing a TableVisit
 * (docs/product-requirements.md Section 5).
 */
class QrCheckInFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String SESSION_COOKIE = "qrmenu_session";

    @Test
    void validActiveTokenStartsAVisitAndReturnsBusinessBranchTableInfo() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Checkin Happy Path");

        mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableVisitId").exists())
                .andExpect(jsonPath("$.businessName").value("Fixture Business Checkin Happy Path"))
                .andExpect(jsonPath("$.branchName").value("Fixture Branch Checkin Happy Path"))
                .andExpect(jsonPath("$.tableLabel").value("Checkin Happy Path"))
                .andExpect(cookie().exists(SESSION_COOKIE))
                .andExpect(cookie().httpOnly(SESSION_COOKIE, true));
    }

    @Test
    void unknownQrTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/qr/{token}/visit", "does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void checkingInWithoutASessionCookieIssuesANewSessionCookie() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "No Cookie Yet");

        MvcResult result = mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull().contains(SESSION_COOKIE + "=");
    }

    @Test
    void secondCheckInWithTheSameSessionAndTableContinuesTheExistingVisit() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Continuation Case");

        MvcResult firstResult = mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                .andExpect(status().isOk())
                .andReturn();
        String sessionCookieValue = extractCookieValue(firstResult, SESSION_COOKIE);
        String firstVisitId = readJson(firstResult).get("tableVisitId").asText();

        MvcResult secondResult = mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken())
                        .cookie(new MockCookie(SESSION_COOKIE, sessionCookieValue)))
                .andExpect(status().isOk())
                .andReturn();
        String secondVisitId = readJson(secondResult).get("tableVisitId").asText();

        assertThat(secondVisitId).isEqualTo(firstVisitId);
    }

    @Test
    void checkInWithADifferentSessionCookieStartsASeparateVisitForTheSameTable() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Two Customers");

        MvcResult firstCustomer = mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult secondCustomer = mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                .andExpect(status().isOk())
                .andReturn();

        String firstVisitId = readJson(firstCustomer).get("tableVisitId").asText();
        String secondVisitId = readJson(secondCustomer).get("tableVisitId").asText();

        assertThat(secondVisitId).isNotEqualTo(firstVisitId);
    }

    @Test
    void aStaleOrUnknownSessionCookieIsIgnoredRatherThanRejected() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Stale Cookie");

        mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken())
                        .cookie(new MockCookie(SESSION_COOKIE, "00000000-0000-0000-0000-000000000000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableVisitId").exists());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String extractCookieValue(MvcResult result, String cookieName) {
        var cookie = result.getResponse().getCookie(cookieName);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}
