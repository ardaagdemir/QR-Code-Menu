package com.qrmenu.customersession;

import com.fasterxml.jackson.databind.JsonNode;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.support.TenantFixtures.CheckedInVisit;
import com.qrmenu.support.TenantFixtures.TableFixture;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #17 (product-requirements.md Section 13.3): PATCH /api/table-visits/
 * {tableVisitId}/guest-count - the opt-in, skippable, editable real-headcount field.
 * Never defaults an unset value to 1: a fresh check-in must come back with guestCount
 * null, and only an explicit PATCH ever changes it.
 */
class TableVisitGuestCountFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String SESSION_COOKIE = "qrmenu_session";

    @Test
    void freshCheckInHasNoGuestCountUntilExplicitlySet() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Guest Count Fresh");

        mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").doesNotExist());
    }

    @Test
    void settingAValidGuestCountPersistsAndIsReturnedOnTheNextCheckIn() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Guest Count Set");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());

        mockMvc.perform(withCookie(patch("/api/table-visits/{tableVisitId}/guest-count", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(3));

        // Same session/table continues the same visit (Section 5) - the recorded headcount sticks.
        MvcResult secondCheckIn = mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken())
                        .cookie(new MockCookie(SESSION_COOKIE, visit.sessionCookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(secondCheckIn.getResponse().getContentAsString());
        assertThat(body.get("guestCount").asInt()).isEqualTo(3);
    }

    @Test
    void guestCountCanBeChangedAndCleared() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Guest Count Change");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());

        mockMvc.perform(withCookie(patch("/api/table-visits/{tableVisitId}/guest-count", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(2));

        mockMvc.perform(withCookie(patch("/api/table-visits/{tableVisitId}/guest-count", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").value(5));

        mockMvc.perform(withCookie(patch("/api/table-visits/{tableVisitId}/guest-count", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCount").doesNotExist());
    }

    @Test
    void zeroOrNegativeGuestCountIsRejected() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Guest Count Invalid");
        CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());

        mockMvc.perform(withCookie(patch("/api/table-visits/{tableVisitId}/guest-count", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(withCookie(patch("/api/table-visits/{tableVisitId}/guest-count", visit.tableVisitId()), visit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherSessionCannotSetGuestCountForSomeoneElsesVisit() throws Exception {
        TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Guest Count Ownership");
        CheckedInVisit ownerVisit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());

        mockMvc.perform(patch("/api/table-visits/{tableVisitId}/guest-count", ownerVisit.tableVisitId())
                        .cookie(new MockCookie(SESSION_COOKIE, "00000000-0000-0000-0000-000000000000"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":4}"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCookie(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, CheckedInVisit visit) {
        return request.cookie(new MockCookie(SESSION_COOKIE, visit.sessionCookieValue()));
    }
}
