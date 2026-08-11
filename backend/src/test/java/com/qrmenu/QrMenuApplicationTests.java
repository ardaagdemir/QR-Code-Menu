package com.qrmenu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full Spring context against a real PostgreSQL container (not H2),
 * proving the datasource + Flyway wiring actually works end-to-end.
 */
@SpringBootTest
@Testcontainers
class QrMenuApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // @ServiceConnection already wires spring.datasource.* from the container;
        // kept explicit here in case @ServiceConnection support changes upstream.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoadsAndFlywayMigratesAgainstRealPostgres() {
        // If the Spring context fails to start (bad datasource config, failed
        // Flyway migration, etc.) this test fails during context initialization.
    }
}
