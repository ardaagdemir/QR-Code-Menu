package com.qrmenu.common.web;

import com.qrmenu.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorrelationIdFilterIntegrationTest extends AbstractIntegrationTest {

    @Test
    void propagatesSafeIncomingCorrelationIdOnEveryResponse() throws Exception {
        String suppliedId = "staff-web.request_123:retry-1";

        mockMvc.perform(get("/actuator/health").header(CorrelationIdFilter.HEADER_NAME, suppliedId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, suppliedId));

        mockMvc.perform(get("/internal/anything").header(CorrelationIdFilter.HEADER_NAME, suppliedId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, suppliedId));
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissingOrUnsafe() throws Exception {
        String generatedId = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(generatedId).isNotBlank();
        assertThatCode(() -> UUID.fromString(generatedId)).doesNotThrowAnyException();

        String unsafeId = "unsafe id with spaces";
        String replacementId = mockMvc.perform(
                        get("/actuator/health").header(CorrelationIdFilter.HEADER_NAME, unsafeId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(replacementId).isNotEqualTo(unsafeId);
        assertThatCode(() -> UUID.fromString(replacementId)).doesNotThrowAnyException();
    }

    @Test
    void corsAllowsClientsToSendAndReadCorrelationId() throws Exception {
        mockMvc.perform(options("/api/staff/auth/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3002")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, CorrelationIdFilter.HEADER_NAME))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("X-Correlation-Id")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("X-Correlation-Id")))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }
}
