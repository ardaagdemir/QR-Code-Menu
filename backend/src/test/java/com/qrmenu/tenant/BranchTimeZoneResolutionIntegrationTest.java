package com.qrmenu.tenant;

import com.qrmenu.support.AbstractIntegrationTest;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Root-cause regression coverage for the day-boundary bug class that originally
 * surfaced as an OrderHistoryIntegrationTest flake: order history, reports, daily
 * close, and the ordering-hours gate all used to fall back to a bare
 * {@code ZoneOffset.UTC} whenever a Branch had no explicit timezone, silently ignoring
 * the Business.defaultTimeZone fallback Branch's own Javadoc documents (Section
 * 12.1/12.2). Every one of those call sites now goes through
 * TenantService.resolveBranchTimeZone instead of inventing its own fallback - these
 * tests pin down that one function's contract directly and deterministically
 * (independent of wall-clock timing), rather than re-deriving it in every consumer.
 */
class BranchTimeZoneResolutionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Test
    void aBranchWithNoExplicitTimezoneFallsBackToItsBusinessDefaultTimeZoneNotUtc() {
        Business business = tenantService.createBusiness("Zone Fallback Business");
        tenantService.updateBusinessSettings(business.getId(), "TRY", "Asia/Tokyo", null);
        Branch branch = tenantService.createBranch(business.getId(), "Şube", true, null, DeliveryModel.WAITER_DELIVERY);

        assertThat(branch.getTimezone()).isNull();
        assertThat(tenantService.resolveBranchTimeZone(branch)).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void aBranchsOwnExplicitTimezoneWinsOverItsBusinessDefault() {
        Business business = tenantService.createBusiness("Zone Override Business");
        tenantService.updateBusinessSettings(business.getId(), "TRY", "Asia/Tokyo", null);
        Branch branch = tenantService.createBranch(business.getId(), "Şube", true, null, DeliveryModel.WAITER_DELIVERY);
        tenantService.setBranchTimezone(business.getId(), branch.getId(), "Pacific/Kiritimati", null);

        Branch reloaded = tenantService.getBranch(business.getId(), branch.getId());
        assertThat(tenantService.resolveBranchTimeZone(reloaded)).isEqualTo(ZoneId.of("Pacific/Kiritimati"));
    }

    @Test
    void aNewBusinessWithNoExplicitSettingsDefaultsToEuropeIstanbul() {
        // Every fixture in this test suite (TenantFixtures.createBusiness) relies on
        // this exact default - it's the reason the rest of the suite can compute
        // "today" as Europe/Istanbul without configuring it explicitly per test.
        Business business = tenantService.createBusiness("Default Zone Business");
        Branch branch = tenantService.createBranch(business.getId(), "Şube", true, null, DeliveryModel.WAITER_DELIVERY);

        assertThat(business.getDefaultTimeZone()).isEqualTo("Europe/Istanbul");
        assertThat(tenantService.resolveBranchTimeZone(branch)).isEqualTo(ZoneId.of("Europe/Istanbul"));
    }
}
