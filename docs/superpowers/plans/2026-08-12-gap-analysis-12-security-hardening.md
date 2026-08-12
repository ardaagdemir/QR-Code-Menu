# Gap-Analysis #12: Security Hardening / RLS Reassessment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the gap-analysis roadmap's final item (product-requirements.md M13) — reassess the "no DB-level RLS" decision, run a security review, verify export authorization, and document log-redaction/deployment/backup posture — while fixing a real cross-tenant authorization bug discovered during the review.

**Architecture:** No new modules or migrations. Task 1 tightens the single shared authorization choke point (`StaffAuthService.resolveStaffContextForBranch`) that `kitchen`, `ordering`, and `refund` controllers all call, closing a cross-tenant IDOR for every branch-scoped staff endpoint that routes through it. Task 2 adds missing regression tests for endpoints that were already correctly guarded but untested. Task 3 is verification + a `development-progress.md` write-up for the areas that were already compliant (CORS, cookies, DTO validation, logging, `.env.example`, actuator, backup/restore expectations) — no code changes there.

**Tech Stack:** Spring Boot 3.5.3 / Java 21 backend, MockMvc + Testcontainers Postgres integration tests (`AbstractIntegrationTest`), existing `TenantFixtures`/`StaffFixtures` test helpers.

## Global Constraints

- `JAVA_HOME` must point at JDK 21 before running `mvn` (the system default `mvn` resolves to JDK 24 — see development-progress.md Milestone 1).
- Run every step's `mvn` command from `backend/`.
- Each task ends with its own `git commit` (no batching multiple tasks into one commit — established project convention).
- Each task's final step updates the "Gap-Analysis #12" section already committed in `docs/development-progress.md` (append findings/results under the existing header; do not create a new standalone doc file).
- Do not touch `ModuleBoundaryTest` rules — the fix in Task 1 uses `TenantService`'s existing public method, never its `.repository` package, so no new ArchUnit exception is needed.
- No live-browser verification needed for this item (backend-only change, no frontend touched); rely on `mvn test`.

---

### Task 1: Fix cross-tenant branch-scoped authorization gap in StaffAuthService

**Context for the implementer:** `StaffContext.canAccessBranch(UUID branchId)` (`backend/src/main/java/com/qrmenu/staffaccess/StaffContext.java`) returns `true` unconditionally for `BUSINESS_ADMIN`/`PLATFORM_ADMIN`, regardless of which business the branch actually belongs to:

```java
public boolean canAccessBranch(UUID branchId) {
    return role == StaffRole.BUSINESS_ADMIN || role == StaffRole.PLATFORM_ADMIN || branchIds.contains(branchId);
}
```

`StaffAuthService.resolveStaffContextForBranch(sessionId, permission, branchId)` is the only place that calls `canAccessBranch`, and it is the sole authorization gate for `KitchenController`, `OrderControlController`, and `RefundController` (`com.qrmenu.kitchen.web`, `com.qrmenu.ordering.web`, `com.qrmenu.refund.web`). None of the `OrderingService`/`RefundService` methods those controllers call validate that `branchId` belongs to the caller's own business — `OrderingService.requireOrderInBranch` only checks `order.getBranchId().equals(branchId)`, and `tenantService.requireBusinessIdForBranch(branchId)` only checks the branch exists *somewhere*, not that it's the caller's. Net effect: a `BUSINESS_ADMIN` of Business A who knows (or guesses) a `branchId` belonging to Business B can view Business B's kitchen queue, accept/reject Business B's orders, and issue Business B's refunds.

(`StaffTenantController`, `StaffReportingController`, and `StaffDailyCloseController` are NOT affected — they already pass `context.businessId()` into `TenantService`/`ReportingService`/`DailyCloseService` methods that call `findByIdAndBusinessId` internally, so cross-tenant `branchId`s already 404 there. Verified by reading `TenantService.getBranch`, `TenantService.setOrderingEnabled`, `TenantService.setBranchBusinessHours`, `ReportingService.getBranchReport`, and `DailyCloseService.getById`/the daily-close controller's `tenantService.getBranch(...)` calls that precede every `dailyCloseService.listForBranch(...)` call.)

`PLATFORM_ADMIN` is intentionally business-id-less (`StaffUser.businessId == null`, documented in `StaffUser`'s Javadoc as "the one exception") and isn't reachable via staff-web login in v1 — its cross-tenant reach is by design and must NOT be restricted by this fix.

**Files:**
- Modify: `backend/src/main/java/com/qrmenu/staffaccess/StaffAuthService.java`
- Create: `backend/src/test/java/com/qrmenu/staffaccess/CrossTenantBranchAccessIntegrationTest.java`
- Modify: `docs/development-progress.md` (Gap-Analysis #12 section)

**Interfaces:**
- Consumes: `TenantService.requireBusinessIdForBranch(UUID branchId): UUID` (existing public method, `com.qrmenu.tenant.TenantService`, already used by `OrderingService` — throws `ResourceNotFoundException` if the branch doesn't exist at all).
- Produces: `StaffAuthService.resolveStaffContextForBranch(UUID sessionId, Permission required, UUID branchId): StaffContext` — same signature and return type as before; now additionally throws `StaffPermissionDeniedException` when the branch belongs to a different business than the caller's, for every role except `PLATFORM_ADMIN`.

- [ ] **Step 1: Write the failing integration test**

Create `backend/src/test/java/com/qrmenu/staffaccess/CrossTenantBranchAccessIntegrationTest.java`:

```java
package com.qrmenu.staffaccess;

import com.qrmenu.support.AbstractIntegrationTest;
import com.qrmenu.support.StaffFixtures;
import com.qrmenu.support.TenantFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockCookie;

import static com.qrmenu.support.AbstractIntegrationTest.TEST_ADMIN_TOKEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gap-analysis #12: StaffContext.canAccessBranch() let BUSINESS_ADMIN reach ANY
 * branchId, not just its own business's - KitchenController/OrderControlController/
 * RefundController all resolve authorization through
 * StaffAuthService.resolveStaffContextForBranch alone, with no secondary businessId
 * check downstream, so fixing that one choke point closes the hole for all three.
 */
class CrossTenantBranchAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void businessAdminCannotReachAnotherBusinesssBranchScopedEndpoints() throws Exception {
        String businessA = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cross-Tenant A");
        String businessB = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Cross-Tenant B");
        String branchB = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessB, "B Şube");
        String adminACookie =
                StaffFixtures.bootstrapBusinessAdminAndLogin(mockMvc, TEST_ADMIN_TOKEN, businessA, "cross-tenant-admin-a@example.com");
        MockCookie cookie = new MockCookie(StaffCookieSupport.COOKIE_NAME, adminACookie);

        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders", branchB).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/staff/branches/{branchId}/orders/pending-acceptance", branchB).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/kitchen/branches/{branchId}/orders/search", branchB).param("orderNumber", "1").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails (proves the vulnerability)**

Run (from `backend/`, with `JAVA_HOME` set to JDK 21):
```
mvn test -Dtest=CrossTenantBranchAccessIntegrationTest
```
Expected: FAIL — all three assertions get HTTP 200 instead of 403 (Business A's admin currently *can* read Business B's kitchen queue/pending-acceptance list/order search).

- [ ] **Step 3: Fix `StaffAuthService.resolveStaffContextForBranch`**

In `backend/src/main/java/com/qrmenu/staffaccess/StaffAuthService.java`, add the import and constructor dependency:

```java
import com.qrmenu.tenant.TenantService;
```

```java
    private final StaffUserRepository staffUserRepository;
    private final StaffUserBranchRepository staffUserBranchRepository;
    private final StaffSessionRepository staffSessionRepository;
    private final TenantService tenantService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffAuthService(
            StaffUserRepository staffUserRepository,
            StaffUserBranchRepository staffUserBranchRepository,
            StaffSessionRepository staffSessionRepository,
            TenantService tenantService) {
        this.staffUserRepository = staffUserRepository;
        this.staffUserBranchRepository = staffUserBranchRepository;
        this.staffSessionRepository = staffSessionRepository;
        this.tenantService = tenantService;
    }
```

Replace the existing `resolveStaffContextForBranch` method body:

```java
    /** Authenticated + must hold the given Permission + that permission must apply to this specific branch. */
    @Transactional(readOnly = true)
    public StaffContext resolveStaffContextForBranch(UUID sessionId, Permission required, UUID branchId) {
        StaffContext context = resolveStaffContext(sessionId, required);
        if (context.role() != StaffRole.PLATFORM_ADMIN
                && !tenantService.requireBusinessIdForBranch(branchId).equals(context.businessId())) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
        }
        if (!context.canAccessBranch(branchId)) {
            throw new StaffPermissionDeniedException("Not authorized for branch: " + branchId);
        }
        return context;
    }
```

No other file needs to change: `TenantService` has no dependency back on `StaffAuthService` (only `StaffTenantController`, a different class, depends on both), so this doesn't introduce a Spring bean cycle; `ModuleBoundaryTest` only forbids reaching into `.repository` packages across modules, and `TenantService` is the module's sanctioned public entry point (already used the same way by `OrderingService`).

- [ ] **Step 4: Run the test to confirm it passes**

Run:
```
mvn test -Dtest=CrossTenantBranchAccessIntegrationTest
```
Expected: PASS — all three assertions now get 403.

- [ ] **Step 5: Run the full backend test suite to confirm no regression**

Run:
```
mvn test
```
Expected: PASS, including `ModuleBoundaryTest`, `OrderControlFlowIntegrationTest`, `KitchenFlowIntegrationTest`, `RefundFlowIntegrationTest` (existing tests use same-business branchIds throughout, so `requireBusinessIdForBranch(...).equals(context.businessId())` continues to hold true for all of them; `PLATFORM_ADMIN`-path behavior is unchanged since the new check is skipped for that role).

- [ ] **Step 6: Update `docs/development-progress.md`**

In the "Gap-Analysis #12" section (already committed under the design note), add a subsection after the design content:

```markdown
**RLS reassessment — sonuç:** DB-seviyesi RLS eklenmedi (Section 21'deki karar korundu). Kod
taraması sırasında gerçek bir cross-tenant IDOR bulundu ve düzeltildi:
`StaffContext.canAccessBranch()` BUSINESS_ADMIN'i PLATFORM_ADMIN ile aynı şekilde ele alıp
herhangi bir branchId'ye izin veriyordu; `KitchenController`/`OrderControlController`/
`RefundController` bunun tek yetkilendirme kapısı olduğundan (altlarındaki OrderingService/
RefundService metotları yalnızca branch-order tutarlılığını kontrol ediyor, business
sahipliğini değil), bir işletmenin BUSINESS_ADMIN'i başka bir işletmenin mutfak kuyruğunu
görebilir/sipariş kabul-red edebilir/refund işleyebilirdi.
`StaffAuthService.resolveStaffContextForBranch` artık PLATFORM_ADMIN dışında her rol için
branch'in gerçekten `context.businessId()`'ye ait olduğunu `TenantService.
requireBusinessIdForBranch` ile doğruluyor. `StaffTenantController`/`StaffReportingController`/
`StaffDailyCloseController` zaten kendi servis katmanlarında businessId-scoped sorgu kullandığı
için bu açıktan etkilenmiyordu (`TenantService.getBranch`/`findByIdAndBusinessId` deseni).
Yeni regresyon testi: `CrossTenantBranchAccessIntegrationTest`.
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/qrmenu/staffaccess/StaffAuthService.java \
        backend/src/test/java/com/qrmenu/staffaccess/CrossTenantBranchAccessIntegrationTest.java \
        docs/development-progress.md
git commit -m "$(cat <<'EOF'
fix(staffaccess): close cross-tenant branch access gap in resolveStaffContextForBranch

BUSINESS_ADMIN could reach any business's branch-scoped kitchen/order-control/
refund endpoints because canAccessBranch() never checked branch ownership.
Gap-Analysis #12 (RLS reassessment) finding.
EOF
)"
```

---

### Task 2: Add missing export-authorization regression tests

**Context for the implementer:** The daily-close Excel export endpoints (`StaffDailyCloseController.exportBranchExcel` / `.exportChainExcel`) already enforce the correct permission (`Permission.REPORT_VIEW` branch-scoped, `Permission.REPORT_CHAIN_VIEW` business-wide respectively — see `backend/src/main/java/com/qrmenu/dailyclose/web/StaffDailyCloseController.java`), but no existing test asserts a 403 for a role that lacks that permission on these specific export endpoints. `DailyCloseFlowIntegrationTest.kitchenStaffCannotAccessDailyClose` only covers `GET /daily-close` and `POST /daily-close/final`, not `/daily-close/excel` or the chain export. `ReportingFlowIntegrationTest.chainReportAggregatesAcrossBranchesAndIsAdminOnly` covers the chain JSON report's admin-only-ness but not the chain Excel export.

**Files:**
- Modify: `backend/src/test/java/com/qrmenu/dailyclose/DailyCloseFlowIntegrationTest.java`
- Modify: `docs/development-progress.md` (Gap-Analysis #12 section)

**Interfaces:**
- Consumes: existing `StaffDailyCloseController` endpoints `GET /api/staff/branches/{branchId}/daily-close/excel` and `GET /api/staff/daily-close/excel` (both already implemented, no production code changes in this task); existing `TenantFixtures.createBusiness/createBranch`, `StaffFixtures.login`, `MockCookie(StaffCookieSupport.COOKIE_NAME, ...)` test helpers.
- Produces: two new `@Test` methods, no new production interfaces.

- [ ] **Step 1: Write the two new test methods (they should already pass — this is regression coverage, not a bugfix)**

Add to `backend/src/test/java/com/qrmenu/dailyclose/DailyCloseFlowIntegrationTest.java`, right after `kitchenStaffCannotAccessDailyClose` (all imports used below — `TenantFixtures`, `StaffFixtures`, `MediaType`, `MockCookie`, `get`, `status` — are already imported in this file):

```java
    @Test
    void kitchenStaffCannotExportDailyCloseExcel() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Export Auth Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String kitchenEmail = "export-auth-kitchen@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + kitchenEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"KITCHEN_STAFF\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String kitchenCookie = StaffFixtures.login(mockMvc, kitchenEmail);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/staff/branches/{branchId}/daily-close/excel", branchId)
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, kitchenCookie)))
                .andExpect(status().isForbidden());
    }

    /** BRANCH_MANAGER has REPORT_VIEW (branch-scoped) but not REPORT_CHAIN_VIEW - the chain export must still reject it. */
    @Test
    void branchManagerCannotExportChainDailyCloseExcel() throws Exception {
        String businessId = TenantFixtures.createBusiness(mockMvc, objectMapper, TEST_ADMIN_TOKEN, "Chain Export Auth Business");
        String branchId = TenantFixtures.createBranch(mockMvc, objectMapper, TEST_ADMIN_TOKEN, businessId, "Şube");
        String managerEmail = "export-auth-manager@example.com";
        mockMvc.perform(post("/internal/businesses/{businessId}/staff-users", businessId)
                        .header("X-Internal-Admin-Token", TEST_ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + managerEmail + "\",\"password\":\"" + StaffFixtures.DEFAULT_PASSWORD
                                + "\",\"role\":\"BRANCH_MANAGER\",\"branchIds\":[\"" + branchId + "\"]}"))
                .andExpect(status().isCreated());
        String managerCookie = StaffFixtures.login(mockMvc, managerEmail);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/staff/daily-close/excel")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .cookie(new MockCookie(StaffCookieSupport.COOKIE_NAME, managerCookie)))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: Run the new tests**

Run:
```
mvn test -Dtest=DailyCloseFlowIntegrationTest
```
Expected: PASS (both new tests, plus the 4 pre-existing ones in this file). If either new test unexpectedly fails, that's a second real authorization gap — stop and investigate before continuing, the same way Task 1 did.

- [ ] **Step 3: Update `docs/development-progress.md`**

Append to the Gap-Analysis #12 section:

```markdown
**Export authorization tests — sonuç:** `daily-close/excel` (branch) ve `daily-close/excel`
(chain) uç noktaları zaten doğru Permission kontrolünü yapıyordu (REPORT_VIEW / REPORT_CHAIN_VIEW)
ama hiçbir test bunu doğrulamıyordu. `DailyCloseFlowIntegrationTest`'e iki yeni test eklendi:
`kitchenStaffCannotExportDailyCloseExcel`, `branchManagerCannotExportChainDailyCloseExcel`.
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/qrmenu/dailyclose/DailyCloseFlowIntegrationTest.java docs/development-progress.md
git commit -m "$(cat <<'EOF'
test(dailyclose): cover export-endpoint authorization (Gap-Analysis #12)

Excel export (branch + chain) already enforced the right Permission but had
no regression test proving it.
EOF
)"
```

---

### Task 3: Security-review checklist, log/token redaction, deployment hardening, backup/restore — verify and document

**Context for the implementer:** This task has no expected production code changes — everything below was already checked during the design phase and found compliant. The job is to re-run each verification command yourself (don't just trust this document), confirm the same result, and write it up. If any command's actual output disagrees with what's stated here, stop and treat it as a new finding (open a small fix, same pattern as Task 1) rather than writing down a false "OK".

**Files:**
- Modify: `docs/development-progress.md` (Gap-Analysis #12 section — close it out as `✅ COMPLETED`)
- Modify: `docs/gap-analysis.md` (mark item 12 as done, same convention as items 6-11)

**Interfaces:** None (documentation only).

- [ ] **Step 1: CORS + cookie flags — re-verify**

Run:
```
grep -n "allowedOrigins\|allowedMethods\|allowCredentials" backend/src/main/java/com/qrmenu/common/web/CorsConfig.java
grep -rn "\.secure(\|\.httpOnly(\|\.sameSite(" backend/src/main/java/com/qrmenu/staffaccess/web/StaffAuthController.java backend/src/main/java/com/qrmenu/customersession/web/QrCheckinController.java
```
Expected: `CorsConfig` restricts to `/api/**` with an explicit non-wildcard origin list + `allowCredentials(true)`; both cookie-setting methods chain `.httpOnly(true).secure(true).sameSite("Lax")`. This is already correct — no code change.

- [ ] **Step 2: DTO validation consistency — re-verify**

Run:
```
grep -rn "@RequestBody" backend/src/main/java | grep -v "@Valid"
```
Expected: exactly one result, `PaymentWebhookController.handleMockWebhook`, which takes a raw `String` body by design (documented in that file's Javadoc as needed for signature verification ahead of parsing) — every other `@RequestBody` DTO is validated. No code change.

- [ ] **Step 3: Log/token redaction — re-verify**

Run:
```
grep -rn "log\.\(info\|debug\|warn\|error\|trace\)(" backend/src/main/java
grep -rn "printStackTrace" backend/src/main/java
```
Expected: a single logger call, `OwnerNotificationService`'s `log.warn(...)` on email-delivery failure, which logs only `reportId`/`contactId` (UUIDs) and the SMTP exception's message — no password/token/webhook-secret/card data anywhere in the codebase, and no `printStackTrace` calls that could leak a stack trace to a client. Also confirm the default Spring Boot error response doesn't leak stack traces:
```
grep -n "server:" backend/src/main/resources/application.yml backend/src/main/resources/application-local.yml
```
Expected: no `server.error.include-stacktrace`/`include-message` override present, meaning Spring Boot's defaults (`never`) apply. Also confirm there's no `logging.level.*` override (`grep -n "logging:" backend/src/main/resources/application*.yml`) — expected: none, so the default `INFO` root level applies, which is appropriate given how little the app currently logs. No code change.

- [ ] **Step 4: Deployment hardening — re-verify**

Run:
```
cat infra/.env.example
grep -n "exposure\|show-details" backend/src/main/resources/application.yml
```
Expected: `.env.example` contains only clearly-labeled placeholder values (`*-change-me` for the two required secrets, `qrmenu_local_dev_only` for the local Postgres password) with a comment that `.env` is gitignored; `management.endpoints.web.exposure.include` is `health,info` only and `health.show-details` is `never`. No code change.

- [ ] **Step 5: Backup/restore expectations — write the procedure note**

No command to run — this is a new doc-only note (Postgres runs in Docker Compose with a named volume, no backup automation exists yet). Add to `docs/development-progress.md`.

- [ ] **Step 6: Update `docs/development-progress.md`, closing out Gap-Analysis #12**

Append to the Gap-Analysis #12 section, then change the section header from `🔄 TASARIM ONAYLANDI, UYGULAMA SÜRÜYOR` to `✅ COMPLETED`:

```markdown
**Security review (kalan kontroller) — sonuç:** CORS (`CorsConfig`: `/api/**`'e scoped, wildcard
olmayan origin listesi + `allowCredentials`), cookie flag'leri (staff+customer session
cookie'lerinin ikisi de `HttpOnly`/`Secure`/`SameSite=Lax`), DTO validation (`@RequestBody`
kullanan her uç nokta `@Valid` - tek istisna, tasarımı gereği ham body alan
`PaymentWebhookController`) zaten doğruydu, kod değişikliği gerekmedi.

**Token/log redaction — sonuç:** Backend'de tek bir logger çağrısı var
(`OwnerNotificationService`, yalnızca reportId/contactId/SMTP hata mesajı basıyor,
hassas veri yok), `printStackTrace` hiç kullanılmıyor, `server.error.include-stacktrace/
include-message` override edilmemiş (Spring Boot varsayılanı: never). Kod değişikliği
gerekmedi.

**Deployment hardening — sonuç:** `infra/.env.example`'da gerçek secret yok (yalnızca
"change-me" placeholder'lar + local-only Postgres şifresi), actuator zaten `health,info`'a
kısıtlı ve `show-details: never`. Kod değişikliği gerekmedi.

**Backup/restore expectations:** Postgres, Docker Compose'da adlandırılmış bir volume ile
çalışıyor (bkz. `infra/docker-compose.yml`) - konteyner silinse bile veri korunuyor, ama
otomatik bir `pg_dump` zamanlaması veya restore tatbikatı yok. Prod'a çıkışta: (1) düzenli
`pg_dump` (ör. günlük, gün sonu snapshot'ından sonra) harici bir depoya (yerel volume'un
dışına) alınmalı, (2) restore prosedürü en az bir kez gerçek bir dump ile tatbik edilmeli,
(3) `INTERNAL_ADMIN_TOKEN`/`PAYMENT_MOCK_WEBHOOK_SECRET` gibi ortam değişkenleri de yedeğin
bir parçası olarak (ayrı, güvenli bir secret store'da) saklanmalı. V1 kapsamında bu yalnızca
bir beklenti notu - otomasyon bu maddenin parçası değil.

Gap-Analysis #12 tamamlandı: RLS için DB-seviyesi politika eklenmedi (bilinçli karar
korundu), ama reassessment sürecinde gerçek bir cross-tenant IDOR bulunup düzeltildi
(yukarıya bkz.). Diğer M13 alt maddeleri (rate limiting, upload security) zaten
var/N-A olduğundan yeniden ele alınmadı.
```

- [ ] **Step 7: Mark item 12 done in `docs/gap-analysis.md`**

In `docs/gap-analysis.md`, section "3. Önerilen Geliştirme Sırası", change line 65 from:
```
12. **Security hardening / RLS yeniden değerlendirme** — kapanışta.
```
to:
```
12. ✅ **Security hardening / RLS yeniden değerlendirme** — kapanışta. (Bkz. development-progress.md,
    Gap-Analysis #12.)
```
(matching the exact `✅ **...** (Bkz. development-progress.md, Gap-Analysis #N.)` convention used by items 6-11 in the same list.)

- [ ] **Step 8: Final full-suite run**

Run:
```
mvn test
```
Expected: PASS (all backend tests, including both new test classes/methods from Tasks 1-2).

- [ ] **Step 9: Commit**

```bash
git add docs/development-progress.md docs/gap-analysis.md
git commit -m "$(cat <<'EOF'
docs: close out Gap-Analysis #12 (security hardening / RLS reassessment)

CORS/cookies/DTO validation/logging/deployment config/backup expectations
verified compliant; RLS reassessment + security review finding already
fixed in prior commits.
EOF
)"
```
