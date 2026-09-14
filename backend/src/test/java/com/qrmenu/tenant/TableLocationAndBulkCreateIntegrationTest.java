package com.qrmenu.tenant;

import com.qrmenu.staffaccess.StaffCookieSupport;
import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import com.qrmenu.tenant.repository.RestaurantTableRepository;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * İç Mekan/Dış Mekan konum + kapasite, Otomatik Oluştur (bulk) numaralandırma/eşzamanlılık,
 * Düzenle aksiyonu ve yeni masaların QR akışı için kapsamlı entegrasyon testleri. Mevcut
 * masa yaşam döngüsü regresyonu TableLifecycleIntegrationTest'te ayrıca doğrulanıyor.
 */
class TableLocationAndBulkCreateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Test
    void manualCreateSupportsIndoorLocationAndOptionalCapacity() throws Exception {
        BranchFixture fixture = createBranchFixture("manual-indoor");
        MockCookie cookie = login(fixture, "manual-indoor-admin@example.com");

        mockMvc.perform(post("/api/staff/tables").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Masa 1\",\"location\":\"INDOOR\",\"capacity\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.location").value("INDOOR"))
                .andExpect(jsonPath("$.capacity").value(4));
    }

    @Test
    void manualCreateSupportsOutdoorLocationWithoutCapacity() throws Exception {
        BranchFixture fixture = createBranchFixture("manual-outdoor");
        MockCookie cookie = login(fixture, "manual-outdoor-admin@example.com");

        mockMvc.perform(post("/api/staff/tables").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Teras 1\",\"location\":\"OUTDOOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.location").value("OUTDOOR"))
                .andExpect(jsonPath("$.capacity").doesNotExist());
    }

    @Test
    void bulkCreateGeneratesSequentialLabelsAndAppliesSharedCapacityToAll() throws Exception {
        BranchFixture fixture = createBranchFixture("bulk-indoor");
        MockCookie cookie = login(fixture, "bulk-indoor-admin@example.com");

        mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"INDOOR\",\"count\":5,\"namePrefix\":\"Masa\",\"capacity\":4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(5)))
                .andExpect(jsonPath("$[0].label").value("Masa 1"))
                .andExpect(jsonPath("$[4].label").value("Masa 5"))
                .andExpect(jsonPath("$[0].capacity").value(4))
                .andExpect(jsonPath("$[4].location").value("INDOOR"));
    }

    @Test
    void bulkCreateDefaultsToMasaPrefixWhenBlank() throws Exception {
        BranchFixture fixture = createBranchFixture("bulk-default-prefix");
        MockCookie cookie = login(fixture, "bulk-default-prefix-admin@example.com");

        mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"OUTDOOR\",\"count\":2,\"namePrefix\":\"  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].label").value("Masa 1"))
                .andExpect(jsonPath("$[1].label").value("Masa 2"));
    }

    @Test
    void bulkCreateContinuesNumberingPastExistingLabelsWithSamePrefix() throws Exception {
        BranchFixture fixture = createBranchFixture("bulk-numbering-gap");
        MockCookie cookie = login(fixture, "bulk-numbering-gap-admin@example.com");
        TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(), "Masa 1");
        TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(), "Masa 3");
        TenantFixtures.createTable(mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(), "Sandalye 9");

        mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"INDOOR\",\"count\":2,\"namePrefix\":\"Masa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].label").value("Masa 4"))
                .andExpect(jsonPath("$[1].label").value("Masa 5"));
    }

    /**
     * Branch isolation: staff-web roles are scoped to exactly one branch (StaffContext.
     * canAccessBranch has no exemption besides PLATFORM_ADMIN - see CrossTenantBranchAccessIntegrationTest
     * for the same pattern on the other table actions). A BUSINESS_ADMIN logged into branchA must be
     * rejected outright when targeting branchB's bulk-create endpoint, and nothing is created there;
     * their own branch's bulk create is unaffected and numbers independently from 1.
     */
    @Test
    void bulkCreateOnAnUnauthorizedBranchIsRejectedAndCreatesNothingThere() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "bulk-isolation-biz");
        String branchA = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube A");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube B");
        MockCookie cookie = cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, businessId, branchA, "bulk-isolation-admin@example.com", "BUSINESS_ADMIN"));

        mockMvc.perform(post("/api/staff/branches/{branchId}/tables/bulk", branchB).cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"OUTDOOR\",\"count\":3,\"namePrefix\":\"Masa\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"INDOOR\",\"count\":3,\"namePrefix\":\"Masa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].label").value("Masa 1"))
                .andExpect(jsonPath("$[2].label").value("Masa 3"));

        assertThat(tableRepository.findAllByBranchIdOrderByLabelAsc(java.util.UUID.fromString(branchA))).hasSize(3);
        assertThat(tableRepository.findAllByBranchIdOrderByLabelAsc(java.util.UUID.fromString(branchB))).isEmpty();
    }

    @Test
    void bulkCreateRejectsOutOfRangeCountBeforeTouchingTheDatabase() throws Exception {
        BranchFixture fixture = createBranchFixture("bulk-validation");
        MockCookie cookie = login(fixture, "bulk-validation-admin@example.com");

        mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"INDOOR\",\"count\":0,\"namePrefix\":\"Masa\"}"))
                .andExpect(status().isBadRequest());

        assertThat(tableRepository.findAllByBranchIdOrderByLabelAsc(java.util.UUID.fromString(fixture.branchId()))).isEmpty();
    }

    /**
     * İki eşzamanlı bulk-create isteği aynı branch'e karşı çalıştığında, branch satırının
     * PESSIMISTIC_WRITE kilidi (TenantService.bulkCreateTables) ikisini serileştirir - ikinci
     * istek birincisinin commit'ini bekleyip numaralandırmayı güncel etiketlerden yeniden
     * hesaplar. Bu yüzden (count=10 + count=10 sonrası) sonuç deterministiktir: tam 20 farklı
     * masa, "Masa 1".."Masa 20", hiç çakışma yok - iki istek de "Masa 11" üretmeye çalışmaz.
     */
    @Test
    void concurrentBulkCreatesOnSameBranchNeverProduceDuplicateLabels() throws Exception {
        BranchFixture fixture = createBranchFixture("bulk-concurrency");
        MockCookie cookie = login(fixture, "bulk-concurrency-admin@example.com");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"location\":\"INDOOR\",\"count\":10,\"namePrefix\":\"Masa\"}"))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> second = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"location\":\"INDOOR\",\"count\":10,\"namePrefix\":\"Masa\"}"))
                        .andReturn().getResponse().getStatus();
            });
            startLatch.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(201);

            List<RestaurantTable> tables = tableRepository.findAllByBranchIdOrderByLabelAsc(java.util.UUID.fromString(fixture.branchId()));
            assertThat(tables).hasSize(20);
            List<String> labels = tables.stream().map(RestaurantTable::getLabel).distinct().toList();
            assertThat(labels).hasSize(20);
            for (int i = 1; i <= 20; i++) {
                assertThat(labels).contains("Masa " + i);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Gerçek transaction-içi başarısızlık: bulk create'in numaralandırma taraması transaction
     * başında bir kez yapılır; branch kilidi yalnız başka bulk-create'lerle yarışır, tek masa
     * manuel oluşturma bu kilidi almaz. Bu yüzden manuel bir create tam bulk'un ürettiği
     * etiketlerden birine (ör. "Masa 3") bulk'un insert'inden önce commit olursa, bulk'un
     * flush()'ı uq_restaurant_table_branch_label ihlaliyle patlar ve @Transactional TÜM
     * batch'i (daha önce başarıyla eklenmiş "Masa 1"/"Masa 2" dahil) rollback eder - kısmi
     * oluşturma asla kalıcı olmaz. İki olası kazanan da (race) doğrulanıyor.
     */
    @Test
    void bulkCreateRollsBackEntireBatchWhenAConcurrentSingleCreateStealsAGeneratedLabel() throws Exception {
        BranchFixture fixture = createBranchFixture("bulk-rollback");
        MockCookie cookie = login(fixture, "bulk-rollback-admin@example.com");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        try {
            Future<Integer> bulk = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"location\":\"INDOOR\",\"count\":5,\"namePrefix\":\"Masa\"}"))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> manual = executor.submit(() -> {
                startLatch.await();
                return mockMvc.perform(post("/api/staff/tables").cookie(cookie)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"label\":\"Masa 3\",\"location\":\"INDOOR\"}"))
                        .andReturn().getResponse().getStatus();
            });
            startLatch.countDown();
            int bulkStatus = bulk.get(15, TimeUnit.SECONDS);
            int manualStatus = manual.get(15, TimeUnit.SECONDS);

            List<RestaurantTable> tables =
                    tableRepository.findAllByBranchIdOrderByLabelAsc(java.util.UUID.fromString(fixture.branchId()));
            if (bulkStatus == 201) {
                // Bulk fully committed first - manual's insert of the same label must then fail,
                // and never leave a duplicate/extra row behind.
                assertThat(manualStatus).isNotEqualTo(201);
                assertThat(tables).hasSize(5);
                assertThat(tables.stream().map(RestaurantTable::getLabel).distinct().toList()).hasSize(5);
            } else {
                // Manual won the "Masa 3" label first - bulk's flush must then fail, and the whole
                // batch (including the Masa 1/Masa 2 rows already inserted before the collision)
                // must roll back: only the manual table survives, never a partial bulk batch.
                assertThat(manualStatus).isEqualTo(201);
                assertThat(tables).hasSize(1);
                assertThat(tables.get(0).getLabel()).isEqualTo("Masa 3");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void newlyCreatedTablesSupportImmediateQrTokenGeneration() throws Exception {
        BranchFixture fixture = createBranchFixture("qr-after-create");
        MockCookie cookie = login(fixture, "qr-after-create-admin@example.com");

        String manualTableId = mockMvc.perform(post("/api/staff/tables").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Masa 1\",\"location\":\"INDOOR\"}"))
                .andReturn().getResponse().getContentAsString();
        String manualTableIdValue = objectMapper.readTree(manualTableId).get("id").asText();
        mockMvc.perform(post("/api/staff/tables/{tableId}/qr-tokens", manualTableIdValue).cookie(cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String bulkResponse = mockMvc.perform(post("/api/staff/tables/bulk").cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"OUTDOOR\",\"count\":1,\"namePrefix\":\"Teras\"}"))
                .andReturn().getResponse().getContentAsString();
        String bulkTableId = objectMapper.readTree(bulkResponse).get(0).get("id").asText();
        mockMvc.perform(post("/api/staff/tables/{tableId}/qr-tokens", bulkTableId).cookie(cookie))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void editUpdatesLabelLocationAndCapacityAndPersists() throws Exception {
        BranchFixture fixture = createBranchFixture("edit-table");
        MockCookie cookie = login(fixture, "edit-table-admin@example.com");
        String tableId = TenantFixtures.createTable(
                mockMvc, objectMapper, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(), "Yanlış Ad");

        mockMvc.perform(patch("/api/staff/tables/{tableId}", tableId).cookie(cookie)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Doğru Ad\",\"location\":\"OUTDOOR\",\"capacity\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Doğru Ad"))
                .andExpect(jsonPath("$.location").value("OUTDOOR"))
                .andExpect(jsonPath("$.capacity").value(6));

        mockMvc.perform(get("/api/staff/tables").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Doğru Ad"))
                .andExpect(jsonPath("$[0].location").value("OUTDOOR"))
                .andExpect(jsonPath("$[0].capacity").value(6));
    }

    private BranchFixture createBranchFixture(String suffix) throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "loc-fixture-" + suffix);
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube " + suffix);
        return new BranchFixture(businessId, branchId);
    }

    private MockCookie login(BranchFixture fixture, String email) throws Exception {
        return cookie(StaffFixtures.bootstrapAndLogin(
                mockMvc, TEST_ADMIN_TOKEN, fixture.businessId(), fixture.branchId(), email, "BUSINESS_ADMIN"));
    }

    private record BranchFixture(String businessId, String branchId) {
    }

    private static MockCookie cookie(String value) {
        return new MockCookie(StaffCookieSupport.COOKIE_NAME, value);
    }
}
