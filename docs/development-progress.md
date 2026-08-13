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

## Gap-Analysis #11 — Sahibine Otomatik Gün Sonu Bildirimi — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #11 maddesi (product-requirements.md Section 15 + M12). WhatsApp
adapter spec gereği blocker değil - bu madde yalnızca email kanalını kapsıyor; tasarım
superpowers:brainstorming akışıyla (4 netleştirme sorusu: gerçek SMTP vs mock, async vs senkron tetikleme,
kalıcı denetim kaydı var/yok, staff-web'de manuel resend var/yok) kullanıcıyla netleştirilip onaylandıktan
sonra uygulandı.

**Ana özellikler:**
- **Yeni `com.qrmenu.ownernotification` modülü** (kendi persistence'ı var - `expense`/`dailyclose` ile aynı
  desen). `OwnerNotificationPort` arayüzü + `EmailOwnerNotificationAdapter` (`spring-boot-starter-mail` /
  `JavaMailSender`). Port yalnızca `EMAIL` kanalıyla başlıyor, WhatsApp için ayrı bir adapter ileride eklenebilir
  (port zaten sağlayıcı-bağımsız kurulduğu için genişletmek kod değişikliği gerektirmeyecek).
- **`OwnerNotificationLog` entity (V19 migration):** `dailyCloseReportId`, `businessId`, `branchId`,
  `businessContactId`, `recipientEmail`, `channel`(=EMAIL), `status`(SENT/FAILED), `errorMessage` nullable,
  `triggeredBy`(AUTO/MANUAL), `triggeredByStaffUserId` nullable, `attemptedAt`. Her deneme (otomatik veya
  manuel) ayrı bir satır - üzerine yazılmıyor, denetim izi.
- **Tetikleme:** `DailyCloseScheduler`, bir branch için `generateFinal(...)` başarılı dönünce
  `OwnerNotificationService.dispatchAutoForDailyClose(report)`'u çağırır - metot `@Async` + `CompletableFuture<Void>`
  döner (M5'teki `MockPaymentSimulationDispatcher` ile aynı fire-and-forget desen; future'ın tek amacı testlerin
  async dispatch'i deterministik biçimde bekleyebilmesi, scheduler dönüş değerini kullanmıyor). Alıcılar
  `TenantService.listBusinessContacts(businessId)` üzerinden `active && dailyReportRecipient && email dolu`
  filtresiyle bulunuyor.
- **Idempotency (AUTO):** aynı `(reportId, contactId)` için zaten bir log satırı varsa tekrar gönderilmiyor
  (recurring-expense'teki "bir dönem için tam bir kez" idempotency felsefesiyle aynı) - scheduler'ın FINAL
  gününü 5 dakikada bir yeniden yoklaması hiç double-send yaratmıyor. **Manuel yeniden gönder** bu kontrolü
  atlar, her seferinde yeni bir deneme/log satırı oluşturur.
- Mesaj içeriği (şube, tarih, brüt/net satış, refund, sipariş sayısı, top-5 ürün) gönderim anında
  `ReportingService.getBranchReport(...)`'tan üretiliyor - `DailyBranchCloseReport` ürün kırılımını
  saklamadığı için (Excel export'la aynı "DB'den anlık yeniden üretilebilir" ilkesi). Bir alıcıya gönderim
  başarısız olursa diğer alıcılar etkilenmiyor (her deneme kendi try/catch'i içinde izole).
- **SMTP (dev):** `docker-compose.yml`'e Mailhog eklendi (SMTP :1025, web UI :8025, auth yok);
  `SMTP_HOST`/`SMTP_PORT`/`SMTP_USERNAME`/`SMTP_PASSWORD`/`OWNER_NOTIFICATION_FROM_EMAIL` env değişkenleri -
  email opsiyonel/blocker olmadığından `INTERNAL_ADMIN_TOKEN` gibi zorunlu değil, Mailhog'a işaret eden sane
  default'larla geliyor (`application.yml`'in `spring.mail.*` bloğu, tek `spring:` kökü altında - ayrı bir
  ikinci `spring:` anahtarı YAML'da sessizce çakışırdı, tek blokta birleştirildi).
- **API:** mevcut `Permission.REPORT_VIEW` yeniden kullanıldı (`daily-close/final` ile aynı gerekçe) -
  `GET /api/staff/branches/{branchId}/daily-close/{reportId}/notifications` (log listesi) ve
  `POST .../notifications/resend` (manuel tetikleme, `StaffDailyCloseController`'a eklendi).
  `DailyCloseService`'e tenant-scope doğrulamalı yeni bir `getById` metodu eklendi;
  `DailyCloseReportResponse`'a da `id` alanı eklendi (önceden hiç dönmüyordu, staff-web'in bu uçları
  çağırabilmesi için gerekliydi).
- **staff-web:** `/reports/[branchId]` Gün Sonu Kapanışları tablosuna yeni "Bildirim" sütunu - her FINAL
  satırda "N gönderildi"/"M başarısız" rozetleri + her zaman görünen "Tekrar Gönder" butonu (PREVIEW satırlarda
  "-").

**Teknik kararlar:**
- `dispatchAutoForDailyClose` `CompletableFuture<Void>` dönüyor ama scheduler bunu görmezden geliyor - salt
  testlerin `@Async` tamamlanmasını `.get(timeout)` ile deterministik bekleyebilmesi için (aksi halde
  idempotency testi race condition'a düşerdi).
- `OwnerNotificationService` içindeki private `saveLog`/`dispatch` metotları `this.` üzerinden çağrıldığından
  bilinçli olarak `@Transactional`/`@Async` taşımıyor (Spring proxy'si self-invocation'ı yakalamaz - M5'in
  `MockPaymentSimulationDispatcher` notundaki aynı tuzak); `repository.save()` zaten kendi transaction'ını
  taşıyor, ek sarmalayıcıya gerek yok.
- Manuel resend, AUTO idempotency kontrolünü tamamen atlıyor ve mevcut tüm uygun alıcılara yeniden gönderiyor
  (kısmi/hedefli resend değil) - basitlik tercih edildi, spec bunu zorunlu kılmıyor.
- `ModuleBoundaryTest`'e yeni bir case eklendi (`ownernotification.repository` yalnızca kendi modülü içinden
  erişilebilir).

**Backend test sayısı 109 → 114** (yeni: `OwnerNotificationFlowIntegrationTest` 4 - auto-dispatch idempotency
GreenMail ile gerçek SMTP üzerinden doğrulanıyor, uygun olmayan contact'ların [pasif/opt-out/email'siz]
atlanması, manuel resend'in her zaman yeni log satırı üretmesi + AUTO zaten göndermişken bile çalışması,
REPORT_VIEW olmayan role 403; `ModuleBoundaryTest` +1).

**Not:** Bu madde de canlı tarayıcı doğrulaması olmadan tamamlandı (bkz. proje hafızası - Chrome testi bu
projede kapalı); doğrulama backend integration testleri (GreenMail in-memory SMTP dahil) +
`npm run build`/`lint`/`tsc --noEmit` ile yapıldı.

---

## Gap-Analysis #12 — Security Hardening / RLS Reassessment — ✅ COMPLETED

Gap-analysis'in "Önerilen Geliştirme Sırası" #12 maddesi, product-requirements.md M13 (Section 24) ile aynı
kapsam: complete security review, RLS reassessment, rate limiting, token/log redaction, upload security, export
authorization tests, backup/restore expectations, observability, deployment hardening. Bu, roadmap'in son
maddesi ("kapanışta").

**Kapsam dışı bırakılanlar (ön-taramada gerekçelendirildi):**
- **Rate limiting** — zaten var (`RateLimitFilter` + testleri), tekrar ele alınmayacak.
- **Upload security** — projede gerçek dosya yükleme (`MultipartFile`) hiç yok, ürün görselleri yalnızca
  `Product.imageUrl` string alanı; bu madde N/A.

**Onaylanmış tasarım — 6 alt-alan, sırayla, her biri kendi tara→düzelt→test→commit döngüsüyle:**

1. **RLS reassessment — sonuç:** DB-seviyesi RLS eklenmedi (Section 21'deki karar korundu). Kod
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
2. **Complete security review** — kontrol listesi: (a) her staff endpoint'in doğru `Permission` kontrolü yaptığı,
   (b) şube-scoping'in (staff yalnızca atandığı branch'lere erişebiliyor mu) tutarlı uygulandığı, (c)
   `CorsConfig`'in gereksiz method/header açmadığı, (d) staff/customer session cookie'lerinin
   `HttpOnly`/`Secure`/`SameSite` flag'leri, (e) DTO'larda Bean Validation (`@Valid`) tutarlılığı, (f)
   native/manuel SQL varsa injection riski. Bulunan her açık ayrı bir küçük fix+commit olacak.
3. **Token/log redaction** — `log.info/debug/warn/error` çağrılarında token/şifre/webhook-secret/kart verisi
   sızıntısı olup olmadığı taranacak (ilk yüzeysel taramada bulunamadı, daha kapsamlı bakılacak), exception
   handler'ların response'a stack trace/secret sızdırmadığı doğrulanacak.
4. **Export authorization tests** — `dailyclose` Excel export + reporting export endpoint'lerine, yanlış
   role/branch ile erişim denenince 403 döndüğünü doğrulayan entegrasyon testleri eklenecek (eksikse).
5. **Deployment hardening + observability** — `.env.example`'da gerçek secret olmadığının doğrulanması, prod'da
   cookie `Secure=true` zorunluluğunun kontrolü, mevcut log formatı/seviyesinin gözden geçirilmesi. Çoğunlukla
   doğrulama + kısa öneri notu; büyük yeni altyapı kurulmayacak.
6. **Backup/restore expectations** — kod değişikliği değil, Postgres `pg_dump`/Docker volume stratejisi için kısa
   bir prosedür notu.

**Doğrulama:** her alt-alan sonunda backend `mvn test` (+ yeni entegrasyon testleri varsa), frontend etkileniyorsa
`npm run build`/`lint`/`tsc --noEmit`. Kritik davranış değişikliği (ör. cookie flag) olursa hızlı gerçek-tarayıcı
doğrulaması (bkz. proje hafızası — Chrome testi bu projede kapalı, sadece gerekirse istisna).

Bu tasarım superpowers:brainstorming akışıyla (RLS kapsamı, M13 alt-maddelerinden hangilerinin dahil edileceği,
backup/restore'un dahil edilip edilmeyeceği, sıralı-modül-modül vs önce-tam-tarama yaklaşımı olmak üzere 4
netleştirme sorusu + bölüm bölüm onay) kullanıcıyla netleştirildi ve onaylandı; kullanıcı talebiyle ayrı bir
`docs/superpowers/specs/*.md` dosyası yerine doğrudan buraya yazıldı. Uygulama adımları ilerledikçe bu bölüm
güncellenecek, tamamlandığında `✅ COMPLETED` olarak kapatılacak.

**Export authorization tests — sonuç:** `daily-close/excel` (branch) ve `daily-close/excel`
(chain) uç noktaları zaten doğru Permission kontrolünü yapıyordu (REPORT_VIEW / REPORT_CHAIN_VIEW)
ama hiçbir test bunu doğrulamıyordu. `DailyCloseFlowIntegrationTest`'e iki yeni test eklendi:
`kitchenStaffCannotExportDailyCloseExcel`, `branchManagerCannotExportChainDailyCloseExcel`.

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

---

## Gap-Analysis #13 — Session/TableVisit TTL — ✅ COMPLETED

`docs/gap-analysis.md`'nin bölüm 2'sindeki "Session/TableVisit TTL" PARTIAL maddesi (bölüm 3'ün 1-12
önceliklendirme sırasına hiç girmemişti — dosyayı yeniden tarayan bir gap-analizi sırasında fark edildi):
`AnonymousCustomerSession`/`TableVisit` `lastActivityAt` tutuyordu ama hiçbir scheduled job süresi dolan bir
`TableVisit`'i kapatmıyordu. `CustomerSessionService.checkIn()` zaten TTL'i (`VISIT_TTL`, 6 saat) geçmiş bir
visit'i *yeniden kullanmıyordu* (yeni visit başlatıyordu), ama `getOwnedTableVisit` (cart/ordering'in ownership
kapısı) hiç TTL kontrolü yapmıyordu — eski bir session cookie + eski bir `tableVisitId` ile süresiz olarak sepete
ekleme/sipariş işlemi yapılabiliyordu.

V20 migration: `table_visit`'e nullable `closed_at TIMESTAMPTZ` eklendi (+ `WHERE closed_at IS NULL` partial
index, açık-visit taramasını hızlandırmak için). `TableVisit.close()`/`isClosed()` eklendi. Yeni
`TableVisitCleanupScheduler` (`OrderCleanupScheduler` ile aynı desende, 15 dk periyot), `last_activity_at`'i
`VISIT_TTL`'i aşan ve henüz kapanmamış her visit'i `closed_at` ile işaretliyor.
`CustomerSessionService.getOwnedTableVisit`, ownership-mismatch ile aynı gerekçeyle (var olduğunu doğrulamamak
için 403 değil 404) artık `visit.isClosed()`'i de reddediyor.

Yeni `TableVisitCleanupSchedulerIntegrationTest` (2): stale visit kapanıyor/fresh visit dokunulmadan kalıyor
(scheduler doğrudan çağrılıp `last_activity_at` SQL ile geriye tarihleniyor, `OrderCleanupSchedulerIntegrationTest`
ile aynı desen), kapanmış bir visit üzerinden cart endpoint'ine istek 404 dönüyor. Tam backend suite yeşil
(119 test, ilgisiz bir flaky `RateLimitFilterTest` testi izole çalıştırmada geçti - tekrar denemede geçti).

---

## Gap-Analysis #14 — Mutfak Ekranında Permission'a Bağlı Ciro Özeti — ✅ COMPLETED

`docs/product-requirements.md`'yi (Gap-Analysis #1-13'ün üzerine, bölüm bölüm) yeniden kodla karşılaştıran bir
taramada bulundu: Section 11'in 💡 notu ("Mutfak ekranında ciro/finansal veri gösterimi role sabitlenmez;
`REPORT_FINANCIAL_SUMMARY_VIEW` permission'ı olan kullanıcıya gösterilir. Böylece işletme isterse mutfakta
görünür, istemezse gizler.") hiç uygulanmamıştı - böyle bir permission yoktu, KDS ekranında da hiçbir ciro/
finansal gösterim yoktu. `docs/gap-analysis.md` bunu hiç yakalamamıştı çünkü o dosyanın taraması modül-var-mı
seviyesindeydi, bu kadar ince taneli bir permission-gated UI detayına inmemişti.

Yeni `Permission.REPORT_FINANCIAL_SUMMARY_VIEW`: yalnızca `BUSINESS_ADMIN`/`BRANCH_MANAGER`'a verildi (ikisi de
zaten `KITCHEN_DECIDE`'a sahip, KDS'i görebiliyor); `KITCHEN_STAFF` ve `CASHIER` almıyor - `CASHIER` düz
`REPORT_VIEW`'a sahip olmasına rağmen bu ayrı permission'ı almıyor, spec'in "role sabitlenmez, ayrı bir izin"
vurgusuyla uyumlu (aksi halde herhangi bir REPORT_VIEW sahibi otomatik görürdü). Yeni
`GET /api/staff/branches/{branchId}/reports/kitchen-summary` (`StaffReportingController`), mevcut
`ReportingService.getBranchReport`'u tekrar kullanıp brüt satış/net satış/sipariş sayısını dönen küçük bir DTO
(`KitchenFinancialSummaryResponse`) - tam rapor değil, yalnızca KDS başlığına yetecek bir alt küme.

`staff-web/app/kitchen`: sayfa `me()` ile kendi rolünü kontrol edip yalnızca BUSINESS_ADMIN/BRANCH_MANAGER ise
özeti çekiyor (backend zaten permission'ı zorunlu kılıyor - bu yalnızca 403'e gidecek bir çağrıdan kaçınma
niceliği, `StaffNav`'ın kendi `isAdmin` desenindeki gibi). KDS başlığının altına küçük bir brüt satış/net satış/
sipariş sayısı bloğu eklendi (`page.module.css`'e `.financialSummary*` sınıfları).

Yeni test: `ReportingFlowIntegrationTest.kitchenFinancialSummaryIsGatedToItsOwnPermissionNotPlainReportView` -
BUSINESS_ADMIN özeti görebiliyor, CASHIER (REPORT_VIEW'a sahip olmasına rağmen) 403 alıyor. Tam backend suite
yeşil (120 test). Frontend `tsc --noEmit` + `eslint` temiz.

---

## UI/UX Productization Gate — Adım 1: Shared Frontend Component Library — ✅ COMPLETED

`product-requirements.md` Bölüm 19.5'in "Cross-cutting" uygulama sırasının 1. adımı: design token'lar
(`globals.css`) zaten iki app arasında birebir eşleşiyordu (staff-web'in kendi dosyasında "customer-web'den
kopyalandı" notu vardı), ama Bölüm 19.1'in ortak component listesinin çoğu (Card, Dialog/Modal, Tabs, Toast,
ConfirmDialog, IconButton, Input/Select/Textarea/FormField) hiçbir app'te yoktu, ve customer-web'de var olan
`EmptyState`/`ErrorState`/`Skeleton` staff-web'de hiç yoktu (10 staff-web sayfası elle "Yükleniyor..." metni
yazıyordu). Kullanıcıyla netleştirme: bu adım yalnızca kütüphaneyi kurar, mevcut sayfalara wiring/refactor
(3-6. adımlar: sidebar, kasa/KDS, admin CRUD refactor, raporlama) bu kapsamda **değil**.

**Ana özellikler:** customer-web canonical kaynak, staff-web'e mevcut `Button`/`globals.css` convention'ıyla
("Copied from customer-web/...") kopyalandı - iki app arasında hâlâ workspace/monorepo tooling yok, kasıtlı
karar korundu. Yeni primitive'ler: `IconButton`, `Card`, `FormField`+`Input`+`Textarea`+`Select` (FormField
`useId` ile label/hint/error `aria-describedby`/`aria-invalid` bağlantısını render-prop üzerinden kuruyor),
`Dialog` (BottomSheet'e dokunulmadı - masaüstü/orta ekran için ayrı, bağımsız bir modal, aynı erişilebilirlik
sözleşmesi: role="dialog"/aria-modal/Escape+overlay ile kapanma), `ConfirmDialog` (Dialog üzerine kurulu,
`tone=default|danger`), `Tabs` (role="tablist" segmented control), `Toast`/`ToastProvider`/`useToast` (native
`alert()` yerine, her iki app'in `layout.tsx`'ine root'ta mount edildi - bu tek başına görünür/davranışsal
değişiklik yaratmıyor, sadece provider'ı kullanılabilir kılıyor). `EmptyState`/`ErrorState`/`Skeleton`
staff-web'e parity kopyası olarak eklendi (+ staff-web `globals.css`'te eksik olan `skeleton-pulse`
keyframes'i tamamlandı). `Badge` tone union'ı iki app'te `neutral|success|danger|warning`'e birleştirildi
(customer-web'de `--color-success` yoktu, ikisine de `--color-warning`/-bg eklendi). Bu iş sırasında Button'ın
`danger` varyantı eksik olduğu fark edildi (Bölüm 19.1: "primary/secondary/destructive action ayrımı") -
`ConfirmDialog`'un `tone="danger"` ihtiyacıyla birlikte eklendi (mevcut `--color-danger`/-bg token'ları, Badge'in
danger tonuyla aynı çift, yeniden kullanıldı).

**Teknik kararlar:** Yeni npm bağımlılığı eklenmedi (icon library dahil - Dialog/Toast kapatma "×" karakteriyle
çözüldü); shadcn/ui'dan kod kopyalanmadı, yalnızca Dialog/ConfirmDialog/Tabs/Toast'ın erişilebilirlik
davranışı (role/aria/escape) referans alındı, projenin kendi CSS Modules idiomunda yazıldı. `BottomSheet`/
`QuantityStepper` staff-web'e kopyalanmadı - şu an gerçek bir kullanım yeri yok (spekülatif component
eklenmedi); ihtiyaç doğduğunda (staff mobile nav/drawer, adım 3) eklenecek.

**Doğrulama:** her iki app'te `npm run lint` + `npm run build` (tsc dahil) temiz. Canlı Chrome testi
yapılmadı (proje hafızası - browser testi bu projede kapalı). Component'ler henüz hiçbir mevcut sayfaya
wiring edilmedi; bu iş roadmap'in sonraki adımlarında (sidebar/kasa-KDS/admin refactor/raporlama) yapılacak.

---

## UI/UX Productization Gate — Adım 2: Customer Web Productization — ✅ COMPLETED

`product-requirements.md` Bölüm 19.5'in "Cross-cutting" uygulama sırasının 2. adımı. Mevcut
`customer-web` akışı (QR karşılama → menü → ürün detay → sepet → ödeme → takip) Milestone 1-9'un ilk
yapımından beri elle test edilmemiş/yeniden gözden geçirilmemişti, ama beklenenin aksine büyük ölçüde
Bölüm 19.2'ye zaten uyuyordu (sticky header/kategori nav, bottom sheet'ler, required/optional option
ayrımı, sticky CTA'lar, Türkçe hata mesajları, EmptyState/ErrorState/Skeleton kullanımı, geçersiz QR/tükenmiş
ürün ele alımı). Kod taraması yalnızca 4 somut gap buldu; kapsam kullanıcıyla bu 4 maddeyle netleştirildi,
kapsam dışı bir yeniden yazım yapılmadı:

1. **Emoji placeholder kaldırıldı** - `ProductCard`'ın görselsiz ürün alanı ve boş menü `EmptyState`'i
   🍽️ emoji kullanıyordu (Bölüm 19.1: "emoji yerine tasarım diliyle uyumlu nötr placeholder"). Yeni
   `DishPlaceholderIcon` (elle yazılmış küçük inline SVG, yeni bağımlılık yok - kullanıcıyla netleştirilen
   karar: Lucide gibi bir icon library şimdilik eklenmedi) her iki yerde kullanılıyor.
2. **Ürün kartı görsel boyutu** - 84px'den Bölüm 19.2'nin önerdiği aralığa (104-120px) uyacak şekilde
   112px'e çıkarıldı; aspect-ratio/lazy-load/onError fallback davranışı değişmedi.
3. **Sipariş takip timeline'ı** - `/order/track/[token]` yalnızca tek bir status `Badge` gösteriyordu
   (Bölüm 19.2: "salt status metni olmamalı; anlamlı durum kartı/timeline/progress pattern'i
   kullanılmalıdır"). Yeni `OrderStatusTimeline`: happy-path adımlarını (Ödeme → İşletme onayı →
   Hazırlanıyor → Hazır → Tamamlandı) done/current/upcoming durumlarıyla dikey bir stepper olarak
   gösteriyor. `REJECTED_BY_STORE`/`CANCELLED` adımların bir devamı gibi değil, ayrı bir "durduruldu"
   banner'ı olarak gösteriliyor (refund notu oraya bağlanıyor); `PAYMENT_FAILED` ödeme adımını durdurmadan
   yerinde bir hata olarak işaretliyor (sipariş `AWAITING_PAYMENT`'a geri dönebildiği için).
4. **Oturum/sepet süresi dolma ekranı** - sayfa yüklendikten sonra sepet aksiyonlarından
   (`addCartItem`/`removeCartItem`/`createPaymentIntent`) gelen bir 404, artık jenerik satır-içi hata yerine
   tam sayfa bir kurtarma ekranına yönlendiriyor (retry, mevcut `retry()`/`checkInWithQrToken` akışını tekrar
   kullanıyor - kapanmış bir visit varsa zaten yeni bir visit başlatıyor, Gap-Analysis #13). Backend araştırması
   sırasında bulunan bir nüans: bu 404 hem kapanmış `TableVisit`'ten hem de bayat ürün/sepet referansından
   gelebiliyor (`OrderingService.addItem/removeItem/beginPaymentForDraftOrder`), ve `ApiError` yalnızca HTTP
   status taşıdığı için (makine-okunur bir reason code yok) ikisi client-side ayırt edilemiyor. Backend'e yeni
   bir error code eklemek onaylanan kapsamın (backend değişikliği yok) dışında kalacağından, ekran metni her
   iki nedende de doğru kalacak şekilde ("oturumunuz sona erdi" yerine "masa oturumunuz veya sepetiniz güncel
   görünmüyor") yazıldı - kurtarma eylemi (yeniden yükleme) ikisi için de zaten doğru.

**Doğrulama:** her değişiklikten sonra `customer-web`'de `npm run lint` + `npm run build` (tsc dahil) temiz.
Canlı Chrome testi yapılmadı (proje hafızası - browser testi bu projede kapalı). Backend değişikliği yok.

---

## UI/UX Productization Gate — Adım 3: Staff Web Application Shell/Sidebar — ✅ COMPLETED

`product-requirements.md` Bölüm 19.5'in "Cross-cutting" uygulama sırasının 3. adımı: Bölüm 19.3'ün
"Application shell" gereksinimi — desktop'ta üstte wrap olan link listesi yerine kalıcı sol sidebar +
top bar, küçük ekranda drawer navigation. Mevcut `StaffNav` (`components/layout/StaffNav.tsx`) tam olarak
bu yasaklanan pattern: `flex-wrap` ile taşan düz link listesi, ~13 sayfanın her biri kendi başına mount
ediyor, hiçbir landing/dashboard route'u yok (her rol girişten sonra `/branches`'e düşüyor).

Kullanıcıyla iki kapsam kararı netleştirildi:
1. **Dashboard:** minimal bir `/dashboard` placeholder sayfası eklenecek (karşılama + role göre kısayol
   kartları, KPI verisi yok); gerçek KPI içeriği Bölüm 19.3'ün "Dashboard" alt-bölümü Adım 6'da (raporlama/
   dashboard görselleştirme) yapılacak. Sidebar'ın "Dashboard" maddesi ve login sonrası yönlendirme buraya
   işaret edecek.
2. **Şube bazlı nav linkleri** (Kasa/Mutfak/Pickup/Raporlar/İadeler, URL'de branchId var): rolün
   `branchIds`'i tam 1 ise link doğrudan o şubeye gider (ör. `/cashier/{branchId}`); değilse (BUSINESS_ADMIN/
   PLATFORM_ADMIN/çok şubeli manager) mevcut `/branches` şube seçiciye yönlendirir. Yeni bir branch-switcher
   state'i eklenmiyor (YAGNI - mevcut `/branches` zaten bu işi görüyor).

**Backend — küçük additive değişiklik:** top bar'ın "aktif şube/işletme bağlamı" göstermesi gerekiyor
(Bölüm 19.3) ama `GET /business` (`BUSINESS_SETTINGS_MANAGE`) ve `GET /branches` (`BRANCH_MANAGE`) CASHIER/
KITCHEN_STAFF için erişilemez. Permission-gated olmayan `GET /api/staff/auth/me`'nin `StaffContextResponse`'una
`businessName: string` ve `branches: {id,name}[]` (yalnızca `context.branchIds()`'e karşılık gelenler -
admin rollerinde branchIds boş olduğu için bu liste de boş kalır) eklenecek. `StaffAuthController`'a
`TenantService` inject edilip `getBusiness`/`listBranches` (filtrelenmiş) kullanılacak. Yeni permission
yüzeyi yok - yalnızca zaten kimliği doğrulanmış kullanıcının kendi business/branch adlarını görmesi.

**Frontend:** yeni `AppShell` component (`components/layout/AppShell.tsx`) `StaffNav`'ın yerini alıyor -
aynı `me()` + 401→redirect deseni, `AnnouncementBanner` mount'u korunuyor. Sol sidebar (≥1024px kalıcı,
<1024px hamburger tetikli drawer overlay), Bölüm 19.3'teki dört grup bilgi mimarisi (Operasyon/Yönetim/
Finans/Sistem), rol bazlı link filtreleme (frontend'de granüler permission listesi yok, mevcut `isAdmin`
deseninin genişletilmiş hali), aktif route vurgusu, top bar'da `businessName`/`branches` + email/rol +
çıkış. Var olan ~13 sayfa dosya taşınmadan (route group yok, düşük riskli) `<StaffNav />`'ı
`<AppShell>...</AppShell>` ile değiştiriyor; eski `StaffNav.tsx`/`.module.css` silinecek. Ayrı bir
collapse-toggle eklenmiyor (spec "kalıcı **veya** collapsible" diyor, kalıcı yeterli).

**Doğrulama planı:** her adımdan sonra backend `mvn test`, frontend `npm run lint` + `npm run build`.
Canlı Chrome testi yapılmayacak (proje hafızası). Bu tasarım superpowers:brainstorming akışıyla (2
netleştirme sorusu: dashboard kapsamı, şube bazlı nav) kullanıcıyla netleştirildi ve onaylandı; ayrı bir
`docs/superpowers/specs/*.md` dosyası yerine doğrudan buraya yazıldı.

**Sonuç:** tasarım plana göre uygulandı, dört ayrı commit'te:

1. **Backend** (`c4e0bb6`): `StaffContextResponse`'a `businessName`/`branches` eklendi,
   `StaffAuthController` `TenantService` kullanarak `getBusiness`/`listBranches` (context.branchIds()'e
   filtrelenmiş) ile dolduruyor. Yeni test: `StaffAccessFlowIntegrationTest`'te businessName/branches
   assertion'ları + branch-scoped bir KITCHEN_STAFF'ın `branches` listesinin yalnızca kendi şubesini
   içerdiğini doğrulayan bir kontrol. Tam backend suite yeşil (mvn test, tüm modüller).
2. **AppShell component** (`5d54053`): `components/layout/AppShell.tsx` + `.module.css` - ≥1024px kalıcı
   sidebar, <1024px hamburger→drawer (backdrop + kapatma), `lib/staffNav.ts`'e çıkarılan `NAV_GROUPS`
   (Operasyon/Yönetim/Finans/Sistem) rol bazlı filtreleniyor. Şube bazlı linkler (Kasa/Mutfak/İadeler)
   için `singleBranchHref`: business-wide rol (BUSINESS_ADMIN/PLATFORM_ADMIN) → `/branches`, değilse
   ilk atanmış şubeye direkt. Raporlar için ayrı `reportsHref` - admin'in gerçek bir "tüm şubeler" ekranı
   (`/reports`, `REPORT_CHAIN_VIEW`) olduğundan `/branches` üzerinden dolaşmıyor, doğrudan oraya gidiyor;
   BRANCH_MANAGER/CASHIER (yalnızca `REPORT_VIEW`, `REPORT_CHAIN_VIEW` değil) `/reports/{branchId}`'e
   gidiyor. Uygulama sırasında bulunan bir React lint hatası (`react-hooks/set-state-in-effect`, pathname
   değiştiğinde drawer'ı kapatan bir effect) kaldırıldı - kapama artık Link'in kendi `onClick`'inde.
3. **Dashboard** (`3853fb4`): minimal `/dashboard` - karşılama + `NAV_GROUPS`'un aynısından (Dashboard
   hariç) türetilen rol bazlı kısayol kart grid'i, KPI yok. Login yönlendirmesi `/branches`'ten
   `/dashboard`'a değişti.
4. **Migrasyon** (`b19f4a3`): 14 dosyanın tamamı (~13 sayfa + `reports/page.tsx`) `<StaffNav />`'ı
   `<AppShell>...</AppShell>` ile değiştirdi, dosya taşınmadı. Eski `StaffNav.tsx`/`.module.css` silindi.

**Kapsam içi küçük bir sadeleştirme:** spec'in önerdiği "Pickup / Siparişler" nav öğesi ayrı bir kalıcı
sidebar linki olarak eklenmedi - `app/pickup/[branchId]` bilinçli olarak kimliksiz/navsız bir kiosk board
(`PickupBoardController`, halka açık uç nokta), zaten `/branches` sayfasındaki mevcut "Pickup Board"
linkinden (yeni sekmede) erişiliyor ve hangi şubelerin `CUSTOMER_PICKUP` modelinde olduğunu AppShell'in
ucuza bilmesinin bir yolu yok. Bu, kullanıcıyla netleştirilen 2 kapsam kararının bir parçası değildi;
küçük bir uygulama detayı olarak burada not ediliyor.

**Doğrulama:** backend `mvn test` (tüm modüller) yeşil. Frontend'de her adımdan sonra `npx tsc --noEmit` +
`npx eslint .` + `npm run build` temiz. Canlı Chrome testi yapılmadı (proje hafızası - browser testi bu
projede kapalı). KDS (`/kitchen/[branchId]`) de AppShell'e sarıldı - Bölüm 19.3'ün "KDS normal admin CRUD
ekranı gibi tasarlanmaz" gereksinimi bu adımın kapsamında değil, Adım 4'te (kasa + KDS operasyonel UX)
ele alınacak.

## UI/UX Productization Gate — Adım 4: Kasa + KDS Operasyonel UX — ✅ COMPLETED

`product-requirements.md` Bölüm 19.5'in "Cross-cutting" uygulama sırasının 4. adımı: Bölüm 19.3'ün "Kasa"
ve "Kitchen Display System" alt-bölümleri. Mevcut `cashier/[branchId]` ve `kitchen/[branchId]` sayfaları
AppShell'e sarılı (Adım 3) ama kart içerikleri spec'in istediği bilgileri (masa, ödeme doğrulama, bekleme
süresi) taşımıyor ve KDS "büyük/dokunmatik/uzaktan okunabilir" değil - admin CRUD kartıyla aynı boyutta.

**Eksik/mevcut karşılaştırması (Bölüm 19.3):**
- Kasa kartı gereksinimi: masa ❌, sipariş no ✅, ödeme doğrulanmış bilgisi ❌, ne kadar süredir beklediği ❌,
  toplam tutar ✅, ürün/adet özeti ✅, Kabul birincil / Reddet kontrollü confirm flow ✅ (zaten var).
- KDS gereksinimi: büyük order/masa no ❌ (masa hiç yok, no küçük), sipariş yaşı ❌, yüksek okunabilir
  ürün/adet ✅ (kısmen), opsiyonların görsel ayrımı ~ (zayıf), büyük touch actions ~ (Button size=md),
  NEW/PREPARING/READY net grouping ❌ (item'lar API sırasıyla, aksiyon gerektiren en altta kalabiliyor),
  realtime durumu dikkat dağıtmadan ✅ (zaten metin, küçültülebilir).

**Backend — additive:** `CustomerOrder`'ın `tableVisitId`'i var ama iki DTO da (`OrderControlOrderResponse`,
`KitchenOrderResponse`) masa etiketini hiç taşımıyor, ne de bir zaman damgası. Zincir: `tableVisitId` ->
`TableVisit.tableId` (customersession) -> `RestaurantTable.label` (tenant) - ModuleBoundaryTest yalnızca
`.repository` paketine dışarıdan erişimi yasaklıyor, servis üzerinden okumak serbest ve `OrderingService`
zaten hem `CustomerSessionService` hem `TenantService`'a bağımlı. Eklenecekler:
- `CustomerSessionService.findTableVisit(UUID tableVisitId): Optional<TableVisit>` - `getOwnedTableVisit`in
  ownership kontrolsüz hali; çağıran zaten branch-yetkili personel, tableVisitId sır değil.
- `TenantService.findTable(UUID businessId, UUID tableId): Optional<RestaurantTable>`.
- `KitchenQueueOrderView`e `String tableLabel` eklenip `OrderingService.buildKitchenQueueView` içinde
  yukarıdaki iki servisle resolve edilecek (mevcut kod zaten bu metotta sipariş başına 2 ayrı sorgu
  yapıyor - items/options - aynı N+1 tarzına 2 sorgu daha eklemek tutarlı, batch optimizasyonu bu ölçekte
  YAGNI).
- `OrderControlOrderResponse`/`KitchenOrderResponse`'a `String tableLabel` + `Instant statusSince` (=
  `order.getLastActivityAt()` - bu iki liste yalnızca sırasıyla AWAITING_STORE_ACCEPTANCE/IN_KITCHEN
  durumundaki siparişleri döndürdüğü için `lastActivityAt` o duruma giriş anıyla aynı, ayrı bir immutable
  kolon eklemeye gerek yok).
- "Ödeme doğrulanmış" bilgisi için yeni alan **eklenmiyor**: `CustomerOrder.markAwaitingStoreAcceptance()`
  yalnızca AWAITING_PAYMENT'tan (yani doğrulanmış ödeme sonrası) çağrılabiliyor - kasa ekranındaki her
  sipariş zaten tanım gereği ödemesi doğrulanmış, statik bir rozet yeterli.

**Frontend:**
- `lib/api.ts`: `OrderControlOrder`/`KitchenOrder` tipine `tableLabel: string | null`, `statusSince: string`.
- Yeni `lib/time.ts`: `formatElapsedMinutes(iso, nowMs)` - Türkçe göreli süre ("3 dk", "1 sa 12 dk").
- Kasa: kart başlığına masa etiketi (büyük) + "Ödeme Alındı" (`Badge tone="success"`, statik - yukarıdaki
  invariant nedeniyle) + bekleme süresi rozeti (nötr <5dk, `warning` 5-10dk, `danger` >10dk) - `now` state'i
  15sn'de bir tick edip yalnız görünümü güncelliyor (refetch yok). Red formundaki çıplak `<select>`/`<input>`
  shared `Select`/`Textarea`'ya geçiyor; boş/hata durumları `EmptyState`/`ErrorState`'e geçiyor.
- KDS: masa+sipariş no büyük tipografi, sipariş yaşı rozeti (aynı eşik mantığı), opsiyonlar ürün adından
  görsel olarak ayrı bir chip/indent bloğunda, aksiyon butonları `size="lg"`, item'lar durum önceliğine göre
  sıralanıyor (PENDING_REVIEW/PREPARING üstte, READY/SERVED/REJECTED altta) - ayrı bir gruplama UI'ı
  eklemeden "net grouping" gereksinimini karşılıyor. Bağlantı durumu metinden küçük bir renkli noktaya
  düşüyor. `EmptyState`/`ErrorState` aynı şekilde entegre ediliyor.
- Kabul/red/decide/ready/served API çağrıları ve SSE refetch deseni değişmiyor (Bölüm 19.5 kriter 10: mevcut
  davranış bozulmaz).

**Doğrulama planı:** her backend değişikliğinden sonra `mvn test`; frontend'de `npx tsc --noEmit` +
`npx eslint .` + `npm run build`. Canlı Chrome testi yapılmayacak (proje hafızası).

**Sonuç:** tasarım plana göre uygulandı, iki commit'te:

1. **Backend**: `CustomerSessionService.findTableVisit` + `TenantService.findTable` eklendi,
   `KitchenQueueOrderView`e `tableLabel` eklenip `OrderingService.buildKitchenQueueView` içinde resolve
   edildi. `OrderControlOrderResponse`/`KitchenOrderResponse`e `tableLabel` + `statusSince` eklendi, her iki
   controller'ın `toResponse` mapping'i güncellendi. `OrderControlFlowIntegrationTest` ve
   `KitchenFlowIntegrationTest`e "Masa 1" etiketi ve dolu `statusSince` assertion'ları eklendi. Tam backend
   suite (`mvn test`, tüm modüller) yeşil.
2. **Frontend**: `lib/api.ts`'e `tableLabel`/`statusSince` alanları, yeni `lib/time.ts`
   (`formatElapsedMinutes`/`waitingUrgency` - nötr <5dk, `warning` 5-10dk, `danger` >10dk, 15sn'lik tick ile
   yalnız görünüm tazeleniyor, refetch yok). Kasa: masa etiketi + statik "Ödeme Alındı" rozeti + bekleme
   süresi rozeti, red formu artık shared `Select`/`Textarea`, boş/hata durumları `EmptyState`/`ErrorState`
   (Adım 1'in component kütüphanesinin staff-web'de ilk gerçek kullanımı - önceki adımlarda hiçbir sayfa
   bunları kullanmıyordu). KDS: masa+sipariş no büyük tipografi (`--font-size-2xl`), aynı bekleme rozeti,
   opsiyonlar ürün adından ayrı bir sol-border'lı chip'te, aksiyon butonları `size="lg"`, item'lar durum
   önceliğine göre sıralanıyor (PENDING_REVIEW/PREPARING üstte), bağlantı durumu metinden küçük renkli
   noktaya indirgendi. Kabul/red/decide/ready/served API sözleşmeleri ve SSE refetch deseni değişmedi.

**Doğrulama:** backend `mvn test` (tüm modüller) yeşil. Frontend `npx tsc --noEmit` + `npx eslint .` +
`npm run build` temiz. Canlı Chrome testi yapılmadı (proje hafızası - browser testi bu projede kapalı).

## UI/UX Productization Gate — Adım 5: Admin CRUD Component Refactor — ✅ COMPLETED

`product-requirements.md` Bölüm 19.5'in "Cross-cutting" uygulama sırasının 5. adımı: Bölüm 19.3'ün
"Admin / CRUD ekranları" alt-bölümü (kriter 4 ve 5). ~9 admin sayfası (branches, branches/[branchId],
staff, business-settings, announcements, audit, chain-comparison, refunds/[branchId], + menu/expenses)
hâlâ `admin.module.css`'in gayri-resmi `.header`/`.title` (PageHeader), `.list`/`.row` (Table) ve
`.form`/`.field`/`.input`/`.select` (Form) deseninde - Adım 1'de kurulan gerçek FormField/Input/Select/
Dialog/ConfirmDialog/EmptyState/ErrorState/Toast component'leri bu sayfalarda hiç kullanılmıyordu.
`menu`/`expenses` sayfaları da tek `page.tsx`'te 545/555 satır state+form+list mantığı taşıyordu.

Kullanıcıyla iki kapsam kararı netleştirildi:
1. **Kapsam:** Kasa/KDS (Adım 4'te ele alındı) ve Raporlar (Adım 6'da ele alınacak) hariç, admin CRUD
   yüzeyinin tamamı (~9 sayfa + menu/expenses split) bu adımda kapatıldı - kısmi bir geçiş bırakılmadı.
2. **Table tasarımı:** gerçek `<table>` semantiği (thead/tbody/th/td + `overflow-x:auto` wrapper) seçildi -
   mevcut `reports.module.css`'teki `.tableWrap`/`.table` deseniyle tutarlı, div-row listesinden daha
   erişilebilir (screen reader).

**Yeni shared component'ler (staff-web, `components/ui/`):** `PageHeader` (title+açıklama+primary action
slot - Bölüm 19.3: "sayfa başlığında title + açıklama + primary action pattern'i"), `Table` (yalnızca
`overflow-x:auto` wrapper + ortak th/td stili sağlayan ince bir sarmalayıcı - `<thead>`/`<tbody>` çağıran
sayfa tarafından yazılır, rijit bir `columns` prop API'si dayatılmadı çünkü ürün satırındaki genişleyen
düzenle/ata panelleri gibi durumlar `colSpan` sub-row gerektiriyor), `TableSkeleton` (Table yüklenirken
gösterilen 4 satırlık iskelet - 8+ sayfada tekrar ettiği için Adım 1'in tekil `Skeleton`'ından ayrı, küçük
bir component).

**Sayfa bazlı değişiklikler:**
- `audit`, `chain-comparison`: salt okunur, en basit sayfalar - yalnızca PageHeader + Table +
  EmptyState/ErrorState/TableSkeleton'a geçirildi, dialog/form yok.
- `branches`, `branches/[branchId]`, `staff`, `business-settings`, `announcements`: oluşturma formları
  artık PageHeader'ın primary action'ından açılan `Dialog` içinde (Bölüm 19.3: "gereksiz inline uzun form
  + liste yığını yerine drawer/modal"); listeler gerçek `Table`; tüm action feedback `Toast`'a taşındı
  (Adım 1'de kurulan ama o zamana kadar hiç kullanılmayan `ToastProvider`'ın ilk gerçek kullanımı).
  `branches/[branchId]`'daki şube ayarları (adres/saat dilimi/çalışma saatleri) düzenleme formu olduğu
  için Dialog'a taşınmadı, inline "iyi bölünmüş form pattern'i" olarak kaldı (spec'in izin verdiği
  alternatif). **ConfirmDialog** yalnızca gerçekten geri dönüşü olmayan aksiyonlara eklendi: QR kod iptali
  (fiziksel etiketi anında geçersiz kılıyor), personel devre dışı bırakma (`deactivateStaffUser`'ın
  reaktive uç noktası yok). İki yönlü toggle'lar (sipariş açık/kapalı, teslimat modeli, rapor alıcısı
  aktif/pasif, duyuru sonlandırma) confirm'süz kaldı - düşük risk.
- `refunds/[branchId]`: sipariş kartı düzeni gerçek bir liste olmadığı için Table'a zorlanmadı (tekil bir
  "makbuz" görünümü); yalnızca PageHeader + shared Input + Toast'a geçirildi. "İade Başlat" gerçek para
  hareketi yapan geri alınamaz bir işlem olduğu için ConfirmDialog arkasına alındı; "Teslim Edildi/Alındı"
  destructive olmadığı için değişmedi.
- `menu` (545 → 68 satır) ve `expenses` (555 → 69 satır) feature/component'lere bölündü:
  - `menu/features/`: `CategoriesSection` (Table+Dialog), `ProductsSection` (Table+Dialog+şube seçici),
    `ProductRow` (müsaitlik/aktiflik toggle'ları + düzenle/şubelere-ata genişleyen `colSpan` panelleri,
    kendi API çağrılarını yönetip günceli parent'a callback ile bildiriyor).
  - `expenses/features/`: `ExpenseCategories`, `ExpenseForm` (Dialog'lu oluşturma), `ExpenseList` (filtre
    formu + Table + gönder/onayla/reddet), `RecurringTemplates` (Dialog'lu oluşturma + Table). Gider
    ekleme sonrası `ExpenseList`'in tazelenmesi, sayfa orkestratöründen geçilen bir `refreshToken` sayaç
    prop'uyla sağlanıyor (state'i tam yukarı taşımak yerine YAGNI bir çözüm). Kategori/şablon devre dışı
    bırakma ConfirmDialog ister (reaktive uç noktası yok).
  - Her iki `page.tsx` da artık yalnızca üst düzey seçim/context state'ini tutan ince bir orkestratör.

**Teknik not:** `react-hooks/set-state-in-effect` lint kuralı, `useEffect` içinde senkron `setState`
çağrısına izin vermiyor - birkaç sayfada (`audit`, `chain-comparison`, `menu`/`ProductsSection`) mount
effect'inin başındaki `setLoading(true)`/`setError(null)` sıfırlamaları bu yüzden kaldırıldı; `loading`
zaten `useState(true)` ile başlıyor, `error` yalnızca `.catch`'te set ediliyor - retry'da skeleton tekrar
görünmüyor ama bu, kod tabanındaki mevcut (`cashier`) davranışla tutarlı.

**Doğrulama:** backend değişikliği yok (bu adım tamamen staff-web frontend). Her commit'ten sonra
`npx tsc --noEmit` + `npx eslint .` + `npm run build` temiz. Canlı Chrome testi yapılmadı (proje hafızası).

## UI/UX Productization Gate — Adım 6: Reporting/Dashboard Visualization — ✅ COMPLETED

`product-requirements.md` Bölüm 19.5'in "Cross-cutting" uygulama sırasının 6. adımı: Bölüm 19.3'ün
"Dashboard" ve "Raporlama" alt-bölümleri (kriter 6: "Rapor ekranı dashboard seviyesinde bilgi
hiyerarşisine sahiptir"). Adım 5'te bilinçli olarak bu adıma bırakılan üç sayfa: `dashboard` (yalnızca
rol bazlı kısayol kartları taşıyan bir placeholder), `reports` (zincir) ve `reports/[branchId]` (şube) -
ikisi de KPI'ları ham `reports.module.css` `.statCard`/`.table` deseninde gösteriyordu; tarih aralığı
seçimi yalnızca serbest metin `type="date"` alanları + "Uygula" butonuydu (hızlı preset yok); ürün/
kategori/şube verisi yalnızca tablo satırı olarak vardı (görsel ranking/trend yok).

**Yeni shared component'ler (staff-web, `components/ui/`):** `KpiCard` (label+value+opsiyonel hint,
`tone` prop'u refund/net-sonuç gibi dikkat gerektiren değerleri `danger`/`success` rengiyle vurgular -
`reports.module.css`'teki ad-hoc StatCard'ın shared hali), `DateRangePresets` (Bölüm 19.3: "hızlı tarih
presetleri: Bugün / Dün / Bu Hafta / Bu Ay / Özel" - preset butonları + özel aralık için `FormField`+
`Input` tarih alanları; `presetRange()` saf fonksiyonu dashboard'da da "bugün" aralığını hesaplamak için
kullanılıyor), `BarList` (Bölüm 19.3: "gelir trendi", "ürün/kategori ranking", "branch comparison" -
CSS genişlikli yatay bar listesi; spec'in "grafik için ağır bir framework eklenmesi zorunlu değildir"
notu gereği harici bir chart kütüphanesi eklenmedi, tamamen bağımlılıksız/küçük bir çözüm).

**Sayfa bazlı değişiklikler:**
- `reports` (zincir): `PageHeader` + `DateRangePresets` (preset seçilince anında yeniden yükleniyor,
  ayrı "Uygula" butonu yok) + `KpiCard` grid (toplam brüt/net satış, refund - `>0` ise `danger` tone,
  toplam sipariş) + brüt satışa göre sıralı şube `BarList` + detay için Adım 5'in `Table` component'i
  (satırlar hâlâ `/reports/{branchId}`'ye bağlı).
- `reports/[branchId]` (şube): aynı `PageHeader`+`DateRangePresets`+`KpiCard` grid deseni (8 KPI: brüt/
  net satış, refund toplamı, sipariş/kabul/red sayısı, ortalama sepet, masa ziyareti). **Günlük ciro
  trendi** yeni bir backend endpoint'i gerektirmeden, mevcut gün sonu kapanış kayıtlarından (`Gün Sonu
  Kapanışları` bölümünün zaten çektiği `DailyCloseReport[]`) türetilen kronolojik bir `BarList` olarak
  eklendi (gate kuralı: "backend business logic yeniden yazılmaz"). Ürün bazında satış artık hem ilk 8'i
  gösteren ciro sıralı bir `BarList` (adet bilgisi `valueLabel` içinde) hem de -Adım 5'te zaten var olan-
  tam `Table`; kategori bazında ciro tablosu tek değerli olduğu için doğrudan `BarList`'e çevrildi (ayrı
  bir tablo tutulmadı - gereksiz tekrar). Saatlik dağılım kronolojik bir zaman serisi olduğu için `Table`
  olarak kaldı (ranking değil). Yönetimsel net sonuç bölümü `KpiCard` grid'e taşındı; negatif net sonuç
  `danger` tone ile vurgulanıyor.
- `dashboard`: placeholder'a gerçek "Bugün" KPI bölümü eklendi - `Permission.REPORT_VIEW`'ı olmayan
  `KITCHEN_STAFF` hariç her rol için (`StaffRole.java`'daki `permissions()` ile birebir, `lib/staffNav.ts`
  zaten aynı rol listesini nav filtrelemesinde kullanıyordu). Zincir geneli rol (`BUSINESS_ADMIN`/
  `PLATFORM_ADMIN`, `isBusinessWide()`) `getChainSalesReport(today, today)` ile toplam KPI'lar + şube
  sıralama `BarList` görür; tek şubeye bağlı rol (`BRANCH_MANAGER`/`CASHIER`) kendi ilk şubesinin
  `getBranchSalesReport(today, today)` KPI'larını + en çok satan ürünler `BarList`'ini + (Bölüm 19.3
  "Dashboard": "aktif/bekleyen operasyon bilgileri") `getPendingAcceptanceOrders(branchId).length`'ten
  gelen "onay bekleyen sipariş" KPI'sını (`>0` ise `danger` tone) görür. Rol bazlı kısayol kartları
  (Adım 3'ten beri var olan `NAV_GROUPS` kaynaklı bölüm) değişmeden KPI bölümünün altında kalıyor.

**Kapsam dışı bırakılanlar (bilinçli):** Excel export, gün sonu kapanış (FINAL) akışı ve owner-notification
yeniden gönderme mevcut haliyle korundu - bunlar zaten Gap-analysis #9/#11'de tamamlanmış, bu adımın
konusu (kriter 6) yalnızca bilgi hiyerarşisi/görselleştirme. Zincir raporunda ürün/kategori ranking
eklenmedi çünkü `ChainSalesReport` backend'de yalnızca şube bazlı toplamlar taşıyor, şubeler arası
birleştirilmiş ürün kırılımı yok - yeni bir agregasyon endpoint'i eklemek gate'in "backend business logic
yeniden yazılmaz" kuralına aykırı olurdu; bu, ileride gerçek bir ihtiyaç çıkarsa ayrı bir gap olarak ele
alınabilir.

**Doğrulama:** backend değişikliği yok (bu adım tamamen staff-web frontend, yalnızca mevcut API'ları farklı
şekilde birleştirdi). Her commit'ten sonra `npx tsc --noEmit` + `npx eslint <path>` + `npm run build`
temiz. Canlı Chrome testi yapılmadı (proje hafızası).

## UI/UX Productization Gate — Adım 7: Responsive/Accessibility/Browser E2E Pass — ✅ COMPLETED

`product-requirements.md` Bölüm 19.5'in "Cross-cutting" sırasının son adımı: Bölüm 19.4 (Responsive
davranış) ve kriter 9 ("Gerçek Chrome'da customer için 390x844, staff için desktop ve KDS için büyük
ekran viewport'unda kritik flow'lar manuel/E2E doğrulanır"). Bu adımdan itibaren canlı Chrome testi
kullanıcı onayıyla tekrar açıldı (önceki oturumlarda token tasarrufu için kapatılmıştı).

**Test ortamı:** `infra/docker-compose.yml` container'ları (backend/customer-web/staff-web) Adım 6 ve
öncesi bazı backend commit'lerinden (`tableLabel`/`statusSince`) daha eskiydi - `docker compose up -d
--build` ile üçü de güncel koda göre yeniden build edilip ayağa kaldırıldı. E2E için `/internal/
businesses/{id}/staff-users` bootstrap endpoint'i ile geçici bir `BUSINESS_ADMIN` test hesabı
(`e2e-test@qrmenu.local`) oluşturuldu; test verisi olarak zaten DB'de bulunan "Test Restoran / Merkez
Şube" (3 ürünlü) kullanıldı.

**Viewport metodolojisi:** Chrome uzantısının `resize_window` aracı bu ortamda pencereyi 390px gibi dar
genişliklere indiremedi (gözlenen minimum ~1024px) - bu yüzden dar viewport'lar (360/390/414/430/768)
için sayfa içine tam istenen CSS genişliğinde (`width:390px` vb.) bağımsız bir `<iframe>` enjekte edilip
o iframe içinde gezinildi; iframe kendi `window.innerWidth`'ine sahip olduğundan gerçek Chrome'da gerçek
bir responsive viewport'u temsil ediyor (aynı origin, aynı çerezler/oturum). >=1024px gerektiren desktop
ve kiosk testleri gerçek pencerede (o an ~1800px genişlik) doğrudan yapıldı.

**Customer (Bölüm 19.4 + kriter 9):**
- 390x844'te tam akış uçtan uca doğrulandı: menü → ürün detay bottom-sheet → sepete ekle → sepet →
  ödeme (mock provider) → "Ödeme başarılı" → sipariş takip (durum zaman çizelgesi) → makbuz. Her adımda
  yatay overflow yok, tüm CTA'lar (Sepete Ekle/Ödemeye Geç/Ödemeyi Onayla/Sipariş durumunu takip et)
  erişilebilir.
- 360/390/430/768/1280 genişliklerinde hem menü hem tracking/makbuz sayfaları için otomatik
  `scrollWidth > clientWidth` taraması yapıldı - hiçbirinde yatay overflow bulunmadı.

**Staff desktop (kriter 9):** gerçek pencerede (~1800px, >=1024px eşiğini karşılıyor) login →
dashboard (Adım 6 KPI içeriği canlı veriyle doğrulandı) → Kasa (tableLabel "Masa 1" + statusSince "2 dk
bekliyor" doğru render edildi, Kabul Et çalıştı) → Mutfak/KDS (financial summary + Onayla akışı
çalıştı) → Satış Raporları (zincir) → Şube Raporu → Menü Yönetimi akışları tek tek gezildi, hepsi sidebar
+ desktop layout ile bekleneni verdi.

**Staff 768-1023px (kriter: "compact/collapsible navigation"):** sidebar hamburger ikonuna daralıyor,
tıklanınca backdrop'lu bir overlay olarak açılıyor - `AppShell.module.css`'teki `@media (max-width:
1023px)` kuralıyla tutarlı, ayrı bir doğrulama gerektirmedi.

**Staff <768px (kriter: "drawer navigation ve tek kolon kullanılabilir yönetim ekranları"):** 414px'te
dashboard/reports/menu/cashier/kitchen/expenses/branches sayfalarının hepsi tek kolona düşüyor, sayfa
genelinde yatay overflow yok. Menü yönetimi tablosunda (ve diğer geniş tablolarda) aksiyon butonları
(Kaldır/Pasif Yap/Düzenle/Şubelere Ata) dar ekranda görünür alanın dışına taşıyor ama bu, Adım 1/5'te
kurulan shared `Table` component'inin kendi `overflow-x: auto` wrapper'ı sayesinde - sayfa değil, yalnızca
tablo yatay kaydırılarak erişiliyor; bilinçli/mevcut bir pattern, kriterin aradığı "erişilemeyen CTA"
durumu değil.

**KDS kiosk/büyük ekran (kriter 9 + Bölüm 19.4: "KDS ayrıca büyük ekran/kiosk viewport'unda test edilir")
- bulunan sorun ve düzeltme:** Mutfak sayfası diğer admin sayfalarıyla aynı `--container-width-desktop`
(1080px) üst sınırını paylaşıyordu; 1920px'lik bir kiosk ekranında bile sipariş kartı grid'i yalnızca
~3 sütuna sığıyor, ekranın geri kalanı boş kalıyordu - kalabalık bir mutfakta aynı anda görülebilecek
sipariş sayısını gereksiz kısıtlıyordu. **Düzeltme:** `app/globals.css`'e yalnızca bu sayfa için
kullanılan `--container-width-kiosk: 1920px` token'ı eklendi, `app/kitchen/[branchId]/page.module.css`
`.page`'in `max-width`'i buna çekildi. 1920px'te grid artık 4 sütuna kadar genişleyebiliyor (DOM'a geçici
kart klonları eklenerek görsel olarak doğrulandı). Kasa (cashier) sayfası aynı deseni kullanıyor ama
kriter yalnızca KDS'i kiosk için özel olarak işaret ettiğinden kapsam dışı bırakıldı.

**Diğer bulgular:** Yok - customer ve staff akışlarının geri kalanında yatay overflow, üst üste binen
sticky alan veya erişilemeyen CTA gözlenmedi.

**Doğrulama:** KDS düzeltmesi sonrası `npx tsc --noEmit` + `npm run build` (staff-web) temiz;
`docker compose up -d --build` ile container yeniden oluşturulup değişiklik gerçek Chrome'da tekrar
doğrulandı. Backend değişikliği yok.

---

## UI/UX Productization Gate — Adım 1 Tasarım Yenilemesi: "Tide" kimliği — ✅ COMPLETED

Gate'in 7 adımı da tamamlanmış olsa da, Adım 1'de kurulan token sistemi (bkz. yukarıdaki "Adım 1: Shared
Frontend Component Library") tamamen nötr gri/siyah-beyazdı - hiçbir renk kişiliği taşımıyordu (`--color-
primary` doğrudan `--color-fg` ile aynıydı). `frontend-design` skill süreciyle (brainstorm → kritik →
uygula) bilinçli bir görsel kimlik kuruldu; kapsam yalnızca **token katmanı + component-library dosyaları**
ile sınırlı tutuldu, sayfa dosyalarına dokunulmadı (Adım 2-7'nin wiring'i korunur).

**Tasarım kararı:** Tek cesur hamle - tüm primary aksiyon/link/focus-ring için tek bir "Tide" camgöbeği
accent (`#0e7c86` light / `#4fd0c6` dark), success (yeşil, hue ~140) ve danger (kırmızı, hue ~6)
tonlarından hue olarak bilinçli uzaklıkta tutuldu (karışma riski yok). Zemin nötr beyaz/ink kaldı (Bölüm
19.1 klişe uyarısı: sıcak krem zemin + serif + terracotta üçlüsünden kaçınıldı - burada sans-serif + neredeyse
beyaz zemin + tek camgöbeği accent var). İnk/paper/surface tonları hafifçe camgöbeği ailesine doğru
tintlendi (`--color-fg: #14201e`, `--color-surface: #f2f4f3`) - tam nötr gri yerine daha tutarlı bir
palet. Yeni **additive** token: `--color-primary-strong` (hover/active için, önceki blanket
`opacity:0.85` yerine).

**Tipografi:** `next/font/google` ile build-time'da self-host edilen iki roldü bir eşleşme - başlıklar
(`h1/h2/h3` + `KpiCard.value`) için Plus Jakarta Sans (600/700/800, karakterli ama ölçülü), gövde/form/
tablo için Inter (yoğun staff-web tabloları için en okunur seçenek). `subsets: ["latin","latin-ext"]` ile
Türkçe karakterler (ç ğ ı ö ş ü) garanti altında. next/font runtime'da CDN isteği atmadığından Bölüm
19.1'in "remote CDN'e bağımlı olmayan modern sans-serif" şartı korunuyor. Spacing/radius/font-size skalası
**değişmedi** - mevcut sayfa layout'larının kırılma riski sıfıra indirildi, yalnızca renk + tipografi +
focus-ring imzası değişti.

**Component değişiklikleri (yalnızca):** `Button.module.css` `.primary:hover/:active`'te yeni
`--color-primary-strong` kullanımı (her iki app), `KpiCard.module.css` `.value`'ye display font +
`font-variant-numeric: tabular-nums`. Diğer tüm component'ler zaten %100 token-driven olduğundan (hiçbir
`.module.css`'te var() dışı hex/rgb bulunmadı) globals.css değişikliği otomatik cascade etti, dosya
başına ek değişiklik gerekmedi.

**Doğrulama:** her iki app'te `npm run lint` + `npm run build` temiz. Canlı Chrome testi yapıldı (Adım
7'den beri proje hafızasında tekrar açık): customer-web `localhost:3001` (port 3000 Docker'a ait) 390x844
placeholder sayfasında display/body font ayrımı doğrulandı; staff-web için `3002` portunun Docker
container'ına ait olduğu, yerel değişiklikleri yansıtmadığı fark edildi - yerel doğrulama için `3010`
portunda ayrı bir `next dev` başlatıldı, `/` (Personel Girişi) sayfasında Tide accent buton, hover/
focus-ring ve başlık display fontu doğru render edildiği görüldü. Dark-mode (`prefers-color-scheme`)
paleti de aynı prensiple güncellendi ama PRD 19.1 öncelik sırasına göre (light theme birincil) ek
doğrulama yapılmadı.

---

## UI/UX Productization Gate — Adım 2 Tasarım Yenilemesi: müşteri sipariş akışı — ✅ COMPLETED

Gate'in "Cross-cutting" sırasının 2. adımı (customer-web productization). Adım 2'nin işlevsel kapsamı
(emoji placeholder kaldırma, ürün kartı görsel boyutu, sipariş takip timeline'ı, oturum süresi dolma
ekranı - bkz. yukarıdaki "Adım 2: Customer Web Productization") daha önce tamamlanmıştı, ama Adım 1'in
"Tide" kimliği yalnızca token katmanına kadar indi - `app/t/[token]` ve `app/order/track/[token]`
altındaki sayfa-seviyesi component'ler otomatik cascade dışında hiç dokunulmamıştı. Bu adımda
`frontend-design` skill süreciyle (Adım 1 Tasarım Yenilemesi ile aynı yöntem) bu iki akışın kendi sayfa
component'lerine (VisitHeader, ProductCard, CategoryNav, OrderStatusTimeline, tracking page) restrained
bir tasarım geçişi uygulandı. Kapsam yalnızca bu component'lerle sınırlı tutuldu; shared `components/ui/`
kütüphanesine (Adım 1'in kapsamı) veya `page.tsx` orkestrasyon mantığına dokunulmadı.

**Tasarım kararı - imza öğesi:** QR okutulduktan sonra görülen ilk ekran olan `VisitHeader`'a, işletme
adının altında kısa bir gradient çizgi (`--color-primary` → `--color-primary-strong` → transparent, 56px)
eklendi - "Tide" (gelgit) kimliğini isim düzeyinden görsel bir imzaya taşıyan tek bilinçli risk. Geri kalan
her yer kasıtlı olarak sakin bırakıldı (Chanel prensibi - "spend boldness in one place"):

1. **ProductCard** - hover'da `--shadow-sm` derinliği eklendi; görselsiz ürünlerde nötr placeholder artık
   düz gri değil, `color-mix()` ile hafif camgöbeği tintli bir zemin üzerinde `--color-primary` renginde
   ikon (placeholder artık "bozuk görsel" değil bilinçli bir öğe gibi okunuyor).
2. **CategoryNav** - aktif kategori chip'ine `--shadow-sm` eklendi (dokunsal kaldırma hissi).
3. **OrderStatusTimeline** (sipariş takip - akışın duygusal karşılığı olan an) - mevcut adımın marker'ına
   yavaş genişleyip solan bir "canlı" pulse halkası eklendi (Bölüm 19.2: "anlamlı durum kartı/timeline/
   progress pattern'i" - salt statik bir log girdisi değil, şu an gerçekten oluyor hissi). `PAYMENT_FAILED`
   durumunda pulse devre dışı bırakıldı (kırmızı marker + camgöbeği halka karışmasın diye).
   `prefers-reduced-motion` zaten globals.css'teki blanket kural ile karşılanıyor, ek kod gerekmedi.
4. **Sipariş takip başlığı** - `<p>Sipariş No: #N</p>` semantik olarak `<h1>`'e çevrildi; hem doğru
   doküman hiyerarşisi hem de globals.css'teki `h1,h2,h3{font-family:var(--font-family-display)}`
   kuralından otomatik olarak display font'u kazandı.

**Bulunan/düzeltilen hata:** `.tideLine` başta `<span>` üzerinde `display: inline` ile bırakılmıştı - inline
element'ler `width`/`height`'ı yok sayar, bu yüzden ilk canlı testte çizgi hiç görünmedi. `display: block`
eklenerek düzeltildi; bu, sonraki bir sayfa-seviyesi tasarım geçişinde aynı hatayı tekrarlamamak için not
edilmeye değer bir CSS tuzağı.

**Doğrulama:** `npm run lint` + `npm run build` (tsc dahil) customer-web'de temiz. Canlı Chrome testi
yapıldı - bu adımda Docker'ın `infra-customer-web-1` container'ı 3000 portunu (backend CORS'un izin
verdiği tek origin'lerden biri, `CorsConfig.java`) tuttuğundan, container geçici olarak durdurulup yerine
`npm run dev -- -p 3000` ile yerel bir sunucu başlatıldı (backend `localhost:8080` Docker'da zaten
ayaktaydı, DB'den gerçek bir QR token - "Test Restoran" işletmesi, 3 ürün - `psql` ile bulundu). 390x900
viewport'ta uçtan uca akış doğrulandı: menü (tide-line + kart hover/placeholder tint) → ürün detay bottom
sheet → sepete ekle → sepet drawer → ödeme (mock) → "Ödeme başarılı" → sipariş takip (h1 başlık + pulse
halkası görsel olarak zoom ile doğrulandı, adım 2/5 "İşletme onayı" current state'te). Test sonunda
`infra-customer-web-1` container'ı yeniden başlatıldı, yerel `next dev` kapatıldı. Işık temasında ayrı bir
doğrulama yapılmadı (Chrome sistem teması dark idi, JS ile `prefers-color-scheme` override edilemiyor) -
düşük risk kabul edildi çünkü tüm yeni stiller mevcut `--color-*` token'larını kullanıyor (Adım 1'de her
iki tema için de zaten doğrulanmıştı), hiçbir yeni hardcoded renk eklenmedi. Backend değişikliği yok.

---

## UI/UX Productization Gate — Adım 3 Tasarım Yenilemesi: staff-web application shell — ✅ COMPLETED

Gate'in "Cross-cutting" sırasının 3. adımı (staff-web application shell/sidebar). Adım 3'ün işlevsel
kapsamı (AppShell component'i, sol sidebar + top bar + drawer, rol bazlı nav filtreleme - bkz. yukarıdaki
"Adım 3: Staff Web Application Shell/Sidebar") daha önce tamamlanmıştı, ama Adım 1'in "Tide" kimliği
`AppShell`'e de yalnızca token cascade'i kadar indi - hiçbir sayfa/shell-seviyesi component'e bilinçli bir
tasarım geçişi uygulanmamıştı. Bu adımda `frontend-design` skill süreciyle (Adım 1/2 Tasarım Yenilemesi
ile aynı yöntem) yalnızca `components/layout/AppShell.tsx` + `.module.css`'e restrained bir geçiş
uygulandı. Kapsam bilinçli olarak yalnızca shell chrome'uyla sınırlı tutuldu - `app/dashboard/page.tsx`
(Adım 6'da gerçek KPI içeriğiyle dolduruldu, kendi tasarım yenilemesi ayrı bir adımda ele alınacak) ve
`styles/admin.module.css` (Adım 5'in kapsamı) bu geçişin dışında bırakıldı.

**Tasarım kararı - imza öğesi:** Sidebar'daki aktif sayfa linkine, customer-web `VisitHeader`'daki
"tide line" imzasını (Adım 2 Tasarım Yenilemesi) shell'in kendi diline çeviren dikey bir "tide marker"
eklendi - linkin sol kenarında 3px, `--color-primary` → `--color-primary-strong` gradient'li dikey bir
çubuk. Önceki `.active` stili (düz `--color-primary` dolgu + beyaz metin) yerini yumuşak bir tint'e
(`color-mix(in srgb, var(--color-primary) 12%, transparent)`) ve `--color-primary` renkli/yarı-kalın
metne bıraktı - bu, bir mesai boyunca sürekli görünen shell'de "aktif blok" yerine "buradasın" hissi veren
daha sakin bir wayfinding pattern'i (Linear/Vercel tarzı sidebar'lardaki tanıdık dil). Geri kalan her yer
sakin bırakıldı:

1. **Brand** (`QR Menü` wordmark) - `--font-family-display` + `-0.01em` letter-spacing kazandı (h1-h3'ün
   zaten aldığı display font kimliği shell'in kendi markasına da taşındı).
2. **Top bar context** - `businessName` artık `--color-fg` + yarı-kalın, şube adları ondan sonra
   `--color-fg-muted` kalıyor (`design-verify@qrmenu.local` test hesabıyla "Test Restoran" / şube adı
   hiyerarşisi doğrulandı) - önceden ikisi de aynı tondaydı, iş yeri adı artık görsel olarak öne çıkıyor.

**Doğrulama:** `npx tsc --noEmit` + `npx eslint` + `npm run build` (staff-web) temiz. Canlı Chrome testi
yapıldı - Docker'ın `infra-staff-web-1` container'ı 3002 portunu (backend CORS'un izin verdiği tek
origin'lerden biri) tuttuğundan, container geçici durduruldu, yerine `npx next dev -p 3002` başlatıldı.
`/internal/businesses/{id}/staff-users` bootstrap endpoint'iyle geçici bir `BUSINESS_ADMIN` test hesabı
(`design-verify@qrmenu.local`, "Test Restoran") oluşturuldu, giriş yapılıp Dashboard → Şubeler arası
gezinildi: tide marker aktif linkte doğru render edildi, sayfa değiştikçe doğru linke taşındı (routing
mantığı bozulmadı - business-wide rol için "Kasa" linki beklendiği gibi `/branches` seçiciye yönlendirdi),
top bar hiyerarşisi ve display-font brand doğru göründü, konsolda hata yok. Test sonunda test hesabı +
session'ı DB'den silindi, yerel `next dev` kapatıldı, `infra-staff-web-1` container'ı yeniden başlatıldı.
Işık temasında ayrı doğrulama yapılmadı (Adım 1/2 ile aynı düşük-risk gerekçesi - yalnızca mevcut
`--color-*` token'ları kullanıldı). Backend değişikliği yok.

## UI/UX Productization Gate — Adım 4 Tasarım Yenilemesi: kasa + KDS — ✅ COMPLETED

Gate'in "Cross-cutting" sırasının 4. adımı. Adım 4'ün işlevsel kapsamı (masa etiketi, statik "Ödeme
Alındı" rozeti, bekleme süresi rozeti, KDS büyük tipografi/sıralama - bkz. yukarıdaki "Adım 4: Kasa + KDS
Operasyonel UX") daha önce tamamlanmıştı, ama o adımın kendisi "büyük/dokunmatik/uzaktan okunabilir"
gereksinimini yalnızca tipografi/spacing ile karşılamıştı - urgency (bekleme süresi) hâlâ sade bir kart
kenarlığı renk değişimiyle (`border-color`) anlatılıyordu, Adım 1'in "Tide" kimliğiyle hiç bağı yoktu. Bu
adımda `frontend-design` skill süreciyle (Adım 1/2/3 ile aynı yöntem) yalnızca `app/cashier/[branchId]/`
ve `app/kitchen/[branchId]/` sayfalarının kendi `page.module.css`'lerine restrained bir geçiş uygulandı -
`page.tsx`'lerde hiçbir değişiklik yok (salt CSS, urgency mantığı zaten `lib/time.ts`'in ürettiği
`waitingUrgency` sınıf adına bağlı).

**Tasarım kararı - imza öğesi: "tide edge".** Her sipariş kartının sol kenarına, AppShell sidebar'ının
"tide marker" imzasını (Adım 3 Tasarım Yenilemesi) yeniden yorumlayan dikey bir çubuk eklendi - ama burada
çubuğun *yüksekliği* de anlam taşıyor: bekleme süresi arttıkça (aynı normal/warning/danger eşikleri,
`waitingUrgency`) çubuk hem uzuyor (28px → 56px → 84px kasada, 32px → 64px → 96px KDS'te) hem rengi
sakin tealden (`--color-primary` → `--color-primary-strong`) amber'e, oradan kırmızıya kayıyor
(`color-mix` ile koyulaştırılmış gradient). Önceki `border-color` değişimi kaldırıldı - artık urgency tek
bir yerde, "yükselen gelgit" metaforuyla anlatılıyor: shell'in "buradasın" işareti burada "ne kadar
bekledi" işaretine dönüşüyor, aynı görsel dilin iki farklı anlamı. Geri kalan her yer sakin bırakıldı:

1. **Bağlantı durumu noktası** - "Canlı" durumdayken customer-web sipariş takip zaman çizelgesindeki
   (Adım 2 Tasarım Yenilemesi) `timeline-pulse` deseniyle aynı yumuşak halka animasyonunu kazandı; yeni
   bir motif icat edilmedi, var olan "şu an oluyor" sinyali yeniden kullanıldı.
2. **KDS finansal özet değerleri** (bugün brüt/net satış, sipariş sayısı) `--font-family-display`'e geçti
   - marka kimliğinin sayısal verilere de taşınması, Adım 3'te brand wordmark'a uygulanan aynı mantık.

**Doğrulama:** `npx tsc --noEmit` + `npx eslint .` + `npm run build` (staff-web) temiz. Canlı Chrome
testi yapıldı - `infra-staff-web-1` container'ı geçici durdurulup yerine `npx next dev -p 3002` başlatıldı.
Gerçek bir sipariş/ödeme akışı simüle etmek yerine (kart görselleri salt CSS/urgency sınıfına bağlı olduğu
için orantısız olurdu), Postgres'e doğrudan 3 test siparişi (aynı masa, üç farklı `last_activity_at`: az
önce/7dk/15dk) seed edildi - önce kasada `AWAITING_STORE_ACCEPTANCE`, ekran görüntüsü alındıktan sonra
aynı siparişler `IN_KITCHEN`'a çevrilip KDS'te tekrar görüntülendi. Her iki ekranda üç tide-edge kademesi
(kısa teal → orta amber → uzun kırmızı) ve "Canlı" noktasının pulse halkası doğru render edildi, konsolda
hata yok. Test sonunda seed edilen sipariş/masa ziyareti/anonim oturum satırları ve geçici
`design-verify@qrmenu.local` hesabı + session'ı DB'den silindi, yerel `next dev` kapatıldı,
`infra-staff-web-1` container'ı yeniden başlatıldı. Işık temasında ayrı doğrulama yapılmadı (Adım 1/2/3
ile aynı düşük-risk gerekçesi). Backend değişikliği yok.

## UI/UX Productization Gate — Adım 5 Tasarım Yenilemesi: admin CRUD component library — ✅ COMPLETED

Gate'in "Cross-cutting" sırasının 5. adımı (admin CRUD component refactor). Adım 5'in işlevsel kapsamı
(PageHeader/Table/TableSkeleton/Dialog/ConfirmDialog/EmptyState/ErrorState + Toast'un ~9 admin sayfası +
menu/expenses'te kullanılması - bkz. yukarıdaki "Adım 5: Admin CRUD Component Refactor") daha önce
tamamlanmıştı; Adım 1 Tasarım Yenilemesi'nin token değişikliği bu component'lere otomatik cascade etti
(hepsi %100 token-driven), ama Adım 2/3/4'te olduğu gibi bilinçli, sayfaya özgü bir imza öğesi hiç
eklenmemişti. Bu adımda `frontend-design` skill süreciyle (Adım 1-4 ile aynı yöntem) yalnızca
`components/ui/Dialog.{tsx,module.css}`, `ConfirmDialog.tsx` ve `Table.module.css`'e restrained bir
tasarım geçişi uygulanıyor. Kapsam bilinçli olarak yalnızca bu paylaşılan component'lerle sınırlı - hiçbir
admin sayfasına (`page.tsx`) veya sayfa-seviyesi `.module.css`'e dokunulmuyor (Adım 5'in wiring'i korunur).

**Tasarım kararı - imza öğesi: "gate stripe".** Admin CRUD akışının duygusal karşılığı, sipariş takibindeki
pulse halkası veya kasa/KDS'teki tide-edge'in aksine tek bir an değil - her `Dialog`/`ConfirmDialog`
açıldığında (bir veri değişikliğine "kapı açılıyor" anı). Dialog yüzeyinin üst kenarına 3px'lik bir gradient
şerit eklendi: rutin aksiyonlarda (oluştur/düzenle) sakin Tide gradient'i (`--color-primary` →
`--color-primary-strong`), geri dönüşü olmayan aksiyonlarda (`ConfirmDialog tone="danger"` - QR iptali,
personel devre dışı bırakma, iade başlatma, kategori/şablon devre dışı bırakma) `--color-danger` → koyulaştırılmış
kırmızı gradient'i (Adım 4'ün tide-edge'indeki "renk = risk" dilinin CRUD'a çevirisi - kullanıcı metni
okumadan önce, dialog'un rengi zaten "bu geri alınamaz" sinyalini veriyor). `Dialog` component'ine yeni
opsiyonel `tone?: "default" | "danger"` prop'u eklendi, `ConfirmDialog` kendi `tone` prop'unu olduğu gibi
alt bileşene iletiyor - sıradan `Dialog` kullanımları (create/edit formları) hiçbir şey geçmediği için
otomatik `default` kalıyor.

**İkinci, sakin dokunuş - Table row hover.** Sidebar'ın "tide marker" imzasını (Adım 3 Tasarım Yenilemesi)
yeni bir motif icat etmeden tabloya taşıyan ince bir sol kenar çubuğu: `tbody tr:hover`'da 2px
`--color-primary` kenarlık + `color-mix()` ile hafif zemin tonu - çoğu admin tablosunun satır-içi aksiyonlar
taşıdığı düşünülürse (düzenle/sil/genişlet), "hangi satırla etkileşimdesin" wayfinding'i shell'deki "buradasın"
diliyle tutarlı hale geliyor. `PageHeader`/`EmptyState`/`ErrorState`/`Toast` bilinçli olarak sakin bırakıldı -
zaten Adım 1'in token cascade'iyle display font + renk kimliğini taşıyorlar, ek bir öğe Chanel prensibini
ihlal eder.

**Uygulanan değişiklikler:** `Dialog.module.css`'e `.dialog::before` ile 3px gradient şerit (`--color-primary`
→ `--color-primary-strong`) + `.danger::before` override (`--color-danger` → `color-mix(... 65% black)`);
`Dialog.tsx`'e opsiyonel `tone?: "default" | "danger"` prop'u (yalnızca `danger` iken `.danger` class'ı
eklenir, `default` hiçbir ek class geçmez - boş bir CSS kuralı icat edilmedi); `ConfirmDialog.tsx` kendi
`tone` prop'unu `Dialog`'a `tone={tone}` ile iletiyor. `Table.module.css`'e `tbody tr:hover` kuralı: inset
`box-shadow` ile 2px `--color-primary` sol kenar + `color-mix()` ile %5 tint zemin (layout shift'e yol
açan `border` yerine `box-shadow` seçildi).

**Doğrulama:** `npx tsc --noEmit` + `npx eslint .` + `npm run build` (staff-web) temiz - 13 route, hepsi
sorunsuz derlendi. Canlı Chrome testi yapıldı: `infra-staff-web-1` container'ı geçici durdurulup yerine
`npx next dev -p 3002` başlatıldı, `/internal/businesses/{id}/staff-users` bootstrap endpoint'iyle geçici
bir `BUSINESS_ADMIN` test hesabı (`design-verify@qrmenu.local`, "Test Restoran") oluşturuldu. `/staff`
sayfasında: (1) tablo satırı hover'da sol kenar tide-marker accent + zemin tint'i doğru render edildi, (2)
"+ Personel Ekle" ile açılan rutin `Dialog`'da üst kenarda sakin cyan gate stripe, (3) "Devre Dışı Bırak"
ile açılan `ConfirmDialog tone="danger"`'da aynı üst kenarın kırmızıya döndüğü görsel olarak doğrulandı -
iki ton net bir şekilde ayrışıyor, konsolda hata yok. Test sonunda test hesabı + session'ı DB'den silindi,
yerel `next dev` kapatıldı, `infra-staff-web-1` container'ı yeniden başlatıldı. Işık temasında ayrı
doğrulama yapılmadı (Adım 1-4 ile aynı düşük-risk gerekçesi - yalnızca mevcut `--color-*` token'ları
kullanıldı). Backend değişikliği yok.
