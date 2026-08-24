package com.qrmenu.tenant;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The real system clock TenantService reads "now" through (see
 * TenantService.isWithinConfiguredBusinessHours) - a separate bean rather than a bare
 * Clock.systemUTC() call inline so integration tests can substitute a Clock.fixed(...)
 * for a deterministic instant (see BranchOvernightCarryoverIntegrationTest) without
 * touching production behavior here.
 */
@Configuration
class TenantClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
