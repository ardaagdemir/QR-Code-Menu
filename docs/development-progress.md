# Geliştirme İlerleme Durumu

Bu dosya, `docs/milestone-1-report.md` … `milestone-4-report.md` dosyalarının yerine geçen özet bir durum
kaydıdır. Amaç geçmişin ayrıntılı raporunu tutmak değil, yeni bir Claude oturumunun projenin mevcut durumunu
hızlıca kavramasıdır. Tam gereksinimler/kararlar için [`product-requirements.md`](product-requirements.md)
(özellikle Bölüm 9 — milestone planı) tek otoritedir; buradaki notlar yalnızca "ne yapıldı, neden, nelere
dikkat" özetidir.

**Genel durum:** Backend `com.qrmenu` modüler monolit (Spring Boot 3.5.3, Java 21, Maven, PostgreSQL 16, Flyway
V1–V11). İki frontend uygulaması var: `customer-web` (Next.js 16, `app/t/[token]`, `app/order/track/[token]`) ve
`staff-web` (Next.js 16, port 3002 — gerçek StaffUser login + admin ekranları + pickup board, Milestone 8/9). 10
modül var: `tenant`, `customersession`, `menu`, `ordering`, `payment`, `kitchen`, `notification`, `refund`,
`staffaccess`, `audit` (+ modül-olmayan `shared`/`shared.outbox`). Tüm milestone'lar (1-9) tamamlandı. Git deposu
hâlâ **başlatılmadı** — hiçbir commit yok.

---

## Milestone 1 — Foundation — ✅ COMPLETED

**Ana özellikler:** Maven/Spring Boot iskeleti, PostgreSQL + Flyway + Testcontainers bağlantısı, Actuator health
endpoint, customer-web Next.js iskeleti, Docker Compose (postgres/backend/customer-web).

**Teknik kararlar:** Host'ta yerel Postgres ile çakışmayı önlemek için Compose'da host portu 5433 (konteyner-içi
hâlâ 5432); Actuator yeterli, özel health controller yok; `application.yml`/`application-local.yml` ayrımı
(default = Docker Compose servis adı, local = host'tan `localhost:5433`); Milestone 1'de `shared/`
(Money/DomainEvent/Outbox) kasıtlı olarak yok.

**Bilinen notlar:** Sistemin varsayılan `mvn`'i JDK 24 kullanıyor — derleme/test için `JAVA_HOME`'un JDK 21'e
işaret etmesi gerekiyor.

---

## Milestone 2 — Business/Branch/Table/QR + AnonymousCustomerSession/TableVisit — ✅ COMPLETED

**Ana özellikler:** `tenant` modülü (Business/Branch/RestaurantTable/TableQrToken), `customersession` modülü
(AnonymousCustomerSession/TableVisit); internal/PLATFORM_ADMIN bootstrap API (`X-Internal-Admin-Token` guard,
`/internal/businesses/**`); public `POST /api/qr/{token}/visit` — QR doğrulama → TableVisit başlatma/devam
ettirme + `qrmenu_session` cookie'si; `ModuleBoundaryTest` (ArchUnit) ile modüller arası repository erişim yasağı.

**Teknik kararlar:** `business_id` her tenant tablosunda **doğrudan** sütun (join değil); QR token **plaintext**
saklanıyor (yalnızca yeni TableVisit başlatabildiği için blast radius düşük — hash yalnızca `orderTrackingToken`
için, Milestone 4); TableQrToken'da tek `ACTIVE` kısıtı partial unique index ile; TableVisit TTL 6 saat (RECOMMENDED
aralığın üst sınırı); internal API koruması tam Spring Security değil, tek bir `OncePerRequestFilter` + paylaşılan
token; `archunit-junit5`'in `@AnalyzeClasses`/`@ArchTest` modeli Maven Surefire'da hiç çalışmadı (sessizce 0 test) —
ArchUnit düz kütüphane olarak normal `@Test` içinden kullanılıyor.

**Bilinen notlar:** `internal.admin.token` prod'da değiştirilmeli, `.env.example`'daki değer yalnızca local dev.
`Branch.openingTime`/`closingTime`/`orderingEnabled` alanları var ama **hâlâ hiçbir yerde uygulanmıyor**.

---

## Milestone 3 — Business-level Ürün Kataloğu + Branch-level BranchProduct — ✅ COMPLETED

**Ana özellikler:** `menu` modülü (MenuCategory/Product/ProductOptionGroup/ProductOption/BranchProduct); internal
CRUD API'leri; public `GET /api/branches/{branchId}/menu` (opt-in birleştirilmiş menü); customer-web
`app/t/[token]` sayfası (QR karşılama + banner + menü görüntüleme); `CorsConfig` (`/api/**` için credentialed CORS,
`GET/POST/PUT`).

**Teknik kararlar:** `BranchProduct` **opt-in** — kayıt yoksa ürün menüde hiç görünmüyor (yalnızca "Tükendi" değil,
tamamen yok); boş kategoriler yanıttan düşürülüyor; `taxRatePercent` her üründe zorunlu (sessiz varsayılan yok);
Money **henüz eklenmedi** (Milestone 4'e bırakıldı); menü yönetimi de aynı internal/PLATFORM_ADMIN deseniyle
(gerçek BUSINESS_ADMIN ekranı Milestone 8).

**Bilinen notlar:** Sepete ekleme/opsiyon seçimi interaktivitesi o an yoktu (Milestone 4'te eklendi).
`Branch.orderingEnabled`/çalışma saatleri hâlâ uygulanmıyor.

---

## Milestone 4 — DRAFT Order/Sepet + Backend Fiyat Doğrulaması — ✅ COMPLETED

**Ana özellikler:** `ordering` modülü (CustomerOrder/OrderItem/OrderItemOption); `com.qrmenu.shared.Money` value
object (ilk kullanımı); sepet API'leri — `GET/POST/DELETE /api/table-visits/{tableVisitId}/cart[/items[/{id}]]`
(cookie ile TableVisit sahipliği doğrulanır); backend-authoritative Product+BranchProduct+opsiyon revalidasyonu;
`orderTrackingToken` üretimi + SHA-256 hash (Order oluşturulduğu anda, ham değer yalnızca bir kez döner); 2 saatlik
DRAFT TTL temizlik job'ı (`@Scheduled`); customer-web'de opsiyon seçim modalı + sepet çubuğu.

**Teknik kararlar:** `OrderStatus` şimdilik yalnızca `DRAFT`/`CANCELLED` (state machine'in geri kalanı henüz
ulaşılamayan durumlar için eklenmedi); Money JPA-mapped değil, yalnızca hesaplama noktasında kullanılıyor (entity'ler
hâlâ düz `long` kuruş); her "sepete ekle" çağrısı yeni bir `OrderItem` satırı (birleştirme/merge yok); Branch
ordering-enabled/çalışma saati kontrolü **bilinçli olarak eklenmedi** — doküman bu kontrolün otoriter halini
ödeme öncesine (Milestone 5) koyuyor; `GET /order/track/{token}` okuma endpoint'i bilinçli olarak eklenmedi
(Milestone 6).

**Bilinen notlar:** Gerçek tarayıcı testinde CORS `allowedMethods` listesinde `DELETE` unutulmuştu (403) —
MockMvc testleri bunu yakalayamadı, düzeltildi. Bu, yeni HTTP metodu eklenen her milestone'da gerçek tarayıcı
doğrulamasının atlanmaması gerektiğini gösteriyor. `OrderStatus`'a `AWAITING_PAYMENT` vb. eklenmesi ve DRAFT'ın
o noktadan sonra değiştirilemez olması Milestone 5'in işi.

---

## Frontend UX/UI Quality Baseline (Milestone 5 öncesi gate) — ✅ COMPLETED

**Ana özellikler:** `product-requirements.md` Bölüm 14 (yeni) eklendi. Backend: `Product.imageUrl` (opsiyonel,
V6 migration, mevcut migration'lara dokunulmadı). Frontend: `app/globals.css`'te tek bir design token katmanı
(renk/spacing/radius/tipografi/gölge/z-index/44px dokunma hedefi); `components/ui/` altında paylaşılan
primitive'ler (`Button`, `Badge`, `Skeleton`, `EmptyState`, `ErrorState`, `QuantityStepper`, `BottomSheet`);
`/t/[token]` sayfası tek devasa component'ten `VisitHeader`/`CategoryNav`/`MenuSection`/`ProductCard`/
`ProductOptionsSheet` (eski `ProductModal`)/`CartDrawer` (eski `CartBar`)/`MenuSkeleton`'a bölündü; sticky
header+kategori nav (IntersectionObserver ile scroll-spy), skeleton/empty/error+retry state'leri, `role="dialog"`/
`aria-modal`/Escape ile kapanan bottom sheet'ler, ürün görseli yüklenemezse placeholder'a düşen `onError` fallback.

**Teknik kararlar:** Ağır bir UI framework'ü eklenmedi (Tailwind/MUI vb.) — sade CSS custom properties + CSS
modules yeterli görüldü; `Product.imageUrl` düz `<img>` ile gösteriliyor (`next/image` değil — media/CDN altyapı
karmaşıklığı istenmedi), CLS `.media`'daki sabit `aspect-ratio` ile önleniyor; sepet drawer'a ödeme/checkout CTA'sı
**eklenmedi** (Milestone 5'e başlamamak için bilinçli sınır).

**Bilinen notlar:** `staff-web` (Milestone 6/8) bu token/component yaklaşımını yeniden kullanacak şekilde
planlanmalı, sıfırdan farklı bir tasarım dili kurulmamalı. Bu ortamda gerçek dar/geniş tarayıcı viewport'u
`resize_window` ile tutarsız davrandığından (yeni sekmelerde ~500px'e sabitleniyor), masaüstü/geniş breakpoint
doğrulaması bir iframe enjeksiyon tekniğiyle yapıldı — gerçek bir cihazda/normal masaüstü Chrome'da ayrıca
gözden geçirilmesi faydalı olur.

---

## Milestone 5 — Mock Ödeme Sağlayıcısı, Webhook, Idempotency, Transactional Outbox — ✅ COMPLETED

**Ana özellikler:** Yeni `payment` modülü (`PaymentProviderPort` + `MockPaymentProviderAdapter`: `CREATED →
PROCESSING → [ayrı, async webhook dispatch] → SUCCEEDED|FAILED`, HMAC-SHA256 imza doğrulama, `(provider,
event_id)` unique constraint ile idempotency); `CustomerOrder` state machine'ine `AWAITING_PAYMENT`/`PAID`/
`PAYMENT_FAILED` eklendi (`markAwaitingPayment`/`markPaid`/`markPaymentFailed`, `PAYMENT_FAILED →
AWAITING_PAYMENT` retry dahil); `TenantService.assertOrderingCurrentlyAllowed` ile Branch ordering-enabled/çalışma
saati kontrolünün otoriter hali, ödeme başlamadan hemen önce eklendi; `shared/outbox` altyapısı (`OutboxEvent` +
`OutboxEventWriter` + `@Scheduled` `OutboxPollerScheduler`, in-process `ApplicationEventPublisher` ile yayınlıyor)
— Order PAID olduğunda `OrderPaid` event'i aynı transaction'da yazılıyor. API: `POST
/api/table-visits/{id}/payments` (intent oluştur), `GET .../payments/{id}` (durum sorgusu), `POST
.../payments/{id}/mock-outcome` (yalnızca `payment.provider=mock` iken var — dev "hosted ödeme ekranı" tetikleyici,
`@ConditionalOnProperty`), `POST /api/payments/webhook/mock` (gerçek sağlayıcı webhook'unun şekli — ham body +
`X-Mock-Signature` header). customer-web: `CartDrawer`'a "Ödemeye Geç" CTA'sı + yeni `PaymentSheet` (mock ödeme
ekranı, başarı/başarısız simülasyonu, `getPaymentStatus` polling ile sonucu bekleme, başarısızlıkta "Tekrar Dene").

**Teknik kararlar:** Mock simulate → webhook dispatch, gerçek bir HTTP self-call değil — `MockPaymentSimulationDispatcher`
(`@Async`) doğrudan `PaymentWebhookService.handleIncomingWebhook`'u çağırıyor; hem `/api/payments/webhook/mock`
hem mock dispatch AYNI kod yolundan geçiyor (Bölüm 1.3 gereksinimi). Webhook idempotency insert'i
`PaymentWebhookIdempotencyGuard` ile ayrı bir `REQUIRES_NEW` transaction'da yapılıyor — aksi halde bir constraint
violation, aynı transaction'ı Hibernate seviyesinde rollback-only işaretleyip çağıran kodun `UnexpectedRollbackException`
almasına yol açıyordu (canlıda bulunup düzeltildi). `Payment`/`OrderStatus` enum'larına yalnızca bu milestone'da
gerçekten ulaşılabilen değerler eklendi (`EXPIRED`/`CANCELLED`/`IN_KITCHEN` vb. eklenmedi — kod yolu yok).
`shared/outbox` bilinçli olarak bir "modül" değil (ArchUnit sınırı yok, Money ile aynı muamele) — her modül
doğrudan enjekte edip kullanabiliyor. `docker-compose.yml`/`.env(.example)`'a `PAYMENT_MOCK_WEBHOOK_SECRET`
(zorunlu, default yok) eklendi.

**Bilinen notlar:** Gerçek Chrome doğrulamasında bir React Strict Mode bug'ı bulundu ve düzeltildi:
`PaymentSheet`'in polling-iptal ref'i yalnızca cleanup'ta `true` set ediliyordu, `next dev`'in
mount→cleanup→mount double-invoke'unda kalıcı olarak `true` takılı kalıp component gerçekten mount'luyken bile
polling'i sessizce öldürüyordu — düzeltme: effect'in setup fazında da `false`'a resetleniyor. Refund (Milestone 7)
kapsamında `PaymentProviderPort.refund` şimdilik `UnsupportedOperationException` atıyor. Ödeme timeout/reconciliation
job'ı (Bölüm 1.3'teki risk) bu milestone'un bilinçli kapsamı dışında bırakıldı — dokümanda M5'e atanmamıştı.

---

## Milestone 6 — Kitchen Display, Kısmi Adet Kabul/Red, SSE — ✅ COMPLETED

**Ana özellikler:** Yeni `kitchen` modülü (KDS sorgu/komut API'leri, `/api/kitchen/branches/{branchId}/...`) ve yeni
`notification` modülü (`OrderStatusNotifier` kanal-agnostik arayüzü + `SseOrderStatusNotifier` — in-process SSE
pub/sub, hem tek bir siparişi izleyen müşteriye hem bir şubenin tüm mutfak kuyruğunu izleyen KDS'e aynı update'i
yayınlıyor). `OrderItem`e `status` (`PENDING_REVIEW→PREPARING|REJECTED→READY→SERVED`) eklendi; `CustomerOrder`e
`orderNumber` (şube+gün bazlı, atomik `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` ile üretiliyor —
`OrderNumberGenerator`) ve `IN_KITCHEN`/`READY` durumları eklendi. Ödeme başarılı olduğunda (`OrderingService.
markOrderPaid`) sipariş numarası atanıp **senkron olarak** `PAID→IN_KITCHEN`'a geçiyor (outbox'un 10s poll
gecikmesini beklemeden — M5'te yazılan `OrderPaid` outbox event'i dursa da KDS'i beslemek için kullanılmıyor).
`GET /api/order-tracking/{token}` + `.../stream` (read-only, cookie'siz, Bölüm 5) eklendi. customer-web'de yeni
`/order/track/[token]` sayfası (SSE ile canlı durum) ve `PaymentSheet`'in başarı ekranında sipariş no + paylaşılabilir
takip linki. **Yepyeni `frontend/staff-web` uygulaması** (Next.js, customer-web'in token/component yaklaşımı
kopyalanarak) — basit giriş ekranı (Şube ID + erişim anahtarı, `localStorage`'da saklanıyor) + KDS board
(`/kitchen/[branchId]`, canlı sipariş kartları, adet bazlı kabul/red + hazır/teslim edildi aksiyonları).
`docker-compose.yml`'e `staff-web` servisi (port 3002) ve `STAFF_ACCESS_TOKEN`/`CORS_ALLOWED_ORIGINS` eklendi.

**Teknik kararlar:** KDS erişimi "basit/geçici" bir paylaşılan-secret filtresiyle korunuyor
(`StaffAccessAuthFilter`, `X-Staff-Access-Token` header veya `staffToken` query param — `EventSource` header
gönderemediği için); gerçek `StaffUser` auth Milestone 8'de. Order-seviyesi `REJECTED` durumu bilinçli olarak DB'de
ayrı bir status değeri olarak **tutulmuyor** — dokümanın "rollup, persisted state değil" notuna göre tam red edilen
bir sipariş de `READY`'ye düşüyor, detay `OrderItem` seviyesinde. `CustomerOrder.markInKitchen()`'ın outbox yerine
senkron çağrılması bilinçli bir tasarım kararı: outbox poller'ın 10s `fixedDelay`'i canlı bir mutfak ekranı için
kabul edilemez bir gecikme olurdu.

**Bilinen notlar (canlı Chrome doğrulamasında bulunup düzeltilen 3 gerçek hata):**
1. **CORS preflight 403:** `StaffAccessAuthFilter`, tarayıcının `OPTIONS` preflight isteğini de token kontrolüne
   tabi tutuyordu — preflight istekleri hiçbir zaman özel header taşımaz, bu da staff-web'den gelen HER isteği
   CORS seviyesinde kırıyordu. Düzeltme: filtre artık `OPTIONS` metodunu kontrolsüz geçiriyor.
2. **Torn read (tutarsız anlık görüntü):** `OrderingService.getOrderTrackingView` ve `getKitchenQueue`, bir
   siparişi ve kalemlerini ayrı `SELECT`'lerle okuyordu; varsayılan READ COMMITTED izolasyonunda, bu iki sorgu
   arasına başka bir mutfak işleminin commit'i girebiliyor ve müşteri takip sayfasında sipariş durumu `IN_KITCHEN`
   iken kalem durumu `READY` gibi tutarsız bir karışım dönebiliyordu. Düzeltme: her iki metot da
   `@Transactional(isolation = Isolation.REPEATABLE_READ)` ile tek bir tutarlı snapshot okuyor.
3. **Senkronize olmayan SSE yazımı:** İki mutfak aksiyonu (`decide` + `ready`) arka arkaya, boşluksuz geldiğinde,
   iki farklı HTTP thread'i **aynı** `SseEmitter`'a eşzamanlı `send()` çağrısı yapabiliyordu (`SseEmitter.send`
   eşzamanlı çağrılar için thread-safe değil) — bu, ikinci event'in sessizce kaybolmasına/stream'in bozulmasına yol
   açıyordu (müşteri takip sayfası ilk durumda kilitli kalıyordu). Düzeltme: `SseOrderStatusNotifier.sendToAll`
   artık her emitter'a yazarken `synchronized (emitter)` kullanıyor.

Üçü de yalnızca gerçek Chrome + gerçek eşzamanlı istek testleriyle ortaya çıktı; entegrasyon testleri (MockMvc,
tek thread) bu sınıf hataları yakalayamıyor.

---

## Milestone 7 — Tam/Kısmi İade, Makbuz — ✅ COMPLETED

**Kapsam kararı:** M7'nin dokümandaki maddesi ("Refund + RefundItem, çoklu kısmi iade + toplam tutar sınırı,
sağlayıcı iade entegrasyonu (mock), makbuz") **personel-başlatmalı** iade akışı olarak uygulandı. OrderItem state
diyagramındaki "red edilince otomatik RefundItem oluşturulur" notu bilinçli olarak uygulanmadı — bunu yapmak
`ordering → refund → payment → ordering` şeklinde bir modüller-arası döngü gerektiriyordu (refund zaten
payment+ordering'e bağımlı; ordering'in de refund'a bağımlı olması döngü oluşturur). Personel, red edilen kalemi
manuel iade akışından zaten iade edebiliyor (arayüz reddedilen adedi otomatik öneriyor) — bu, dokümanın "kısmi red
+ kısmi iade yeterli güvenlik ağı" ilkesini hâlâ karşılıyor, sadece "otomatik" değil "personel onaylı".

**Ana özellikler:** Yeni `refund` modülü — `Refund` (`REQUESTED→PROCESSING→COMPLETED`, mock sağlayıcı hiç
başarısız olmadığından `FAILED` eklenmedi) + `RefundItem` (`orderItemId`, `refundedQuantity`,
`refundAmountMinorUnits`). `Payment`e `totalRefundedAmountMinorUnits` + `applyRefund()` eklendi — tek iade
kuralı: `toplam iade ≤ ödenen tutar`, aynı transaction'da kontrol+güncelleme. Miktar her zaman `OrderItem`'ın
snapshot birim fiyatından backend'de hesaplanıyor (personel tutar girmiyor, yalnızca adet). Personel API'leri
(`/api/kitchen/branches/{branchId}/orders/search?orderNumber=N`, `POST .../{orderId}/refunds`, `GET
.../{orderId}/refunds`) mevcut `StaffAccessAuthFilter`'ın `/api/kitchen/*` kapsamını paylaşıyor (URL öneki
paylaşımı salt pragmatik — Servlet path-pattern'i tek wildcard'a izin verdiği için, iadelerin "mutfak" konusu
olduğu anlamına gelmiyor). Yeni printable makbuz: `GET /api/order-tracking/{token}/receipt` (public, token'lı,
`refund` modülünde barındırılıyor — aynı döngü-önleme mantığıyla). customer-web: takip sayfasına "Makbuzu
Görüntüle" linki + yeni `/order/track/[token]/receipt` sayfası (işletme/şube adı, kalemler, ödenen/iade
edilen/net tutar, iade geçmişi, `window.print()` ile yazdırma, `@media print` ile buton gizleme). staff-web: yeni
`/refunds/[branchId]` sayfası (sipariş no ile arama, kalem bazlı iade adedi girişi, geçmiş iadeler), KDS
ekranından link.

**Teknik kararlar:** Mock sağlayıcının `refund()` çağrısı, ödeme akışının aksine **senkron ve her zaman başarılı**
— M5'teki "gerçekçi mock" zorunluluğu özellikle ödemenin SUCCEEDED anı için geçerliydi (idempotency/imza kod
yolunun test edilmemesi riski); iade onayında eşdeğer bir risk yok, ayrı bir async webhook akışı kurulmadı.
Manuel iade doğrulaması yalnızca dokümanın tek zorunlu kuralını (`toplam iade ≤ ödenen tutar`) uyguluyor — bir
kalemin birden fazla iadede toplamda kaç kez/adet iade edildiğini ayrıca takip eden genel bir "tüketim" mekanizması
bilinçli olarak kurulmadı (dokümanda belirtilmiyor, aşırı mühendislik olurdu).

**Bilinen notlar (canlı doğrulamada bulunup düzeltilen hata):** staff-web'in iade adedi input'ları
`defaultValue` (React'te kontrolsüz) ile öneri adedini gösteriyordu, ama gönderim state'i (`quantityInputsRef`)
yalnızca `onChange` ile güncelleniyordu — kullanıcı önerilen değeri hiç değiştirmeden "İade Başlat"a basınca ref
boş kalıp "en az bir kalem girin" hatası veriyordu, oysa ekranda adet görünüyordu. Düzeltme: sipariş arandığında
ref, ekranda gösterilen aynı öneri değerleriyle elle başlatılıyor. Receipt'te KDV/vergi kırılımı yok (fiyatlar
zaten KDV dahil gösteriliyor - Bölüm 5); bu yasal bir fatura olmadığından (Bölüm 7/12) kasıtlı bir sadeleştirme.

---

## Milestone 8 — StaffUser Auth, Roller/Permission, Admin Ekranları, Audit — ✅ COMPLETED

**Ana özellikler:** Yeni `staffaccess` modülü — gerçek `StaffUser` (email+bcrypt şifre), `StaffSession`
(cookie-tabanlı, `qrmenu_staff_session`, TTL 6 saat, M6'daki paylaşılan `X-Staff-Access-Token`'ın tam yerine
geçti), `StaffUserBranch` (BRANCH_MANAGER/KITCHEN_STAFF için çoklu-şube scoping), `StaffRole` enum
(PLATFORM_ADMIN/BUSINESS_ADMIN/BRANCH_MANAGER/KITCHEN_STAFF) → `Set<Permission>` eşlemesi (rol-adı kontrolü
değil, permission kontrolü). Yeni `audit` modülü (leaf, hiçbir modüle bağımlı değil) — `AuditService.record(...)`
her mutasyonun sonunda çağrılıyor. `KitchenController`/`RefundController` gerçek auth'a bağlandı
(`Permission.KITCHEN_DECIDE`/`REFUND_ISSUE`, branch-scoped). Yeni `/api/staff/**` admin uçları: Branch/Table/QR
yönetimi (`TenantService`'e `listBranches/listTables/setOrderingEnabled` eklendi, QR revoke + ordering-toggle
audit'e yazıyor), Menü yönetimi (`MenuService`'in tüm mutasyon metotları artık `actorStaffUserId` alıp audit
kaydı yazıyor), Personel/Rol yönetimi, Audit log görünümü. staff-web tamamen yeniden yazıldı: gerçek e-posta/şifre
giriş sayfası, `StaffNav` (role'e göre admin linkleri), `/branches`, `/branches/[branchId]`, `/menu`, `/staff`,
`/audit` yeni admin ekranları; KDS + iade ekranları artık `credentials:'include'`/`EventSource
{withCredentials:true}` ile cookie tabanlı.

**Kapsam kararı:** `spring-boot-starter-security` kullanılmadı (yalnızca `spring-security-crypto` →
`BCryptPasswordEncoder`) — M6'dan beri süregelen "açık cookie oku + servise sor" deseniyle tutarlı, framework
auto-config'inin SPA'nın kendine özgü ihtiyaçlarıyla (her controller'da branch-scope kontrolü gibi) çakışmasını
önlemek için. `/api/kitchen/**` altındaki KDS/refund URL'leri, onları oraya taşıyan asıl neden (tek path-wildcard
sınırı, M6) artık geçerli olmasa da değiştirilmedi — çalışan, test edilmiş bir URL'yi kozmetik nedenle değiştirmek
gereksiz churn olurdu.

**Teknik kararlar:** `ModuleBoundaryTest`'e `staffaccess`/`audit` için repository-erişim kuralları eklendi (8
kural, hepsi geçiyor). Testler artık gerçek `StaffUser` bootstrap+login akışını kullanıyor (`StaffFixtures` —
`/internal/.../staff-users` + `POST /api/staff/auth/login`, dönen `Set-Cookie`'den session id çıkarılıyor) —
`AbstractIntegrationTest`'teki artık ölü `staff.access.token`/`TEST_STAFF_ACCESS_TOKEN` temizlendi. Backend test
sayısı 54 → 62 (yeni: `StaffAccessFlowIntegrationTest` 5, `ModuleBoundaryTest` +2, `Kitchen`/`RefundFlow`
testleri gerçek login'e taşındı).

**Bilinen notlar (canlı Docker Compose + Chrome doğrulamasında bulunup düzeltilen gerçek hata):** KDS/mutfak
takip SSE bağlantıları (`SseOrderStatusNotifier`), ilk abonelikten sonra hiçbir sipariş olayı olmazsa süresiz
"bağlanıyor" durumunda kalıyordu — Tomcat, `SseEmitter`'ın async response'unu ilk gerçek `send()` çağrısına kadar
hiç flush etmiyordu, bu yüzden ne `curl` ne de tarayıcının `EventSource`'u hiçbir byte/`open` eventi görmüyordu
(bu, M8'in kendi değişikliği değil, M6'dan beri var olan gizli bir hataydı — müşteri tarafı order-tracking
stream'i de aynı şekilde etkileniyordu, ancak önceki canlı testler her zaman abone olur olmaz bir sipariş olayı
tetiklediği için fark edilmemişti). Düzeltme: `SseOrderStatusNotifier.register` artık abone olur olmaz boş bir
SSE yorum satırı (`: connected`) gönderip response'u hemen flush ediyor.

---

## Milestone 9 — Pickup Board, Teslimat Modelleri, Security Hardening — ✅ COMPLETED

**Ana özellikler:** `Branch.deliveryModel` (`CUSTOMER_PICKUP`/`WAITER_DELIVERY`, varsayılan `WAITER_DELIVERY` —
mevcut tüm şubelerin zımni davranışı, migration geriye dönük hiçbir şubeyi bozmuyor), staff-web'de oluşturma
formunda + `/branches` ekranında sonradan değiştirilebilir. `OrderStatus.COMPLETED` (Bölüm 6: "READY ->
COMPLETED: teslim edildi / alındı") ilk kez eklendi — `POST /api/kitchen/branches/{branchId}/orders/{orderId}/complete`
(yeni `Permission.ORDER_COMPLETE`, BUSINESS_ADMIN+BRANCH_MANAGER), staff-web'in iade/sipariş arama ekranına
"Teslim Edildi / Alındı" butonu olarak eklendi. Yeni public/kimliksiz `GET /api/branches/{branchId}/pickup-board`
+ `.../stream` (SSE) — yalnızca CUSTOMER_PICKUP şubenin READY siparişlerinin numaralarını döner (fiyat/ürün detayı
yok), staff-web'de kiosk-modu `/pickup/[branchId]` ekranı (StaffNav yok, login gerektirmez — public menü
endpoint'iyle aynı güven modeli). Müşteri tarafı `/order/track/{token}` artık `deliveryModel` alanını da
döndürüyor ve müşteriye "hazır olduğunda pickup ekranında görünecek" / "masanıza getirilecek" gibi bağlama uygun
bir mesaj gösteriyor.

**Hata senaryoları (Bölüm 9'un açıkça istediği "webhook timeout/retry"):** Ödeme başarısız + webhook idempotency/
retry senaryoları zaten Milestone 5'te test edilmişti (`PaymentFlowIntegrationTest`), bu milestone'da dokunulmadı.
Eksik olan tek şey `PaymentStatus.PROCESSING -> EXPIRED` (Bölüm 6) geçişiydi: hiç webhook almayan bir ödeme
(gerçek sağlayıcı kesintisi, ya da mock akışta müşterinin sekmeyi kapatıp `/mock-outcome`'u hiç çağırmaması)
süresiz PROCESSING'de kalıp o TableVisit'i kilitli bırakıyordu. Yeni `PaymentTimeoutScheduler`
(`OrderCleanupScheduler` ile aynı "backdate + scheduled metodu doğrudan çağır" test deseni), 15 dakika sonra
PROCESSING'i EXPIRE edip siparişi PAYMENT_FAILED'e (yeniden denenebilir) döndürüyor — tam olarak FAILED
webhook'unun tepkisiyle aynı. `PaymentWebhookService`'in "zaten terminal durumda" güvenlik ağına EXPIRED de
eklendi (geç gelen bir webhook artık PROCESSING-only state guard'ına çarpıp 500 vermek yerine sessizce no-op).

**Yeni: `RateLimitFilter` (Bölüm 7 MVP kapsamının "session/masa bazlı rate limiting" maddesi, M1-M8 boyunca hiç
uygulanmamış bir kalemdi — M9'un "security hardening" görevi kapsamında kapatıldı).** In-memory, tek katmanlı
sabit-pencere sayaç (Redis yok, Bölüm 12: "yatay ölçekleme v1 dışı" ile aynı tek-instance varsayımı,
`SseOrderStatusNotifier` gibi). Üç anahtar katmanı, öncelik sırasıyla: (1) session cookie
(`qrmenu_session`/`qrmenu_staff_session`) — limit 120/dk, tek bir sekmenin bunu aşması organik kullanım değildir;
(2) QR check-in path'inden çıkarılan masa token'ı — limit 30/dk, dokümanın tam olarak "masa bazlı" dediği şey;
(3) IP adresi — limit 300/dk, kasıtlı olarak cömert, çünkü bir restoranın paylaşılan WiFi/NAT'ı altında onlarca
*farklı* meşru müşteri aynı IP'yi paylaşabilir (yalnızca gerçekten tek-kaynaklı bir saldırıyı yakalamak için var,
yoğun bir öğle servisini cezalandırmak için değil). `/internal/**`, `/actuator/**`, SSE `*/stream` uçları ve CORS
preflight (`OPTIONS`) muaf.

**RLS yeniden değerlendirmesi (Bölüm 9, Milestone 9'un açık maddesi) — karar: hâlâ ertelensin.** Section 2'nin
"her repository sorgusu business_id'yi açıkça WHERE koşuluna ekler" disiplini + `TenantIsolationIntegrationTest`/
`MenuIsolationIntegrationTest`'in kapsadığı çapraz-tenant erişim testleri, tek-instance/tek-deploy MVP için yeterli
savunma derinliği sağlıyor. Gerçek Postgres RLS eklemek: (a) her connection için bir session GUC'u
(`app.current_business_id` gibi) set etmeyi gerektirir - HikariCP'nin connection pooling'i ile bunu doğru
sıfırlamak (bir connection tekrar havuza dönmeden önce) başlı başına bir kaynak; (b) Hibernate'in ürettiği
sorgularla RLS policy'lerinin etkileşimini (özellikle `IN`/join sorguları) ayrıca doğrulamak gerekir; (c) bugüne
kadar hiçbir çapraz-tenant sızıntısı ne testlerde ne canlı doğrulamada bulunmadı. Bu iş, gerçek bir çoklu-kiracılı
production dağıtımı (paylaşılan DB, güvenilmeyen operatör erişimi) somutlaştığında yeniden değerlendirilmeli;
bugünkü tek-işletme/tek-deploy kullanım şekli için ek karmaşıklığı haklı çıkarmıyor (Bölüm 12 ruhuyla tutarlı).

**Güvenlik gözden geçirme (bulgular):** ✅ Şifreler bcrypt (`BCryptPasswordEncoder`); ✅ tüm session cookie'leri
HttpOnly+Secure+SameSite=Lax; ✅ CORS açık origin listesi + `allowCredentials(true)`, wildcard yok; ✅ tek native
SQL sorgusu (`OrderNumberGenerator`) tamamen parametreli, string concatenation yok; ✅ `/internal/**` bootstrap
API'si CORS'a hiç maruz değil (tarayıcıdan çağrılamaz); ✅ `/actuator` yalnızca health+info expose ediyor (env/beans
yok); ✅ üç Docker image'ı da non-root user ile çalışıyor; ✅ sırlar (`INTERNAL_ADMIN_TOKEN`,
`PAYMENT_MOCK_WEBHOOK_SECRET`) prod'da boş varsayılanla "fail closed", yalnızca `application-local.yml`'de dev
değerleri var, `infra/.env` gitignore'da. ⚠️ **Bilinen, kasıtlı olarak ertelenen açık:** `Secure` cookie bayrağı
prod'da gerçek TLS/HTTPS termination gerektirir - reverse proxy olmadan bir dağıtım cookie'lerin hiç set
edilmediğini (login sessiz şekilde çalışmaz) görür; bu deploy hazırlığı notuna taşındı, kod değişikliği değil.

**Deploy hazırlığı notları (kod değişikliği gerektirmeyen, operasyonel):** Prod dağıtımı önünde bir TLS-terminating
reverse proxy (nginx/Caddy/Cloud LB) şart - yukarıdaki Secure-cookie bağımlılığı yüzünden. `INTERNAL_ADMIN_TOKEN`
ve `PAYMENT_MOCK_WEBHOOK_SECRET` prod'da `infra/.env`'deki dev değerleriyle **asla** dağıtılmamalı - her ortam
için ayrı, rastgele üretilmiş değerler gerekir. Flyway migration'ları (`V1`-`V11`) her ortamda otomatik
uygulanıyor (`spring.flyway` varsayılanları) - prod'a ilk deploy'da bunun manuel/CI adımı olarak doğrulanması
önerilir. Tek-instance varsayımı (SSE pub/sub, rate limiter, scheduler'lar hepsi in-memory) yatay ölçeklenemez -
Bölüm 12'nin bilinçli v1 sınırı, birden fazla backend instance'ı çalıştırmak (örn. blue-green deploy'un ötesinde
kalıcı çoklu-instance) bu üçünü de kırar.

**Backend test sayısı 62 → 66** (yeni: `PaymentTimeoutSchedulerIntegrationTest` 1, `RateLimitFilterTest` 2,
`PickupToCompletionEndToEndTest` 1 — QR taramadan pickup board'a, oradan "teslim edildi" işaretlemesine kadar
tüm zinciri tek testte kapsayan gerçek bir E2E).

---

## Gap-Analysis #1 — Kasa Kabul/Red Kapısı — ✅ COMPLETED

`docs/gap-analysis.md`'nin CONFLICTING #1 maddesi (ödeme sonrası otomatik mutfağa düşme) kapatıldı. `OrderStatus`
değişti: `PAID` kaldırıldı, `AWAITING_STORE_ACCEPTANCE` + `REJECTED_BY_STORE` eklendi (V12 migration - `status`
kolonu VARCHAR(20)→VARCHAR(30), yeni `rejection_reason_code`/`rejection_note` kolonları). Ödeme webhook'u artık
siparişi `AWAITING_STORE_ACCEPTANCE`'a düşürüyor (`OrderingService.markOrderAwaitingStoreAcceptance`), mutfağa
gitmek için yeni `OrderControlController` (`/api/staff/branches/{branchId}/orders/{pending-acceptance,{id}/accept,
{id}/reject}`) üzerinden kasa ACCEPT'i gerekiyor. REJECT, `RefundService.requestFullRefund` ile tam iadeyi tetikliyor
- orkestrasyon bilinçli olarak controller katmanında (OrderingService içinde değil), Milestone 7'nin
`ordering→refund→payment→ordering` döngü kaçınma kararıyla aynı gerekçeyle. Yeni `StaffRole.CASHIER` +
`Permission.ORDER_VIEW/ORDER_ACCEPT/ORDER_REJECT` (BUSINESS_ADMIN/BRANCH_MANAGER da alır); CASHIER, BRANCH_MANAGER/
KITCHEN_STAFF gibi explicit branch ataması gerektiriyor. `getKitchenQueue` değişmedi - IN_KITCHEN artık yalnızca
ACCEPT üzerinden ulaşılabildiği için otomatik olarak "yalnızca kasa-kabullü siparişler" haline geldi.

Mevcut 4 entegrasyon testi (Payment/Kitchen/Refund/E2E) ödeme-sonrası-otomatik-mutfak varsayımına göre yazılmıştı;
hepsi kasa ACCEPT adımını (veya PaymentFlowIntegrationTest için yalnızca beklenen durumu
`AWAITING_STORE_ACCEPTANCE`'a) güncellenerek düzeltildi. Yeni `OrderControlFlowIntegrationTest` (3 test): ACCEPT
akışı, CASHIER/KITCHEN_STAFF permission sınırları, REJECT'in tam iadeyi otomatik tetiklediği ve iki kez
reddedilemeyeceği. customer-web'in sipariş takip sayfasına `AWAITING_STORE_ACCEPTANCE`/`REJECTED_BY_STORE` için
minimal etiket/mesaj eklendi (ham enum string göstermemek için) - "müşteri bildirim durumlarını genişletme"
kapsamının geri kalanı (gap-analysis sıradaki madde) ayrı bir aşama.

**Backend test sayısı 66 → 69** (yeni: `OrderControlFlowIntegrationTest` 3).

---

## Gap-Analysis #4 ve #6 — Branch Çalışma Saatleri + Müşteri Bildirim Durumları — ✅ COMPLETED

**#4 (PARTIAL/CONFLICTING → çözüldü):** `Branch.openingTime/closingTime` tek çifti kaldırıldı, yerine yeni
`BranchBusinessHours(businessId, branchId, dayOfWeek, openingTime, closingTime, closed)` tablosu geldi - haftanın
her günü ayrı satır (V13 migration, mevcut şubelerin tek çifti varsa 7 güne otomatik kopyalandı, veri kaybı yok).
Bir gün için satır yoksa o gün kısıtlamasız kalır (eski nullable-alan davranışıyla aynı varsayılan - hiç saat
girmemiş şubeler bozulmadı). `closed=true` saatlerden bağımsız o günü tamamen kapatır.
`orderingEnabled` zaten "geçici kapat/aç override" ihtiyacını karşılıyor - ikinci bir alan eklenmedi (Bölüm 26,
gereksiz karmaşıklık). Ayrıca Bölüm 12.2'nin aynı madde grubundan `Branch.address` (opsiyonel) eklendi. Yeni
`TenantService.setBranchBusinessHours` (haftayı tek seferde replace-all yazıyor) + `getBranchBusinessHours`, yeni
`/api/staff/branches/{branchId}/business-hours` (GET/POST) ve `/address` (POST) uçları. `assertOrderingCurrentlyAllowed`
artık günün `BranchBusinessHours` satırını okuyor (overnight-wrap mantığı aynen korundu).

**#6 (PARTIAL → çözüldü):** `OrderTrackingResponse`'a `latestRefundStatus` eklendi (en son `Refund`'un durumu,
yoksa null) - `OrderTrackingController` artık `RefundService`'e de bağımlı (yalnızca controller katmanında,
`ordering`→`refund` döngüsü yaratmıyor, `OrderControlController`'la aynı gerekçe). customer-web'in takip sayfasına
tüm `OrderStatus` değerleri için etiket (DRAFT/AWAITING_PAYMENT/PAYMENT_FAILED/CANCELLED dahil - artık hiçbir ham
enum string görünmüyor) ve refund durumuna göre dinamik mesaj ("İadeniz işleniyor" / "tamamlandı" / "başarısız
oldu, işletmeyle iletişime geçin") eklendi.

Yeni testler: `BranchBusinessHoursFlowIntegrationTest` (5 - saatsiz şube kısıtlamasız, `closed=true` her zaman
engeller, saat penceresi dışı/içi doğru davranıyor, staff kendi yazdığı saatleri geri okuyabiliyor) +
`OrderControlFlowIntegrationTest`'e `latestRefundStatus` doğrulaması eklendi. customer-web/staff-web build+lint
temiz.

**Backend test sayısı 69 → 74** (yeni: `BranchBusinessHoursFlowIntegrationTest` 5).

---

## Gap-Analysis — Kasa Dashboard (staff-web) — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #2 maddesi: yeni `staff-web` ekranı `/cashier/[branchId]` -
`Gap-Analysis #1`'de eklenen `OrderControlController`'ı kullanıcı arayüzüne bağlıyor. Onay bekleyen
(`AWAITING_STORE_ACCEPTANCE`) siparişleri kart olarak listeliyor, her sipariş için tekli "Kabul Et" (tüm sipariş) ve
"Reddet" (açılır red-nedeni formu: `OUT_OF_STOCK`/`KITCHEN_BUSY`/`CLOSED`/`OTHER` + opsiyonel not) aksiyonları var.
KDS/pickup board'la aynı desen: SSE'yi (`buildKitchenStreamUrl`, mevcut branch-kitchen kanalı) salt "bir şey
değişti, yeniden çek" sinyali olarak kullanıyor - yeni SSE altyapısı gerekmedi, kanal zaten her durum geçişinde
(`markOrderAwaitingStoreAcceptance`/`acceptOrder`/`rejectOrder`) event yayınlıyor. `StaffContext.role`/`StaffRole`
tipine `CASHIER` eklendi; Mutfak/İadeler ekranlarının header'larına karşılıklı "Kasa" linki eklendi (üçü de artık
birbirine bağlı).

**Canlı doğrulama (Chrome + gerçek Docker Compose stack'i, `AWAITING_STORE_ACCEPTANCE` durumunda gerçek bir sipariş
üzerinden):** Kabul Et → sipariş kasa listesinden düştü, mutfak panosunda `IN_KITCHEN`/`PENDING_REVIEW` olarak
doğru şekilde belirdi. Reddet (KITCHEN_BUSY + not) → sipariş kasa listesinden düştü; DB doğrulaması `status=
REJECTED_BY_STORE`, `rejection_reason_code`/`rejection_note` doğru kaydedilmiş, ve otomatik tam iade (`Refund.status
=COMPLETED`, tutar sipariş toplamıyla birebir) oluşmuş. Bu doğrulama sırasında ayrı bir gerçek hata da bulundu ve
düzeltildi (bkz. altında) - fonksiyonel kod hatası değil, canlı ortama özgü bir migration sıralama hatasıydı.

**Bilinen not / bulunup düzeltilen gerçek hata (V12 migration, canlı Docker Compose'da):** `V12` dosyasında `UPDATE
customer_order SET status='AWAITING_STORE_ACCEPTANCE' WHERE status='PAID'` ifadesi, eski `CHECK` kısıtı henüz
düşürülmeden önce çalıştırılmıştı - taze Testcontainers DB'sinde (hiç `PAID` satırı yok) bu sessizce no-op kaldığı
için testler yakalayamadı, ama kalıcı local dev Postgres'inde (önceki manuel test oturumlarından kalma bir `PAID`
satırı vardı) hem eski kısıt UPDATE'i reddetti hem de (ilk düzeltme denemesinde) yeni kısıt henüz eklenmemişken satır
zaten yanlış değere sahipti. Doğru sıra: kolonu genişlet → **eski kısıtı düşür** → veri taşı (`UPDATE`) → **yeni
kısıtı ekle**. `flyway_schema_history`'de V12 hiç `success=true` olarak görünmediği için (transactional DDL, hatalı
migration temiz rollback oluyor) aynı dosyayı yeni bir versiyon numarası açmadan düzeltmek güvenliydi. Bu, salt
Testcontainers'a güvenmenin (her test çalıştırmasında sıfırdan, "temiz" bir DB) neden mevcut veriyle canlı doğrulamanın
yerini tutamayacağının somut bir örneği - proje boyunca süregelen bir prensip (bkz. Milestone 6/8/9 canlı bulgular).

Backend test sayısı değişmedi (74) - bu aşama salt staff-web frontend + mevcut backend uçlarının canlı doğrulaması,
yeni backend davranışı yok. customer-web/staff-web build+lint zaten temizdi.

---

## Gap-Analysis — Product Alanları (allergens/estimatedPreparationMinutes/active) — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #5 maddesi (Bölüm 3.2). `Product`'a üç yeni alan eklendi (V14
migration): `active` (varsayılan `true`, mevcut ürünler bozulmadı), `estimated_preparation_minutes` (nullable
Integer), ve `allergens` - kendi repository/entity'si olmayan bir `@ElementCollection` (`product_allergen` value
tablosu), her zaman sahip `Product` satırı üzerinden okunuyor, modül-içi, ArchUnit sınırı gerekmiyor. `Allergen`
enum'u AB'nin 14 zorunlu alerjen listesini kullanıyor (Bölüm 3.2: "yapılandırılmış enum/reference listesi, serbest
metin olmamalı").

`active=false`, BranchProduct opt-in'den **bağımsız, işletme seviyesinde bir kill-switch** - bir ürün pasife
alındığında her şubenin menüsünden kayboluyor, o şubede hâlâ bir BranchProduct/AVAILABLE satırı olsa bile
(`PublicMenuController`, opt-in filtresine `product.isActive()` eklendi). Aynı disiplin sepete ekleme akışında da
var: `OrderingService.addItem` artık `product.isActive()` kontrolü yapıyor (Milestone 4'ün "backend-authoritative
revalidation" ilkesiyle aynı - müşteri menüde göremese bile eski/önbelleklenmiş bir sayfadan sipariş denerse yine
reddedilir). Yeni `MenuService.updateProductDetails` + staff-web'de `PATCH /api/staff/products/{id}` (yeni
endpoint - önceden ürün için hiçbir update yolu yoktu, yalnızca create) bu üç alanı düzenliyor; `/menu` ekranına
her ürün satırına "Pasif Yap/Aktif Yap" hızlı toggle'ı + "Düzenle" (hazırlık süresi input'u + 14 alerjen
checkbox'ı) paneli eklendi. customer-web'in `ProductCard`'ına hazırlık süresi + "İçerir: ..." satırı eklendi
(Bölüm 19: "product image/name/description/allergen/prep time/fiyat" kalite kriteri).

**Canlı doğrulamada bulunup düzeltilen gerçek hata (aynı sınıf, Milestone 4'teki CORS bug'ıyla birebir aynı kök
neden):** Yeni `PATCH` endpoint'i tarayıcıdan çağrıldığında CORS preflight (`OPTIONS`) 403 dönüyordu -
`CorsConfig.allowedMethods` listesi hâlâ `GET/POST/PUT/DELETE`, `PATCH` hiç yoktu. MockMvc testleri gerçek CORS
filtresinden geçmediği için (tarayıcı yok) bunu yakalayamadı - tam olarak Milestone 4'ün notunda belirtilen
"yeni HTTP metodu eklenen her milestone'da gerçek tarayıcı doğrulaması atlanmamalı" uyarısının kendisi. Düzeltme:
`allowedMethods`'a `PATCH` eklendi. Canlı doğrulama: staff-web'de bir ürünün hazırlık süresi/alerjenleri
güncellendi (kaydedildi, satırda doğru göründü), `Pasif Yap` ile ürün customer-web menüsünden tamamen kayboldu
(“Menü hazırlanıyor” boş-durum ekranı), `Aktif Yap` ile geri geldi.

**Backend test sayısı 74 → 77** (yeni: `PublicMenuIntegrationTest` +1 - pasif ürün BranchProduct satırı olsa bile
menüden düşüyor; `CartFlowIntegrationTest` +1 - pasif ürün AVAILABLE olsa bile sepete eklenemiyor;
`StaffAccessFlowIntegrationTest` +1 - staff PATCH ile ürünü pasife alabiliyor ve değişiklik kalıcı oluyor).
customer-web/staff-web build+lint temiz.

---

## Gap-Analysis #6 — Business/Branch Ayarları (currency/timezone + BusinessContact) — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #6 maddesi (Bölüm 12.1/12.2/12.3). `Business`'a `defaultCurrency`/
`defaultTimeZone` eklendi (V15 migration, varsayılan `TRY`/`Europe/Istanbul` - mevcut işletmeler bozulmadı);
bunlar salt görüntüleme/rapor fallback'i, `Money`'nin tek-para-birimli tasarımına (Bölüm 5, onaylı karar)
dokunmuyor, hiçbir yerde para hesaplamasına karışmıyor. `Branch`'e opsiyonel `timezone` override'ı eklendi (null
ise tüketen taraf `Business.defaultTimeZone`'a düşer - henüz hiçbir tüketici yok, raporlama modülüyle birlikte
gelecek). Yeni `BusinessContact` entity/repository/CRUD (Bölüm 12.3: name/phone/email/whatsappEnabled/
dailyReportRecipient/monthlyReportRecipient/active) - şimdilik salt veri, hiçbir bildirim/rapor modülü henüz
tüketmiyor (o roadmap maddeleriyle birlikte gelecek).

Hem currency (ISO 4217, `java.util.Currency`) hem timezone (IANA, `java.time.ZoneId`) girişleri entity
seviyesinde doğrulanıyor - geçersiz kod/kimlik 400 olarak reddediliyor. Yeni `Permission.BUSINESS_SETTINGS_MANAGE`
(yalnızca `BUSINESS_ADMIN`'e atandı) tüm yeni uçları koruyor: `GET/POST /api/staff/business(/settings)`,
`POST /api/staff/branches/{id}/timezone`, `GET/POST /api/staff/business/contacts`,
`PUT /api/staff/business/contacts/{id}`. staff-web'de yeni `/business-settings` ekranı (nav'da "İşletme
Ayarları") + şube detay sayfasına saat dilimi alanı eklendi.

**Backend test sayısı 77 → 84** (yeni: `BusinessSettingsFlowIntegrationTest`, 7 - varsayılan ayarlar, güncelleme+
geri okuma, geçersiz currency/timezone reddi, izinsiz erişim 403, şube saat dilimi set/temizle, geçersiz şube saat
dilimi reddi, contact create/list/update). staff-web build+lint temiz.

**Not (bu oturumun kuralı):** Bu madde canlı tarayıcı doğrulaması olmadan tamamlandı - kullanıcı bu oturumdan
itibaren Chrome üzerinden test yapılmamasını istedi (bkz. proje hafızası). Doğrulama yalnızca backend integration
testleri + `npm run build`/`lint` ile yapıldı; önceki milestone'larda birkaç kez gerçek tarayıcıda CORS/preflight
gibi hataların testlerden kaçtığı not edilmişti (bkz. yukarıdaki Product Alanları notu) - bu sınıf bir regresyon
bu PR'da mümkün (örn. yeni `PATCH`/`PUT` metodu yok, mevcut `CorsConfig.allowedMethods` zaten `PUT` içeriyor, ama
gerçek tarayıcı doğrulaması yapılmadığı açıkça belirtilsin diye).

---

## Gap-Analysis #7 — Toplu Menü Atama + Zincir Karşılaştırma + StaffAnnouncement — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #7 maddesi. Kapsam bilinçli olarak finansal-olmayan verilerle
sınırlandırıldı (Bölüm 13.2'nin tam ciro/refund karşılaştırması gelecekteki Raporlama modülüne - gap-analysis
#8 - bırakıldı); StaffAnnouncement tamamen manuel, ayrı bir özellik olarak kuruldu (toplu menü atamasının
otomatik tetiklediği bir bildirim değil).

**Ana özellikler:**
- **Toplu menü atama:** `MenuService.bulkAssignProductToBranches` - "tüm şubelere ata" (`TenantService.
  listBranches` ile çözülür) veya "seçili şubelere ata", mevcut `upsertBranchProduct`'ı şube başına çağırıyor
  (yalnızca availability=AVAILABLE, fiyat override'sız - Bölüm 5'in opt-in modeli korunuyor, bypass edilmiyor).
  Yeni `POST /api/staff/products/{id}/branch-assignments` (`Permission.MENU_MANAGE`). `/menu` ekranına her ürün
  satırına "Şubelere Ata" paneli eklendi (tüm/seçili radio + şube checkbox'ları).
- **StaffAnnouncement (Bölüm 18.1):** yeni `com.qrmenu.announcement` modülü (entity/repository/service/
  controller, V16 migration - `staff_announcement` + `staff_announcement_branch` element-collection tablosu).
  `AnnouncementService.create/listForBusiness/endNow/listActiveFor` - hedef `ALL_BRANCHES`/`SELECTED_BRANCHES`,
  opsiyonel `expiresAt` (erken sonlandırma için `endNow` de var). Yeni `Permission.ANNOUNCEMENT_MANAGE`
  (yalnızca `BUSINESS_ADMIN`). `GET/POST /api/staff/announcements`, `POST /api/staff/announcements/{id}/end`
  bu permission'la korunuyor; `GET /api/staff/announcements/active` ise permission'sız, herhangi bir oturum
  açmış personel görebiliyor (banner'ın her rol için çalışması için) - görünürlük `StaffContext.
  canAccessBranch` ile filtreleniyor (admin hepsini görür, şube-scope'lu roller yalnızca ALL_BRANCHES + kendi
  şubesini kapsayan SELECTED_BRANCHES duyurularını görür). Yeni `/announcements` ekranı (admin-only nav) +
  `AnnouncementBanner` bileşeni `StaffNav` içinde her role gösteriliyor, kapatma yalnızca `localStorage`'da
  tutuluyor (sunucu tarafı okunma takibi yok - kasıtlı minimal kapsam).
- **Zincir/şube karşılaştırma (Bölüm 13.2, finansal-olmayan):** yeni ince `com.qrmenu.chain` modülü - kendi
  persistence'ı yok, salt-okunur `ChainComparisonService` mevcut `TenantService.listBranches` +
  `OrderingService.countOrdersSince` (yeni, DRAFT/CANCELLED hariç) + `CustomerSessionService.
  countTableVisitsSince` (yeni) metotlarını son 24 saatlik sabit pencerede birleştiriyor. Yeni
  `GET /api/staff/branches/comparison` (`Permission.BRANCH_MANAGE`). Yeni `/chain-comparison` ekranı (admin-only
  nav): şube × {sipariş sayısı, masa ziyareti sayısı} tablosu.

**Teknik kararlar:**
- Bulk assign ve StaffAnnouncement için ayrı `BranchAssignmentTarget`/`AnnouncementTarget` enum'ları (aynı
  ALL_BRANCHES/SELECTED_BRANCHES şekli ama modüller arası paylaşılan bir soyutlama yok - YAGNI, iki modülün
  birbirinden habersiz kalması tercih edildi).
- `ChainComparisonService`'in kendi repository'si yok; yalnızca üç modülün zaten public facade'lerini (
  TenantService/OrderingService/CustomerSessionService) besliyor - ModuleBoundaryTest'e yeni bir case gerekmedi,
  yalnızca `announcement.repository` için yeni bir case eklendi.
- `OrderingService.countOrdersSince` DRAFT ve CANCELLED durumlarını hariç tutuyor (terk edilmiş sepetler
  "sipariş" sayılmıyor) - CustomerSessionService.countTableVisitsSince ise ham TableVisit sayısı (finansal
  olmayan bir "trafik" metriği, bilinçli olarak filtresiz).

**Backend test sayısı 84 → 95** (yeni: `BulkAssignBranchesFlowIntegrationTest` 4, `AnnouncementFlowIntegrationTest`
4 - create/list/end, SELECTED_BRANCHES görünürlük filtresi şube-scope'lu role göre, boş branchIds reddi, izinsiz
erişim 403 ama /active açık kalıyor -, `ChainComparisonFlowIntegrationTest` 2, `ModuleBoundaryTest` +1 yeni
`announcement.repository` case'i). staff-web build+lint temiz.

**Not:** Bu madde de canlı tarayıcı doğrulaması olmadan tamamlandı (bkz. proje hafızası - Chrome testi bu
projede kapalı); doğrulama backend integration testleri + `npm run build`/`lint` ile yapıldı.

---

## Gap-Analysis #8 — Raporlama Modülü: Temel Metrikler + Zincir Görünümü — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #8 maddesi (product-requirements.md Section 13.1/13.2). Kapsam
bilinçli olarak Bölüm 13'e (satış raporlama) sınırlandırıldı - gün sonu snapshot/Excel export (Bölüm 14, gap-
analysis #9) ve gider yönetimi (gap-analysis #10) bu maddenin dışında bırakıldı.

**Ana özellikler:**
- **`com.qrmenu.reporting` modülü:** kendi persistence'ı yok - `ChainComparisonService` (gap-analysis #7) ile
  aynı desen, `OrderingService`/`RefundService`/`MenuService`/`TenantService`/`CustomerSessionService`'in zaten
  public facade metotlarını besliyor. `ReportingService.getBranchReport`/`getChainReport` bir `[from, to]`
  business-date aralığını şubenin kendi timezone'unda (Bölüm 4/gap-analysis #6'nın `Branch.timezone`'u,
  yoksa UTC) Instant sınırlarına çeviriyor.
- **Gross/net/refund matematiği (Bölüm 13.4):** gross = `OrderingService.findOrdersForReport`'un döndürdüğü
  "ödemesi başarılı" statülerdeki (`AWAITING_STORE_ACCEPTANCE`/`IN_KITCHEN`/`READY`/`COMPLETED`/
  `REJECTED_BY_STORE` - hepsi yalnızca başarılı bir webhook sonrası ulaşılabilir statüler)
  `CustomerOrder.totalMinorUnits` toplamı; refund = `RefundService.sumCompletedRefundAmount`'ın döndürdüğü
  tamamlanmış refund toplamı; net = gross - refund. Reddedilen bir sipariş gross'a girip tam refund'la
  netlenerek geri çıkıyor - canlı Product fiyatına değil, immutable Order/OrderItem snapshot'ına dayanıyor.
- **Ürün/kategori kırılımı:** yalnızca `OrderItem.acceptedQuantity > 0` olan kalemlerden (mutfağın gerçekten
  kabul ettiği adet - sipariş edilen değil), `productNameSnapshot` kullanılıyor (menu modülüne bağımlılık
  yok); kategori adı için tek yeni cross-module çağrı `MenuService.getProductsByIds` (+ mevcut
  `getCategoriesForBusiness`).
- **Yeni `Permission.REPORT_VIEW`** (CASHIER/BRANCH_MANAGER/BUSINESS_ADMIN, şube-scope'lu -
  `resolveStaffContextForBranch` ile) ve **`Permission.REPORT_CHAIN_VIEW`** (yalnızca BUSINESS_ADMIN/
  PLATFORM_ADMIN). `GET /api/staff/branches/{branchId}/reports` ve `GET /api/staff/reports/chain`
  (`?from=&to=` ISO tarih, seçilebilir aralık).
- **staff-web:** yeni `/reports` (admin-only nav, zincir toplamları + şube karşılaştırma tablosu, her satır
  şube detayına bağlanıyor) ve `/reports/[branchId]` (tarih aralığı formu, stat kartları, ürün/kategori/saatlik
  tablo) ekranları; `/branches` satır aksiyonlarına "Raporlar" linki (Kasa/Mutfak/İadeler ile aynı desen).

**Teknik kararlar:**
- Facade metotları status/business-logic'i kendi modülünde tutuyor (`OrderingService.findOrdersForReport`
  kendi `PAID_ORDER_STATUSES` sabitini kapsüllüyor - `countOrdersSince`'in izlediği aynı disiplin), reporting
  modülü yalnızca sonuçları birleştiriyor.
- Ortalama sepet = gross / orderCount (refund öncesi, standart AOV tanımı); "kaç kişi geldi" yerine mevcut
  `TableVisit` sayısı kullanılıyor (Bölüm 13.3'ün `guestCount` alanı hâlâ yok - gelecekteki iş).
- `ModuleBoundaryTest`'e yeni bir case gerekmedi (`chain` modülüyle aynı sebep - `reporting`'in kendi
  repository'si yok).

**Backend test sayısı 95 → 98** (yeni: `ReportingFlowIntegrationTest` 3 - gross/net/refund matematiği + ürün/
kategori kırılımı + saatlik dağılım, zincir agregasyonu + REPORT_CHAIN_VIEW admin-only gate + BRANCH_MANAGER'ın
kendi şubesini görüp diğerini görememesi, KITCHEN_STAFF'ın REPORT_VIEW'i olmadığı için 403 alması).

**Not:** Bu madde de canlı tarayıcı doğrulaması olmadan tamamlandı (bkz. proje hafızası - Chrome testi bu
projede kapalı); doğrulama backend integration testleri + `npm run build`/`lint` ile yapıldı.

## Gap-Analysis #9 — Gün Sonu Kapanış Raporu + Excel Export — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #9 maddesi (product-requirements.md Section 14). Kapsam Bölüm 14
(daily close snapshot + Excel) ile sınırlandırıldı - Bölüm 15 (sahibine otomatik bildirim, gap-analysis #11)
ve Bölüm 16 (gider yönetimi, gap-analysis #10) bu maddenin dışında bırakıldı.

**Ana özellikler:**
- **Yeni `com.qrmenu.dailyclose` modülü** (reporting/chain'in aksine kendi persistence'ı var):
  `DailyBranchCloseReport` bir branch+businessDate için tek satır (unique constraint), `ReportingService.
  getBranchReport` (gap-analysis #8) `from=to=businessDate` ile beslenerek gross/net/refund/orderCount/
  acceptedOrderCount/rejectedOrderCount/averageOrderValue/tableVisitCount hesaplıyor - iki modül asla farklı
  matematik kullanmıyor. `guestCount` alanı yok (#8'deki aynı gerekçe - veri kaynağı henüz yok).
- **PREVIEW/FINAL yaşam döngüsü:** `DailyCloseService.generatePreview` her çağrıda güncellenebilir (FINAL
  yoksa); `generateFinal` **immutable** - satır zaten FINAL ise yeniden hesaplamadan aynı satırı döner. Section
  14.3'ün "Excel source of truth değil, DB'den yeniden üretilebilir" ilkesiyle uyumlu: bir FINAL satır bir kez
  raporlandıktan sonra hiçbir zaman değişmiyor.
- **`DailyCloseScheduler`** (`@Scheduled`, 5 dakikada bir): her branch için o günkü `BranchBusinessHours.
  closingTime`'ına göre `closingTime-10dk`'da PREVIEW, `closingTime+5dk`'da FINAL üretiyor (Section 14.2).
  Eşikler `>=` ile kontrol ediliyor (dar bir pencere eşleşmesi değil) - kaçırılan bir poll döngüsü bir sonraki
  turda yakalanıyor. Saat tanımsız/kapalı günler atlanıyor - böyle bir şube manuel endpoint'le kapatılabiliyor.
  Sistem geneli şube taraması için `TenantService.listAllBranches()` eklendi (scheduler tek bir business'a
  değil tüm business'lara bakıyor).
- **Manuel endpoint'ler:** `GET .../daily-close` (liste), `POST .../daily-close/final` (elle FINAL tetikleme -
  ör. business hours yanlış girilmişse). Ayrı bir permission açılmadı, mevcut `REPORT_VIEW` yeniden kullanıldı.
- **Excel export (Apache POI, `poi-ooxml` 5.3.0):** `GET .../daily-close/excel` (branch) ve `GET /api/staff/
  daily-close/excel` (zincir, `REPORT_CHAIN_VIEW`) - her istekte DB'deki `DailyBranchCloseReport` satırlarından
  **anlık** üretiliyor, kalıcı dosya/object storage yok (henüz tüketen bir bildirim adaptörü olmadığından
  gereksiz karmaşıklık eklenmedi; spec'in "gerektiğinde DB'den yeniden üretilebilir" notuyla zaten uyumlu).
- **staff-web:** `/reports/[branchId]` sayfasına "Gün Sonu Kapanışları" bölümü (PREVIEW/FINAL rozetli liste,
  "Excel indir" ve bugün henüz FINAL değilse "Bugünü kapat" aksiyonları). `Badge` bileşenine yeni `success`
  tone'u eklendi (FINAL rozeti için) - `--color-success`/`--color-success-bg` token'ları `globals.css`'e
  eklendi.

**Teknik kararlar:**
- `dailyclose` kendi repository'sine sahip olduğu için `ModuleBoundaryTest`'e yeni bir case eklendi (tenant/
  ordering ile aynı desen - #7/#8'in "reporting/chain'in kendi repository'si yok" muafiyeti burada geçerli
  değil).
- Zone çözümleme (`branch.getTimezone() ?? UTC`) `ReportingService.resolveZone`'un birebir aynısı ama ayrı bir
  paylaşılan yardımcı çıkarılmadı - tek satırlık mantığı iki modül arasında paylaşmak, modül sınırını (private
  metoda erişim) ihlal etmeden mümkün değildi; kopyalamak gereksiz bir cross-module bağımlılıktan daha ucuz.

**Backend test sayısı 98 → 103** (yeni: `DailyCloseFlowIntegrationTest` 4 - manuel FINAL'in sipariş verisinden
doğru hesaplanması + FINAL'den sonra yeni siparişin sayıyı değiştirmemesi (immutability), KITCHEN_STAFF'ın
REPORT_VIEW'i olmadığı için 403 alması, Excel export'un POI ile geri okunabilir olması, scheduler'ın preview-
lead/final-grace eşiklerine uyması; `ModuleBoundaryTest`'e 1 yeni case).

**Not:** Bu madde de canlı tarayıcı doğrulaması olmadan tamamlandı (bkz. proje hafızası - Chrome testi bu
projede kapalı); doğrulama backend integration testleri + `npm run build`/`lint`/`tsc --noEmit` ile yapıldı.

## Gap-Analysis #10 — Gider Yönetimi — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #10 maddesi (product-requirements.md Section 16: Expense +
Recurring). Section 17 (Gelir/Gider ve Yönetimsel Kârlılık) de bu maddeye dahil edildi - gider verisi tek
başına anlamlı değil, doğal sonucu olan "Yönetimsel Net Sonuç" dashboard'u olmadan eksik kalırdı; gap-analysis
listesinde ayrı bir madde olarak numaralanmamıştı.

**Ana özellikler:**
- **Yeni `com.qrmenu.expense` modülü:** `ExpenseCategory` (business-scoped, manuel yönetim), `Expense`
  (`DRAFT`→`SUBMITTED`→`APPROVED`/`REJECTED`; `APPROVED`/`REJECTED` sonrası **immutable** - gün sonu kapanış
  raporunun FINAL kilidiyle aynı desen), `RecurringExpenseTemplate` (yalnızca `MONTHLY`, spec'in "ilk ihtiyaç"
  dediği tek değer). `branchId` nullable - business-level (şube bağımsız) gider de mümkün (Section 16.1).
- **Yeni permission'lar:** `EXPENSE_VIEW`/`EXPENSE_MANAGE` (BUSINESS_ADMIN + BRANCH_MANAGER - kendi şubesi için
  oluştur/gönder), `EXPENSE_APPROVE` (yalnızca BUSINESS_ADMIN - finansal onay merkezi kalıyor, `REPORT_CHAIN_
  VIEW` ile aynı gerekçe). Business-level (branchId=null) gider oluşturma da BUSINESS_ADMIN-only.
- **`RecurringExpenseScheduler`** (`@Scheduled`, 6 saatte bir): aktif şablonları tarar, `dayOfMonth` bugüne
  denk geliyorsa (kısa aylarda ayın son gününe düşürülüyor) ve o dönem (`YearMonth`) için henüz üretilmemişse
  otomatik bir `DRAFT` Expense oluşturur - admin sonra düzenler/onaylar (Section 16.2). İdempotency DB'deki
  `uq_expense_template_period` partial unique index + `existsBySourceTemplateIdAndGeneratedForPeriod` ön
  kontrolüyle sağlanıyor; scheduler'ın kaçırılan/tekrarlanan çalışması hiçbir zaman bir dönemi iki kez
  taslaklamıyor (gün sonu kapanış scheduler'ıyla aynı self-correcting felsefe).
- **`StaffExpenseController`:** kategori CRUD, gider CRUD + submit/approve/reject, tekrarlayan şablon CRUD.
- **`reporting` modülüne Section 17 endpoint'i:** `GET .../reports/operating-result` - `ReportingService.
  getBranchReport`'un net satışından `ExpenseService.sumApprovedExpenses`'i (yalnızca `APPROVED` giderler)
  çıkararak "Yönetimsel Net Sonuç" döner. Backend/frontend hiçbir yerde "net kâr" ifadesi kullanılmıyor -
  Section 17'nin uyarısı (vergi/stok maliyeti/personel tahakkuku/amortisman modellenmiyor) `OperatingResult
  Response`'un javadoc'unda ve staff-web kartındaki uyarı metninde açıkça belirtiliyor.
- **staff-web:** yeni `/expenses` ekranı (kategori yönetimi, gider oluştur/gönder/onayla/reddet - rol bazlı
  aksiyon görünürlüğü, tekrarlayan şablon listesi/oluşturma), `/reports/[branchId]`'ye "Yönetimsel Net Sonuç"
  kartı. `StaffNav`'a BUSINESS_ADMIN + BRANCH_MANAGER için "Giderler" linki eklendi.

**Teknik kararlar:**
- `receiptImageUrl` mevcut `Product.imageUrl` deseniyle aynı: düz bir URL alanı, gerçek dosya upload/object
  storage yok (kod tabanında hiçbir yerde böyle bir altyapı yok - kapsam dışı bırakıldı).
- `created_by_staff_user_id` nullable - scheduler'ın ürettiği taslakların bir insan aktörü yok.
- `ExpenseService` diğer modüllerin servisleri gibi `StaffContext`'i doğrudan alıyor (branch-scope + business-
  level-only kuralları kendi içinde uyguluyor) - controller yalnızca `Permission` düzeyini çözüyor, tıpkı
  `AnnouncementService`/`DailyCloseService` gibi.
- `ModuleBoundaryTest`'e yeni bir case eklendi (`expense.repository` yalnızca `expense` modülü içinden
  erişilebilir); `reporting`'in `ExpenseService`'e bağımlılığı bu kuralı ihlal etmiyor çünkü yalnızca public
  facade'a erişiyor.

**Backend test sayısı 103 → 109** (yeni: `ExpenseFlowIntegrationTest` 5 - DRAFT→SUBMIT→APPROVE ve sonrasında
immutability, KITCHEN_STAFF'ın 403 alması, BRANCH_MANAGER'ın business-level gider oluşturamaması + başka
şubeye erişememesi + onaylayamaması, recurring scheduler'ın bir dönem için tam olarak bir kez taslak
üretmesi (idempotency), operating-result'ın net satıştan onaylı giderleri doğru çıkarması; `ModuleBoundaryTest`
'e 1 yeni case).

**Not:** Bu madde de canlı tarayıcı doğrulaması olmadan tamamlandı (bkz. proje hafızası - Chrome testi bu
projede kapalı); doğrulama backend integration testleri + `npm run build`/`lint`/`tsc --noEmit` ile yapıldı.

---

## Gap-Analysis #11 — Sahibine Otomatik Gün Sonu Bildirimi — 🔄 TASARIM ONAYLANDI, UYGULAMA SÜRÜYOR

Gap-analysis'in "Önerilen Geliştirme Sırası" #11 maddesi (product-requirements.md Section 15 + M12). WhatsApp
adapter spec gereği blocker değil - bu madde yalnızca email kanalını kapsıyor.

**Onaylanmış tasarım:**
- **Yeni `com.qrmenu.ownernotification` modülü** (kendi persistence'ı var - `expense`/`dailyclose` ile aynı
  desen). `OwnerNotificationPort` arayüzü + `EmailOwnerNotificationAdapter` (`spring-boot-starter-mail` /
  `JavaMailSender`). Port yalnızca `EMAIL` kanalıyla başlıyor, WhatsApp için ayrı bir adapter ileride eklenecek
  (port zaten sağlayıcı-bağımsız kurulduğu için genişletmek kod değişikliği gerektirmeyecek).
- **`OwnerNotificationLog` entity (V19 migration):** `dailyCloseReportId`, `businessId`, `branchId`,
  `businessContactId`, `recipientEmail`, `channel`(=EMAIL), `status`(SENT/FAILED), `errorMessage` nullable,
  `triggeredBy`(AUTO/MANUAL), `triggeredByStaffUserId` nullable, `attemptedAt`. Her deneme (otomatik veya
  manuel) ayrı bir satır - üzerine yazılmıyor, denetim izi.
- **Tetikleme:** `DailyCloseScheduler`, bir branch için `generateFinal(...)` başarılı dönünce
  `OwnerNotificationService.dispatchAutoForDailyClose(report)`'u `@Async` çağırır (M5'teki
  `MockPaymentSimulationDispatcher` ile aynı desen - SMTP yavaşlığı scheduler'ın diğer şubeleri işlemesini
  bloklamasın diye). Alıcılar `TenantService.listBusinessContacts(businessId)` üzerinden
  `active && dailyReportRecipient && email dolu` filtresiyle bulunuyor.
- **Idempotency (AUTO):** aynı `(reportId, contactId)` için zaten bir `AUTO` log satırı varsa tekrar
  gönderilmiyor (recurring-expense'teki "bir dönem için tam bir kez" idempotency felsefesiyle aynı). **Manuel
  yeniden gönder** bu kontrolü atlar, her seferinde yeni bir deneme/log satırı oluşturur.
- Mesaj içeriği (şube, tarih, brüt/net satış, refund, sipariş sayısı, top-5 ürün) gönderim anında
  `ReportingService.getBranchReport(...)`'tan üretiliyor - `DailyBranchCloseReport` ürün kırılımını
  saklamadığı için (Excel export'la aynı "DB'den anlık yeniden üretilebilir" ilkesi). Bir alıcıya gönderim
  başarısız olursa diğer alıcılar etkilenmiyor (izole hata).
- **SMTP (dev):** `docker-compose.yml`'e Mailhog eklenir (SMTP :1025, web UI :8025, auth yok);
  `SMTP_HOST`/`SMTP_PORT`/`SMTP_USERNAME`/`SMTP_PASSWORD`/`OWNER_NOTIFICATION_FROM_EMAIL` env değişkenleri -
  email opsiyonel/blocker olmadığından `INTERNAL_ADMIN_TOKEN` gibi zorunlu değil, Mailhog'a işaret eden sane
  default'larla gelir.
- **API:** mevcut `Permission.REPORT_VIEW` yeniden kullanılıyor (`daily-close/final` ile aynı gerekçe) -
  `GET /api/staff/branches/{branchId}/daily-close/{reportId}/notifications` (log listesi) ve
  `POST .../notifications/resend` (manuel tetikleme). `StaffDailyCloseController`'a eklenecek;
  `DailyCloseService`'e tenant-scope doğrulamalı küçük bir `getById` metodu gerekiyor.
- **staff-web:** `/reports/[branchId]` Gün Sonu Kapanışları listesindeki FINAL satırlara "Bildirim: N
  gönderildi / M başarısız" rozeti + eksik/başarısız varsa "Tekrar Gönder" butonu.
- **Test planı:** `OwnerNotificationFlowIntegrationTest` - auto-dispatch idempotency, manuel resend'in her
  zaman yeni satır oluşturması, kısmi başarısızlıkta izolasyon, uygun olmayan contact'lara (dailyReportRecipient
  =false / email boş) gönderilmemesi, REPORT_VIEW olmayan role 403. Email doğrulaması gerçek SMTP yerine
  GreenMail (in-memory test SMTP) ile yapılacak.

Bu tasarım superpowers:brainstorming akışıyla (4 netleştirme sorusu: gerçek SMTP vs mock, async vs senkron
tetikleme, kalıcı denetim kaydı var/yok, staff-web'de manuel resend var/yok) kullanıcıyla netleştirildi ve
onaylandı; kullanıcı talebiyle ayrı bir `docs/superpowers/specs/*.md` dosyası yerine doğrudan buraya yazıldı.
Uygulama adımları ilerledikçe bu bölüm güncellenecek, tamamlandığında `✅ COMPLETED` olarak kapatılacak.
