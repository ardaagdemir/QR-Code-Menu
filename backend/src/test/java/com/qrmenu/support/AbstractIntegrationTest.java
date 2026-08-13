package com.qrmenu.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers Postgres instance for every Milestone 2 integration test class
 * (singleton-container pattern: started once in a static initializer, not managed via
 * @Container/@Testcontainers, so it's reused - and Flyway/context-init only runs once -
 * across all subclasses in the same test run instead of once per class).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "internal.admin.token=" + AbstractIntegrationTest.TEST_ADMIN_TOKEN,
            "payment.mock.webhook-secret=" + AbstractIntegrationTest.TEST_PAYMENT_WEBHOOK_SECRET
        })
public abstract class AbstractIntegrationTest {

    public static final String TEST_ADMIN_TOKEN = "test-internal-admin-token";
    public static final String TEST_PAYMENT_WEBHOOK_SECRET = "test-payment-mock-webhook-secret";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("media.storage.local.base-dir", AbstractIntegrationTest::createTempMediaDir);
    }

    /** Gap-analysis #15: keeps LocalFileMediaStorageAdapter writes out of the repo working directory during tests. */
    private static String createTempMediaDir() {
        try {
            return Files.createTempDirectory("qrmenu-test-media").toAbsolutePath().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
