package com.qrmenu.tenant;

import com.qrmenu.customersession.TableVisit;
import com.qrmenu.customersession.repository.TableVisitRepository;
import com.qrmenu.ordering.CustomerOrder;
import com.qrmenu.ordering.repository.OrderRepository;
import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.tenant.repository.RestaurantTableRepository;
import com.qrmenu.tenant.repository.TableQrTokenRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Masa yaşam döngüsü: hard-delete yalnızca hiç TableVisit geçmişi olmayan masa için;
 * geçmişi olan masa archive/deactivate edilir; aktif TableVisit veya aktif sipariş
 * varsa archive 409 ile reddedilir; cross-branch guard her üç aksiyon için de geçerli.
 */
class TableLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Autowired
    private TableQrTokenRepository qrTokenRepository;

    @Autowired
    private TableVisitRepository tableVisitRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void neverVisitedTableIsHardDeletedAndItsQrTokenIsCascadeRemoved() throws Exception {
        TenantFixtures.TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Never Visited");
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(),
                "delete-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(delete("/api/staff/tables/{tableId}", fixture.tableId()).cookie(cookie))
                .andExpect(status().isNoContent());

        assertThat(tableRepository.findById(UUID.fromString(fixture.tableId()))).isEmpty();
        assertThat(qrTokenRepository.findByToken(fixture.qrToken())).isEmpty();
    }

    @Test
    void tableWithVisitHistoryCannotBeHardDeletedAndHistorySurvives() throws Exception {
        TenantFixtures.TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Has History");
        TenantFixtures.CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(),
                "delete-blocked-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(delete("/api/staff/tables/{tableId}", fixture.tableId()).cookie(cookie))
                .andExpect(status().isConflict());

        assertThat(tableRepository.findById(UUID.fromString(fixture.tableId()))).isPresent();
        assertThat(tableVisitRepository.findById(UUID.fromString(visit.tableVisitId()))).isPresent();
    }

    @Test
    void archivingATableWithAnActiveVisitIsRejectedAndNothingIsTouched() throws Exception {
        TenantFixtures.TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Active Visit");
        TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(),
                "archive-visit-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(post("/api/staff/tables/{tableId}/archive", fixture.tableId()).cookie(cookie))
                .andExpect(status().isConflict());

        RestaurantTable table = tableRepository.findById(UUID.fromString(fixture.tableId())).orElseThrow();
        assertThat(table.isActive()).isTrue();
        assertThat(qrTokenRepository.findByToken(fixture.qrToken())).isPresent().get()
                .extracting(TableQrToken::getStatus).isEqualTo(QrTokenStatus.ACTIVE);
    }

    @Test
    void archivingATableWithAnActiveOrderIsRejectedEvenWhenTheVisitItselfIsAlreadyClosed() throws Exception {
        TenantFixtures.TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Active Order");
        TenantFixtures.CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());

        CustomerOrder order = new CustomerOrder(
                UUID.fromString(fixture.businessId()), UUID.fromString(fixture.branchId()),
                UUID.fromString(visit.tableVisitId()), "test-tracking-token-hash-active-order");
        order.markAwaitingPayment();
        order.markAwaitingStoreAcceptance();
        orderRepository.save(order);

        // Visit itself already closed (e.g. absolute-lifetime scheduler) - the order is
        // still operationally active, and that alone must block the archive.
        TableVisit tableVisit = tableVisitRepository.findById(UUID.fromString(visit.tableVisitId())).orElseThrow();
        tableVisit.close();
        tableVisitRepository.save(tableVisit);

        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(),
                "archive-order-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(post("/api/staff/tables/{tableId}/archive", fixture.tableId()).cookie(cookie))
                .andExpect(status().isConflict());

        assertThat(tableRepository.findById(UUID.fromString(fixture.tableId())).orElseThrow().isActive()).isTrue();
    }

    @Test
    void archivingATableWithOnlyClosedHistoryRevokesTheActiveQrAndKeepsTheVisitRecord() throws Exception {
        TenantFixtures.TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Archivable");
        TenantFixtures.CheckedInVisit visit = TenantFixtures.checkIn(mockMvc, objectMapper, fixture.qrToken());
        TableVisit tableVisit = tableVisitRepository.findById(UUID.fromString(visit.tableVisitId())).orElseThrow();
        tableVisit.close();
        tableVisitRepository.save(tableVisit);
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(),
                "archive-ok-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(post("/api/staff/tables/{tableId}/archive", fixture.tableId()).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(qrTokenRepository.findByToken(fixture.qrToken())).isPresent().get()
                .extracting(TableQrToken::getStatus).isEqualTo(QrTokenStatus.REVOKED);
        assertThat(tableVisitRepository.findById(UUID.fromString(visit.tableVisitId()))).isPresent();

        // Archived table accepts no new QR/check-in.
        mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken())).andExpect(status().isNotFound());

        // Reactivate: table usable again, but no QR is auto-issued.
        mockMvc.perform(post("/api/staff/tables/{tableId}/reactivate", fixture.tableId()).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/staff/tables/{tableId}/qr-tokens/active", fixture.tableId()).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void tableLifecycleActionsAreRejectedAcrossBranchesEvenWithARealTableId() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "lifecycle-cross-branch");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "A Şube");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "B Şube");
        String tableB = TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, branchB, "B Masa");
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA,
                "lifecycle-cross-branch-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(delete("/api/staff/tables/{tableId}", tableB).cookie(cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/tables/{tableId}/archive", tableB).cookie(cookie))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/staff/tables/{tableId}/reactivate", tableB).cookie(cookie))
                .andExpect(status().isNotFound());

        assertThat(tableRepository.findById(UUID.fromString(tableB))).isPresent();
    }

    /**
     * TenantService.checkIn vs OrderingService.archiveTable: check-in locks the table
     * row PESSIMISTIC_READ (findByIdForShare) and archive locks it PESSIMISTIC_WRITE
     * (getTableForUpdate), so the two always serialize instead of racing - see both
     * methods' Javadoc. Whichever acquires the lock first decides the outcome; both
     * outcomes are asserted here since a fixed-thread-pool CountDownLatch race doesn't
     * control which side wins.
     */
    @Test
    void concurrentCheckInAndArchiveNeverBothSucceedAndNeverLeaveAVisitOnAnArchivedTable() throws Exception {
        TenantFixtures.TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Race Archive");
        MockCookie staffCookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(),
                "race-archive-admin@example.com", "BUSINESS_ADMIN"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Integer> checkInFuture = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            Future<Integer> archiveFuture = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/staff/tables/{tableId}/archive", fixture.tableId()).cookie(staffCookie))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            startLatch.countDown();
            int checkInStatus = checkInFuture.get(15, TimeUnit.SECONDS);
            int archiveStatus = archiveFuture.get(15, TimeUnit.SECONDS);

            UUID tableId = UUID.fromString(fixture.tableId());
            if (archiveStatus == 200) {
                // Archive won the lock first - the table is gone from service, so the
                // racing check-in must fail cleanly and never create a TableVisit for it.
                assertThat(checkInStatus).isEqualTo(404);
                assertThat(tableVisitRepository.existsByTableId(tableId)).isFalse();
                assertThat(tableRepository.findById(tableId).orElseThrow().isActive()).isFalse();
            } else {
                // Check-in won the lock first and created a visit - archive must then see
                // it as active and refuse, never silently archive out from under it.
                assertThat(archiveStatus).isEqualTo(409);
                assertThat(checkInStatus).isEqualTo(200);
                assertThat(tableVisitRepository.existsByTableId(tableId)).isTrue();
                assertThat(tableRepository.findById(tableId).orElseThrow().isActive()).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * TenantService.checkIn vs TenantService.deleteTable: check-in locks the table row
     * PESSIMISTIC_READ (findByIdForShare) and delete locks it PESSIMISTIC_WRITE (also
     * getTableForUpdate, same as archive), so the two always serialize instead of racing
     * into the table_visit -> restaurant_table FK constraint - see both methods'
     * Javadoc. Both possible winners are asserted, same reasoning as the archive race
     * above.
     */
    @Test
    void concurrentCheckInAndHardDeleteNeverBothSucceedAndNeverThrowAForeignKeyViolation() throws Exception {
        TenantFixtures.TableFixture fixture =
                TenantFixtures.createTableWithActiveQrToken(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Race Delete");
        MockCookie staffCookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(),
                "race-delete-admin@example.com", "BUSINESS_ADMIN"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Integer> checkInFuture = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/qr/{token}/visit", fixture.qrToken()))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            Future<Integer> deleteFuture = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(delete("/api/staff/tables/{tableId}", fixture.tableId()).cookie(staffCookie))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
            startLatch.countDown();
            int checkInStatus = checkInFuture.get(15, TimeUnit.SECONDS);
            int deleteStatus = deleteFuture.get(15, TimeUnit.SECONDS);

            UUID tableId = UUID.fromString(fixture.tableId());
            if (deleteStatus == 204) {
                // Hard delete won the lock first - the table row is gone, so the racing
                // check-in must fail cleanly, never insert a TableVisit that would
                // violate the table_visit -> restaurant_table FK.
                assertThat(checkInStatus).isEqualTo(404);
                assertThat(tableRepository.findById(tableId)).isEmpty();
            } else {
                // Check-in won the lock first and created visit history - hard delete
                // must then see it and refuse (archive instead), never hit the FK.
                assertThat(deleteStatus).isEqualTo(409);
                assertThat(checkInStatus).isEqualTo(200);
                assertThat(tableRepository.findById(tableId)).isPresent();
                assertThat(tableVisitRepository.existsByTableId(tableId)).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static MockCookie cookie(String value) {
        return new MockCookie(StaffCookieSupport.COOKIE_NAME, value);
    }
}
