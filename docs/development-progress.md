# Geliştirme İlerleme Durumu

Bu dosya, `docs/milestone-1-report.md` … `milestone-4-report.md` dosyalarının yerine geçen özet bir durum
kaydıdır. Amaç geçmişin ayrıntılı raporunu tutmak değil, yeni bir Claude oturumunun projenin mevcut durumunu
hızlıca kavramasıdır. Tam gereksinimler/kararlar için [`product-requirements.md`](../../product-requirements.md)
(özellikle Bölüm 9 — milestone planı) tek otoritedir; buradaki notlar yalnızca "ne yapıldı, neden, nelere
dikkat" özetidir.

**Genel durum:** Backend `com.qrmenu` modüler monolit (Spring Boot 3.5.3, Java 21, Maven, PostgreSQL 16, Flyway
V1–V11). İki frontend uygulaması var: `customer-web` (Next.js 16, `app/t/[token]`, `app/order/track/[token]`) ve
`staff-web` (Next.js 16, port 3002 — gerçek StaffUser login + admin ekranları + pickup board, Milestone 8/9). 10
modül var: `tenant`, `customersession`, `menu`, `ordering`, `payment`, `notification`, `refund`,
`staffaccess`, `audit` (+ modül-olmayan `shared`/`shared.outbox`) — ayrı bir `kitchen` modülü/ekranı yok, sipariş
hazırlama akışı `ordering`/Kasa altında (bkz. bu dosyanın alt kısımlarındaki "Mutfak/KDS kaldırıldı" kararları).
Tüm milestone'lar (1-9) tamamlandı.

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
- **Yeni `com.qrmenu.expense` modülü:** `ExpenseCategory` (business-scoped, manuel yönetim), kaydedildiği
  anda raporlanan manuel `Expense` ve her vadesi gelen dönem için immutable Expense snapshot'ı üreten
  `RecurringExpenseTemplate` (yalnızca `MONTHLY`, spec'in "ilk ihtiyaç"
  dediği tek değer). `branchId` nullable - business-level (şube bağımsız) gider de mümkün (Section 16.1).
- **Yeni permission'lar:** `EXPENSE_VIEW`/`EXPENSE_MANAGE` (BUSINESS_ADMIN + BRANCH_MANAGER - kendi şubesi için
  görüntüleme ve oluşturma). Ayrı bir gider onay permission/workflow'u yoktur.
- **`RecurringExpenseScheduler`** (`@Scheduled`, 6 saatte bir): aktif şablonları tarar, `dayOfMonth` bugüne
  denk geliyorsa (kısa aylarda ayın son gününe düşürülüyor) ve o dönem (`YearMonth`) için henüz üretilmemişse
  otomatik, immutable bir Expense oluşturur. İdempotency DB'deki
  `uq_expense_template_period` partial unique index + `existsBySourceTemplateIdAndGeneratedForPeriod` ön
  kontrolüyle sağlanıyor; scheduler'ın kaçırılan/tekrarlanan çalışması hiçbir zaman bir dönemi iki kez
  taslaklamıyor (gün sonu kapanış scheduler'ıyla aynı self-correcting felsefe).
- **`StaffExpenseController`:** kategori CRUD, manuel gider CRUD ve tekrarlayan şablon CRUD.
- **`reporting` modülüne Section 17 endpoint'i:** `GET .../reports/operating-result` - `ReportingService.
  getBranchReport`'un net satışından seçili dönemdeki manuel + oluşmuş recurring giderleri
  çıkararak "Yönetimsel Net Sonuç" döner. Backend/frontend hiçbir yerde "net kâr" ifadesi kullanılmıyor -
  Section 17'nin uyarısı (vergi/stok maliyeti/personel tahakkuku/amortisman modellenmiyor) `OperatingResult
  Response`'un javadoc'unda ve staff-web kartındaki uyarı metninde açıkça belirtiliyor.
- **staff-web:** yeni `/expenses` ekranı (kategori yönetimi, manuel gider oluşturma/listeleme,
  tekrarlayan şablon listesi/oluşturma), `/reports/[branchId]`'ye "Yönetimsel Net Sonuç"
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

**Backend entegrasyon kapsamı:** manuel giderin anında rapora girmesi ve düzenlenebilmesi, CASHIER'ın
403 alması, BRANCH_MANAGER'ın başka şubeye erişememesi, recurring scheduler'ın bir dönem için tam
olarak bir kez kayıt üretmesi (idempotency), recurring snapshot immutability ve operating-result'ın manuel
+ vadesi gelmiş recurring giderleri doğru çıkarması; `ModuleBoundaryTest` kapsamı korunur.

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
    formu + manuel gider tablosu), `RecurringTemplates` (Dialog'lu oluşturma + Table). Gider
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

## UI/UX Productization Gate — Adım 7 Tasarım Yenilemesi: responsive/erişilebilirlik doğrulama geçişi — ✅ COMPLETED

Gate'in "Cross-cutting" sırasının son adımı. Adım 7'nin işlevsel kapsamı (responsive/erişilebilirlik/
Chrome E2E - bkz. yukarıdaki "Adım 7: Responsive/Accessibility/Browser E2E Pass") kendi başına yeni bir
görsel imza öğesi taşımıyordu - bu adım zaten Adım 1-6'nın *doğrulamasıydı*. Aynı mantıkla, "Adım 7
Tasarım Yenilemesi" de Adım 1-6 Tasarım Yenilemesi'nde eklenen altı imza öğesinin (tide line, tide
marker, tide-edge, timeline pulse, gate stripe, tide bar) tamamının dar/geniş viewport'larda ve
erişilebilirlik açısından (klavye focus, `prefers-reduced-motion`) kırılmadığını doğrulayan son bir
geçiş - yeni bir component veya sayfa eklenmiyor, kod değişikliği yalnızca bulgu çıkarsa yapılacak.

**Kapsam - doğrulanacak imza öğeleri ve konumları:**
1. **Tide line** (Adım 2) - customer-web `VisitHeader`, 390px'te.
2. **ProductCard/CategoryNav hover+placeholder tint** (Adım 2) - customer-web menü, 390px'te (dokunmatik
   cihazda hover yok, ama placeholder tint ve CategoryNav aktif chip gölgesi statik olarak görünür kalmalı).
3. **Timeline pulse** (Adım 2) - sipariş takip sayfası, 390px'te + `prefers-reduced-motion: reduce`
   altında pulse'ın durduğunu doğrulama (globals.css'teki blanket kural).
4. **Tide marker** (Adım 3) - staff-web `AppShell` sidebar, 768-1023px (drawer/overlay) ve <768px'te.
5. **Tide-edge** (Adım 4) - kasa/KDS sipariş kartları, KDS'in Adım 7'nin orijinal kapsamında eklenen
   `--container-width-kiosk` (1920px) genişliğinde tide-edge'in grid'in geri kalanıyla hizalı kaldığını
   doğrulama.
6. **Gate stripe** (Adım 5) - Dialog/ConfirmDialog, dar viewport'ta (dialog genişliği daralınca üst
   şeridin kırpılmadığını doğrulama) + klavye focus (Tab ile dialog içi gezinme).
7. **Table row hover tide-marker** (Adım 5) + **tide bar** (Adım 6) - <768px'te tabloların/BarList'in
   `overflow-x` davranışını bozmadığını doğrulama.

**Erişilebilirlik notu:** Yeni eklenen hiçbir öğe interaktif bir kontrol değil (hepsi salt görsel/
dekoratif - `::before`/`box-shadow`/`background` katmanları), bu yüzden yeni bir `aria-*` veya focus
kuralı gerekmiyor beklentisi var; doğrulamanın amacı bunu teyit etmek. Pulse/tide-edge gibi hareket
içeren öğelerin `prefers-reduced-motion` altında durduğu (Adım 2'de zaten blanket kuralla karşılandığı
belirtilmişti) bu adımda ilk kez fiilen tarayıcıda test ediliyor.

**Uygulama planı:** (1) Docker container'ların (backend/customer-web/staff-web) güncel kodla ayakta
olduğunu doğrula; (2) customer-web 390px iframe testiyle tide line/ProductCard/pulse+reduced-motion; (3)
staff-web 768-1023px ve <768px'te tide marker/table hover/BarList overflow; (4) staff-web gerçek pencerede
Dialog gate stripe klavye focus + KDS 1920px tide-edge hizası; (5) bulgu çıkarsa düzelt, çıkmazsa yalnızca
doğrulama sonucu logla; (6) `npx tsc --noEmit` + `npx eslint .` + `npm run build` (her iki app, yalnızca
kod değişikliği olduysa).

**Test ortamı:** `docker compose up -d --build customer-web staff-web` ile iki container da Adım 6 Tasarım
Yenilemesi commit'lerini yansıtacak şekilde yeniden build edilip ayağa kaldırıldı (container'lar Adım 6'nın
commit'lerinden daha eskiydi). `/internal/businesses/{id}/staff-users` bootstrap endpoint'iyle geçici bir
`BUSINESS_ADMIN` test hesabı (`design-verify-a7@qrmenu.local`, "Test Restoran") oluşturuldu; customer-web
için DB'den gerçek bir QR token bulundu. Dar viewport'lar için (Adım 7'nin orijinal metodolojisiyle aynı)
sayfa içine enjekte edilen bağımsız `<iframe>`'ler kullanıldı; kiosk genişliği (1920px) için de aynı
yöntem - gerçek pencere bu ortamda ~1512px ile sınırlı kaldığından.

**Bulgular - hepsi doğrulandı, hiçbir imza öğesinde kırılma yok:**
1. **Tide line + ProductCard/CategoryNav accents** (Adım 2) - customer-web 390px'te menüden tam bir
   sipariş akışı (menü → ürün detay → sepet → mock ödeme → sipariş takip) uçtan uca koşturuldu, her
   adımda `scrollWidth <= clientWidth` doğrulandı (overflow yok), tüm CTA'lar erişilebilir kaldı.
2. **Timeline pulse** (Adım 2) - sipariş takip sayfasında "İşletme onayı" (mevcut adım) marker'ının pulse
   halkası 390px'te doğru render edildi. `globals.css`'teki blanket `prefers-reduced-motion: reduce` kuralı
   (`* { animation-duration: 0.001ms !important; }`) Adım 2-6'daki hiçbir değişiklikle dokunulmamış halde
   duruyor - OS seviyesinde gerçek toggle bu ortamda emüle edilemedi (araç seti bunu desteklemiyor), statik
   doğrulamayla yetinildi.
3. **Tide marker** (Adım 3) - 900px genişlikte (768-1023 aralığı) sidebar hamburger ikonuna daralıyor,
   tıklanınca backdrop'lu bir drawer açılıyor ve aktif "Dashboard" linkinde tide marker + tint doğru
   render ediliyor; 414px'te dashboard/personel/reports sayfaları tek kolona düşüyor, sayfa genelinde
   overflow yok (`scrollWidth === clientWidth === 412px` ölçüldü).
4. **Table row hover tide-marker + tide bar** (Adım 5/6) - 414px'te `/staff` tablosu kendi
   `overflow-x: auto` wrapper'ı sayesinde sayfa overflow'una yol açmadan yatay kaydırılabiliyor (bilinen/
   mevcut pattern); `/reports`'ta tide bar ve `DateRangePresets` aktif pili 414px'te de doğru render edildi.
5. **Gate stripe** (Adım 5) - `/staff` sayfasında "+ Personel Ekle" (`Dialog`, rutin ton) ve "Devre Dışı
   Bırak" (`ConfirmDialog tone="danger"`) açılıp klavyeyle (`Tab`) alan alan gezinildi - odak halkası her
   alanda (input/select/checkbox/buton) görünür kaldı, gate stripe (cyan/kırmızı) dialog'un yuvarlatılmış
   üst köşelerinde kırpılmadan render edildi.
6. **Tide-edge + KDS kiosk genişliği** (Adım 4 + Adım 7'nin orijinal `--container-width-kiosk` düzeltmesi)
   - `/kitchen/{branchId}` sayfası 1920px'lik bir iframe'e yüklendi, `getComputedStyle` ile grid'in
   `grid-template-columns`'ının 4 sütuna (`390px 390px 390px 390px`) genişlediği doğrulandı (DOM'a geçici
   kart klonları eklenerek), her klonda tide-edge sol çubuğunun tam yükseklikte ve hizalı kaldığı görsel
   olarak teyit edildi - Adım 4/7'nin `--container-width-kiosk` düzeltmesi Adım 6 gradient değişikliğiyle
   çakışmıyor.

**Bulunan ama bu adımın kapsamı dışında olan durum:** `/staff` personel tablosunda önceki bir oturumdan
kalma `e2e-test@qrmenu.local` test hesabı (orijinal Adım 7'nin bootstrap hesabı) hâlâ DB'de duruyor - o
oturum temizliği tamamlamamış. Bu adımın kendi test hesabı (`design-verify-a7@qrmenu.local`) ve test
siparişi (checkout akışıyla oluşan #3 numaralı sipariş, payment/payment_webhook_event/order_item dahil)
DB'den silindi, ama eski `e2e-test@qrmenu.local` hesabına dokunulmadı - kullanıcıya ayrıca bildirildi.

**Doğrulama:** Kod değişikliği yapılmadı (yalnızca doğrulama), bu yüzden `tsc`/`eslint`/`build` tekrar
çalıştırılmadı - Adım 6'nın derleme çıktısı hâlâ geçerli. Backend değişikliği yok.

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

## UI/UX Productization Gate — Adım 6 Tasarım Yenilemesi: raporlama/dashboard — ✅ COMPLETED

Gate'in "Cross-cutting" sırasının 6. adımı (reporting/dashboard visualization). Adım 6'nın işlevsel kapsamı
(KpiCard/DateRangePresets/BarList component'leri + bunların `dashboard`, `reports` (zincir), `reports/
[branchId]` sayfalarında kullanılması - bkz. yukarıdaki "Adım 6: Reporting/Dashboard Visualization") daha
önce tamamlanmıştı. Adım 1 Tasarım Yenilemesi'nin token cascade'i `KpiCard.value`'ye zaten display font +
`tabular-nums` kazandırmıştı, ama Adım 2/3/4/5'te olduğu gibi bilinçli, sayfaya özgü bir imza öğesi hiç
eklenmemişti - `BarList`'in çubukları hâlâ düz `--color-primary` dolgu, `DateRangePresets`'in aktif preset
pili de düz `--color-primary` dolgu kullanıyordu. Bu adımda `frontend-design` skill süreciyle (Adım 1-5 ile
aynı yöntem) yalnızca `components/ui/BarList.module.css` ve `DateRangePresets.module.css`'e restrained bir
tasarım geçişi uygulanıyor. Kapsam bilinçli olarak bu iki paylaşılan component'le sınırlı - `KpiCard`'a,
hiçbir sayfaya (`dashboard`/`reports`/`reports/[branchId]`) veya `page.module.css`'e dokunulmuyor.

**Tasarım kararı - imza öğesi: "tide bar".** Raporlama akışının duygusal karşılığı, bir sıralama veya
trendin *büyüklüğü* - `BarList` zaten şube/ürün/kategori sıralamasını ve günlük ciro trendini büyüklüğe göre
çubuk uzunluğuyla anlatıyor, ama rengi anlamsız düz bir tondu. `.bar`'ın dolgusu, shell/dialog'daki aynı Tide
gradient'ine (`--color-primary` → `--color-primary-strong`) ama bar'ın kendi ekseni boyunca (yatay,
`to right`) çevrildi - "değer ne kadar yüksekse gelgit o kadar yükseliyor" okuması, tide-edge'in (Adım 4)
"süre uzadıkça çubuk uzuyor/koyulaşıyor" mantığının raporlamaya çevirisi. `BarList` yalnızca pozitif
sıralama/trend değerleri için kullanıldığı doğrulandı (`reports`/`reports/[branchId]`/`dashboard`'da refund
veya net-sonuç gibi "dikkat" değerleri hep `KpiCard tone="danger"` ile ayrı gösteriliyor, `BarList`'e hiç
geçmiyor) - bu yüzden tek bir sakin gradient yeterli, `ConfirmDialog`'daki gibi ayrı bir `danger` varyantına
gerek yok.

**İkinci, sakin dokunuş - DateRangePresets aktif pil.** Yeni bir motif icat etmeden aynı "tide bar"
gradient'i, seçili tarih preset pilinin (`Bugün`/`Dün`/`Bu Hafta`/`Bu Ay`/`Özel`) dolgusuna da taşındı -
kavramsal olarak zorlama değil: "tide" zaten zamanın gelgitiyle ilgili bir metafor, zaman aralığı seçimi bu
dile doğal olarak oturuyor. `KpiCard` bilinçli olarak sakin bırakıldı - Adım 1'in cascade'iyle zaten display
font kimliğini taşıyor, ek bir öğe Chanel prensibini ihlal eder (Adım 5'te `PageHeader`/`EmptyState`/
`ErrorState`/`Toast`'ın aynı gerekçeyle sakin bırakılmasıyla tutarlı).

**Uygulanan değişiklikler:** `BarList.module.css` `.bar`'ın `background`'ı düz `--color-primary`'den
`linear-gradient(to right, var(--color-primary), var(--color-primary-strong))`'a; `DateRangePresets.module.css`
`.active`'in `background`'ı aynı gradient'e çevrildi. İki dosyalık, sayfa dokunmayan minimal bir değişiklik.

**Doğrulama:** `npx tsc --noEmit` + `npx eslint .` + `npm run build` (staff-web, 13 route) temiz. Canlı
Chrome testi yapıldı - `infra-staff-web-1` container'ı geçici durdurulup yerine `npx next dev -p 3002`
başlatıldı, `/internal/businesses/{id}/staff-users` bootstrap endpoint'iyle geçici bir `BUSINESS_ADMIN` test
hesabı (`design-verify@qrmenu.local`, "Test Restoran") oluşturuldu. `dashboard`'da "Bugün" KPI grid'i +
Şube Sıralaması `BarList`'inin tide gradient'i (teal → koyu teal, soldan sağa) gerçek veriyle (₺300 brüt
satış, tek şube) doğru render edildi (zoom ile görsel olarak doğrulandı); `reports` (zincir) sayfasında
"Bugün" preset pilinin aynı gradient dolgusu ve `Şube Sıralaması` bar'ı doğrulandı; `reports/[branchId]`
(şube raporu) sayfasına geçildi, sayfa hatasız render edildi (o aralıkta ürün/kategori/gün-sonu verisi
olmadığından bu bölümler boş-durum metni gösterdi - tasarım değişikliğiyle ilgisiz, mevcut veri durumu).
Konsolda hata yok. Test sonunda test hesabı + session'ı DB'den silindi, yerel `next dev` kapatıldı,
`infra-staff-web-1` container'ı yeniden başlatıldı. Işık temasında ayrı doğrulama yapılmadı (Adım 1-5 ile
aynı düşük-risk gerekçesi - yalnızca mevcut `--color-*` token'ları kullanıldı). Backend değişikliği yok.

---

## Gap-Analysis #15 — Görsel/Receipt Storage (MediaStoragePort) — ✅ COMPLETED

Önceki bir oturumun requirements-vs-kod taramasında bulunan tek somut PARTIAL: `Product.imageUrl` /
`Expense.receiptImageUrl` yalnızca birer plain string kolon - Bölüm 3.2/16.1'in istediği "media/storage
adapter'ın döndürdüğü URL/key" hiç kurulmamıştı, staff-web'de de bu alanları dolduran hiçbir UI yoktu
(`ProductsSection`'ın oluşturma formu `imageUrl` hiç göndermiyordu, `ExpenseForm` `receiptImageUrl: null`
sabit gönderiyordu) - kullanıcı bugüne kadar bu alanları yalnızca doğrudan API çağrısıyla doldurabilirdi.

**Tasarım:**
- **`com.qrmenu.shared.media`** (yeni, `shared.outbox` ile aynı statü - modül değil, ArchUnit sınırı yok,
  persistence'ı yok): `MediaStoragePort` arayüzü (`store(MediaCategory, filename, contentType, bytes) ->
  StoredMedia(key, url)`), `MediaCategory` enum (`PRODUCT_IMAGE`, `EXPENSE_RECEIPT` - kategori başına ayrı
  izin verilen content-type seti + boyut limiti), `LocalFileMediaStorageAdapter` (tek adapter, "production
  storage sağlayıcısı seçme" kullanıcı talimatıyla uyumlu - ❓ Bölüm 27'de zaten açık karar). Doğrulama:
  magic-byte sniffing (deklare edilen `Content-Type`'a güvenilmiyor - JPEG/PNG/WEBP/PDF imzaları elle
  kontrol ediliyor, yeni bağımlılık gerekmedi), boyut limiti (ürün görseli 5MB, gider fişi 10MB - fişler
  PDF/taranmış doküman olabilir, Bölüm 16.1: "fotoğrafı/dokümanı"), boş dosya reddi, dosya adı her zaman
  sunucu tarafında üretilen bir UUID + sniff edilen uzantı (kullanıcı dosya adı hiç güvenilmiyor - path
  traversal/uzantı sahteciliği yüzeyi yok). `MediaValidationException extends IllegalArgumentException` -
  mevcut `ApiExceptionHandler`'ın zaten `IllegalArgumentException`'ı 400'e çevirdiği kural yeniden
  kullanıldı, handler'a yeni case eklenmedi.
- **Sunum:** yeni dosyalar `/media/**` altında Spring'in `WebMvcConfigurer.addResourceHandlers` static
  resource handler'ıyla (dosya sistemi -> HTTP, ETag/cache header'ları ücretsiz) sunuluyor - hem ürün
  görseli (müşteri menüsünde herkese açık gösteriliyor, zaten kimliksiz) hem gider fişi bu yoldan sunuluyor.
  Fiş dosya adı da sunucu tarafında üretilen yüksek-entropili bir UUID (tahmin edilemez) - bu, projenin QR
  token/orderTrackingToken'da zaten kullandığı "yüksek entropi = düşük blast radius, ayrı bir auth katmanı
  yerine" kararıyla aynı gerekçe (Bölüm 21/22); yükleme (upload) tarafı zaten `EXPENSE_MANAGE` permission'ı
  arkasında, yalnızca okuma/sunum tarafı bu şekilde basitleştirildi. Gerçek bir prod storage sağlayıcısı
  seçildiğinde (❓ açık karar) bu adapter S3/GCS gibi imzalı-URL veren bir adapter'la değiştirilebilir, port
  arayüzü değişmez.
- **Upload endpoint'leri (additive, mevcut Product/Expense create/update DTO'larına dokunmadan):**
  `POST /api/staff/media/product-images` (`Permission.MENU_MANAGE`, multipart `file`) ve
  `POST /api/staff/media/receipts` (`Permission.EXPENSE_MANAGE`, multipart `file`) - ikisi de yalnızca
  `{url}` döner. Frontend dosyayı seçer seçmez hemen yükler, dönen URL'i mevcut `imageUrl`/`receiptImageUrl`
  form state'ine yazar, ardından mevcut create/update akışı **hiç değişmeden** aynı URL string'ini gönderir
  - `Product`/`Expense` entity/tablo şeması bu adımda hiç değişmiyor (kullanıcı talimatı: "mevcut yapıyı
    mümkün olduğunca koru").
- **Var olan boşluk:** `updateProductDetails`/`Product.updateDetails` şu ana kadar `imageUrl` hiç
  almıyordu (yalnızca creation'da set edilebiliyordu, DB'de zaten var ama hiçbir UI yolu yoktu) - bu adımda
  `UpdateProductDetailsRequest`/`MenuService.updateProductDetails`/`Product.updateDetails`'e `imageUrl`
  eklenip staff-web'in "Düzenle" paneline taşınıyor; böylece zaten oluşturulmuş ürünlere de sonradan görsel
  eklenebiliyor. `Expense` tarafında `receiptImageUrl` zaten `createExpense`/`updateDraft`'ta vardı, backend
  değişikliği gerekmedi - yalnızca frontend'in bu alanı artık dolu göndermesi gerekiyordu.
- **Frontend:** yeni shared `components/ui/FileUploadField` (dosya seç -> anında yükle -> `value` prop'una
  URL yaz, "Kaldır" ile temizle - manuel URL text input'u hiçbir yerde yok), `ProductsSection` (oluşturma
  formu) + `ProductRow` (mevcut ürünü düzenleme paneli) + `ExpenseForm` (oluşturma formu) bu component'i
  kullanıyor.
- **Docker:** `infra/docker-compose.yml`'e backend için adlandırılmış bir `media_data` volume'u (container
  içi sabit bir path'e mount, konteyner yeniden oluşturulsa bile yüklenen dosyalar korunuyor - `postgres_data`
  ile aynı desen).

Bu tasarım kullanıcının kendi talimatındaki 8 maddeyi birebir karşılıyor; ayrı bir netleştirme turu
gerekmedi (talimat zaten tüm ana kararları - provider-bağımsız port, local adapter, upload UI, mevcut şema
korunması, content-type/boyut/güvenlik kontrolü, prod sağlayıcı seçilmemesi - içeriyordu).

**Sonuç:** tasarım plana göre uygulandı, iki commit'te:

1. **Backend** (`c302c17`): `com.qrmenu.shared.media` (`MediaStoragePort`/`MediaCategory`/`DetectedFileType`/
   `LocalFileMediaStorageAdapter`/`MediaResourceConfig`), `com.qrmenu.media.web.StaffMediaUploadController`
   (`/api/staff/media/product-images` + `/receipts`), `Product.updateDetails`/`MenuService.
   updateProductDetails`/`UpdateProductDetailsRequest`'e `imageUrl` eklendi (artık PATCH ile de
   düzenlenebiliyor - önceden yalnızca creation'da vardı), `ApiExceptionHandler`'a
   `MaxUploadSizeExceededException → 400`, `application.yml`'e `spring.servlet.multipart.max-file-size`
   (12MB) + `media.storage.*`, `infra/docker-compose.yml`'e `media_data` volume'u. Yeni
   `MediaUploadFlowIntegrationTest` (5: başarılı ürün görseli upload + PATCH ile ürüne bağlama, PDF fiş
   upload, tanınmayan içerik reddi, boyut aşımı reddi, `KITCHEN_STAFF`'ın her iki uç noktadan da 403 alması).
   Tam backend suite (`mvn test`, tüm modüller) 120 → 125 yeşil.
2. **Frontend** (`57e9fee`): yeni shared `components/ui/FileUploadField` (dosya seç → anında yükle →
   `value`'ya URL yaz, `previewAsImage` prop'uyla görsel thumbnail/dosya linki ayrımı), `lib/api.ts`'e
   `uploadProductImage`/`uploadReceiptImage` (multipart `fetch`, `apiFetch`'in JSON-only varsayımını
   atlıyor) + `createProduct`/`updateProductDetails`/`ExpenseInput` imzalarına `imageUrl`/`receiptImageUrl`.
   `ProductsSection` (oluşturma), `ProductRow` (düzenleme - `imageUrl` daha önce hiç UI'dan
   değiştirilemiyordu), `ExpenseForm` (oluşturma - `receiptImageUrl` daha önce sabit `null` gönderiliyordu,
   alan hiç UI'da yoktu) bu component'i kullanıyor. `npx tsc --noEmit` + `npx eslint .` + `npm run build`
   (13 route) temiz.

**Canlı Chrome doğrulaması (gerçek Docker Compose stack'i, uçtan uca):** `docker compose up -d --build
backend staff-web` ile güncel koda göre yeniden build edildi. İlk denemede gerçek bir hata bulundu ve
düzeltildi: **backend container'ı non-root `appuser` ile çalışıyor** (Milestone 9 güvenlik kararı), ama yeni
`media_data` named volume'u Docker tarafından `root:root` sahipliğiyle oluşturuluyordu -
`LocalFileMediaStorageAdapter.store`'un `Files.createDirectories(/data/media/product-images)` çağrısı
`AccessDeniedException` ile 500 veriyordu. Düzeltme: `backend/Dockerfile`'da `/data/media`'yı `useradd`'dan
hemen sonra, `USER appuser`'a geçmeden önce `mkdir -p` + `chown -R appuser:appuser` ile önceden oluşturuldu
- Docker boş bir named volume'u ilk mount'ta image'daki dizinin sahiplik/izinleriyle initialize ediyor, bu
yüzden volume da temiz yeniden oluşturuldu (`docker volume rm` + yeniden `up`). Bu, Milestone 4/6/8/9'daki
"yeni bir HTTP metodu/altyapı eklenen her adımda gerçek ortam doğrulaması entegrasyon testlerinin
yakalayamayacağı bir hata çıkarabilir" örüntüsünün bir tekrarı - Testcontainers'ın kendi geçici container'ı
hiçbir zaman non-root/named-volume sahiplik etkileşimini test etmiyor.

Düzeltme sonrası tam akış geçici bir `BUSINESS_ADMIN` test hesabıyla ("Media Verify Business") doğrulandı:
(1) `/menu`'de "+ Ürün Ekle" → PNG dosyası seçildi → anında yüklendi (thumbnail önizleme + "Kaldır" göründü)
→ ürün oluşturuldu → "Düzenle" paneli açılıp aynı görselin geri okunduğu (GET `/media/product-images/...`)
doğrulandı; (2) `/expenses`'te "+ Gider Ekle" → PDF fiş seçildi → anında yüklendi ("Dosyayı görüntüle" linki
göründü, `previewAsImage=false` doğru davrandı) → gider oluşturuldu → API'den `receiptImageUrl`'in kalıcı
olduğu ve dosyanın gerçekten `GET /media/receipts/{uuid}.pdf` üzerinden 200 ile inebildiği `curl` ile teyit
edildi. Test hesabı/business/branch/ürün/gider ve yüklenen test dosyaları doğrulama sonunda temizlendi.

**Bilinen not (bu doğrulama sırasında fark edildi, bu maddenin kapsamı dışında):** Yeni oluşturulan test
işletmesinde, hiç açıkça çağrılmamış birkaç aksiyon (bir "Kira" gider kategorisi + aylık tekrarlayan şablon,
3 masa, bir QR token oluşturup iptal etme, şube çalışma saatleri/teslimat modeli değişikliği, bir duyuru)
audit log'da aynı test staff kullanıcısı tarafından yapılmış görünüyor - bu oturumun kendi curl/Chrome
adımlarının parçası değildi. Muhtemelen bu ortamda bağımsız çalışan bir smoke-test/demo-seed script'i (bkz.
DB'de önceden var olan "Docker Smoke Test Business") her yeni BUSINESS_ADMIN hesabını otomatik olarak
deniyor. Media storage özelliğini etkilemiyor (temiz doğrulama sonucu değişmedi), ama ayrı bir not olarak
kullanıcıya bildirildi - kaynağı bu oturumda araştırılmadı.

**Doğrulama:** backend `mvn test` (125/125 yeşil) + frontend `tsc`/`eslint`/`build` temiz + canlı Docker
Compose + gerçek Chrome'da uçtan uca upload/görüntüleme akışı (bulunan Dockerfile izin hatası dahil)
doğrulandı.

### Ek düzeltme (2026-08-13): Expense receipt artık public `/media/**` üzerinden erişilemiyor

Yukarıdaki Gap-Analysis #15 doğrulamasında receipt dosyalarının product image'larla aynı public
`/media/**` yolundan (yalnızca yüksek entropili UUID dosya adına güvenerek) servis edildiği görüldü -
fiş/fatura tutar/satıcı gibi hassas bilgi içerebileceğinden bu yeterli değil. Düzeltme:

- **`MediaResourceConfig`**: static resource handler artık yalnızca `/media/product-images/**`'i
  `product-images/` dizinine bağlıyor - `receipts/` dizini hiçbir public path'e bağlı değil (eşleşen
  handler yok → 404).
- **`MediaStoragePort`**: `load(key)` (dosyayı okuyup content-type'ı yeniden sniff'leyerek döndürür) ve
  `resolveKeyFromUrl(url)` (daha önce `Expense.receiptImageUrl`'de saklanan public URL'den storage key'i
  çıkarır) eklendi; tek implementasyon olan `LocalFileMediaStorageAdapter`'da path traversal'a karşı
  `baseDir` içinde kaldığını doğrulayan bir kontrolle uygulandı.
- **`ExpenseService`**: yeni `loadReceipt(context, expenseId)` - `requireEditableExpense`'in tenant/branch
  kontrolünü (`findByIdAndBusinessId` + `canAccessBranch`) editable-state şartı olmadan yeniden kullanan
  `requireViewableExpense` üzerinden (APPROVED/REJECTED bir giderin fişi de görüntülenebilmeli, sadece
  düzenlenemez olmalı).
- **`StaffExpenseController`**: yeni `GET /api/staff/expenses/{expenseId}/receipt` - `Permission.EXPENSE_VIEW`
  + yukarıdaki tenant/branch scoping ile korunuyor, dosya bytes'ını doğru `Content-Type` ile döndürüyor.
- **Frontend (`FileUploadField`)**: receipt (`previewAsImage=false`) önizlemesi artık ham URL'e giden bir
  `<a href>` değil, sade bir "Dosya yüklendi" onayı - URL artık public olmadığından doğrudan link vermenin
  anlamı kalmadı. Product image davranışı (thumbnail `<img>`, public URL) değişmedi.
- **Testler**: `MediaUploadFlowIntegrationTest`'e receipt upload sonrası public URL'in artık 404 döndüğünü
  doğrulayan bir assertion eklendi; `ExpenseFlowIntegrationTest`'e yeni bir test
  (`receiptIsOnlyDownloadableThroughTheAuthenticatedTenantScopedEndpoint`) eklendi - public path 404,
  sahibi için authenticated endpoint 200 + doğru bytes, aynı işletmenin başka şubesindeki
  `BRANCH_MANAGER` için 403, tamamen farklı bir işletme için 404 (tenant enumeration'ı önlemek için, mevcut
  `requireEditableExpense` deseniyle tutarlı).
- **Doğrulama:** `mvn test` - tüm backend suite (ilgili iki sınıf dahil) 0 hata/0 başarısız; frontend
  `tsc --noEmit` temiz.

**Eşzamanlı süreç/oturum araştırması:** Görev kapsamında repo üzerinde başka bir process/Claude oturumunun
eşzamanlı değişiklik yapıp yapmadığı araştırıldı. Bulgular: (1) çalışan tek `claude` process'i bu oturumun
kendisiydi, repo dizininde açık dosya tutan başka bir process (node/next dev, mvn, editör) yoktu; (2)
`frontend/staff-web/app/dashboard/page.tsx`'teki commit edilmemiş değişiklik ("Toplam sipariş" →
"Toplam masa siparişi") ve `frontend/{staff-web,customer-web}/{AGENTS.md,CLAUDE.md}` untracked dosyaları bu
oturumdan önce, ayrı zamanlarda oluşmuş: dashboard değişikliği `e3ad1619-...` ID'li, saat 11:19 UTC'de
biten daha önceki bir Claude oturumuna ait (session log'unda doğrulandı) ve hiç commit edilmemiş kalmış;
`AGENTS.md`/`CLAUDE.md` dosyaları ise Claude'a değil, Next.js'in kendisine ait - içerikleri `next dev`
tarafından otomatik yazıldığını belirtiyor (`node_modules/next/dist/server/lib/generate-agent-files.js`),
yani bir noktada `next dev` çalıştırılmış ve bu dosyaları üretmiş. Sonuç: **şu anda eşzamanlı çalışan başka
bir process/oturum yok**; görülen değişiklikler daha önce biten bir oturumun commit edilmemiş kalıntısı ve
bir `next dev` çalıştırmasının yan etkisi. Talimata uyularak bu dosyalara dokunulmadı.

## Gap-Analysis #16 — Kasa Kabul Bekleme Süresi Politikası (Section 6/27 açık kararı) — ✅ COMPLETED

Bölüm 27'deki açık karar ("❓ İşletme bazında kasa accept timeout politikası") kullanıcı tarafından somut
bir politikayla kapatıldı: `AWAITING_STORE_ACCEPTANCE` bekleme süresi takip edilir, varsayılan 5 dakika,
süre dolduğunda **hiçbir otomatik red/refund tetiklenmez** - yalnızca kasa ekranında belirgin
gecikmiş/kritik işaretleme + mümkünse sesli/görsel uyarı, ve timeout branch seviyesinde configurable.

**Tasarım:** `CustomerOrder.lastActivityAt` zaten `markAwaitingStoreAcceptance()` transition'ında
damgalanıyor ve accept/reject'e kadar değişmiyor (`OrderControlOrderResponse.statusSince` olarak API'de
zaten dışa açıktı) - "bekleme süresini takip et" ihtiyacı için yeni bir alan/scheduler gerekmedi, mevcut
alan zaten doğru anlamı taşıyordu. Asıl eksik: eşiğin branch bazında configurable olmaması (frontend'de
`WARNING_THRESHOLD_MINUTES=5`/`DANGER_THRESHOLD_MINUTES=10` sabit kodlanmıştı) ve "otomatik red/refund
YOK" garantisinin açık olması - bu yüzden hiçbir scheduler/otomatik aksiyon eklenmedi (Section 6'nın refund
başarısızlığı için zaten kurduğu "sessizce iptal edilmiş sayılmaz" ilkesiyle aynı ruh: kabul bekleme
süresinin dolması da sessiz bir otomatik sonuca yol açmaz).

- **Backend:** `branch.store_acceptance_timeout_seconds` (V21 migration, `NOT NULL DEFAULT 300`) -
  `Branch` entity'sine `timezone`/`orderingEnabled` ile aynı setter/audit deseninde eklendi
  (`TenantService.setStoreAcceptanceTimeoutSeconds`, `StaffTenantController POST
  /branches/{branchId}/store-acceptance-timeout`, `Permission.BRANCH_MANAGE`, sıfır/negatif değer
  `@Positive` + entity-level guard ile reddediliyor). `OrderControlController.getPendingAcceptance` artık
  branch'in timeout'unu bir kez okuyup her sipariş satırına `OrderControlOrderResponse
  .storeAcceptanceTimeoutSeconds` olarak ekliyor - kasa ekranı ayrı bir branch-settings çağrısı yapmadan,
  zaten SSE ile sürekli refetch ettiği aynı listeden eşiği okuyor.
- **Frontend:** `lib/time.ts`'teki `waitingUrgency` artık opsiyonel bir `timeoutSeconds` parametresi
  alıyor - verildiğinde "danger/kritik" tam olarak o timeout'ta, "warning" ise %60'ında tetikleniyor; KDS
  (mutfak hazırlama süresi, timeout kavramı olmayan bir bağlam) parametre vermediği için eski sabit 5/10
  dakikalık davranışını koruyor. Kasa dashboard'u (`cashier/[branchId]/page.tsx`) her siparişin kendi
  `storeAcceptanceTimeoutSeconds`'ını geçiyor; kritik hale gelen bir sipariş için `lib/alertSound.ts`
  (Web Audio API ile anlık üretilen kısa çift bip - harici ses dosyası yok) sipariş başına yalnızca bir
  kez çalıyor (`alertedOrderIdsRef` ile 15sn'lik "now" tazelemesinde tekrar tekrar öttürmüyor), kart da
  "Kritik ·" etiketli rozet + yumuşak kırmızı nabız animasyonuyla (`.card--critical`) ayrışıyor. Şube
  ayarları sayfasına (`branches/[branchId]/page.tsx`) dakika cinsinden giriş alan bir form eklendi
  (timezone formuyla aynı desen), backend'e saniyeye çevrilerek gönderiliyor.
- **Testler:** `BusinessSettingsFlowIntegrationTest`'e üç yeni test (yeni branch varsayılan 300sn,
  BUSINESS_ADMIN değiştirebiliyor, sıfır/negatif reddediliyor); `OrderControlFlowIntegrationTest`'e
  pending-acceptance yanıtının varsayılan 300sn'i taşıdığını ve branch'in timeout'u değiştirildiğinde
  (600sn) pending listesine yansıdığını doğrulayan testler eklendi. Mevcut accept/reject/refund akışları
  (aynı entegrasyon testleri) değişmeden geçiyor.
- **Doğrulama:** backend `mvn test` - tüm suite 130/130 yeşil (0 hata/0 başarısız); frontend `tsc --noEmit`
  + `eslint` + `next build` (17 route) temiz.

---

## Gap-Analysis #17 — Gerçek Ziyaretçi Sayısı (`TableVisit.guestCount`) — ✅ COMPLETED

Section 13.3'ün 💡 notu: QR sistemi `TableVisit`/session sayısından gerçek fiziksel müşteri sayısını
türetemez (bir kişi bütün masa için sipariş verebilir). Bu nedenle opsiyonel `TableVisit.guestCount`
alanı ekleniyor - müşteri ziyaret başında düşük-friction bir adımda (bir kez, atlanabilir, sonra
değiştirilebilir) kişi sayısını girebilir. Girilmezse alan `NULL` kalır; **hiçbir yerde "bilinmiyor" 1
kabul edilmez** - raporlama yalnızca gerçekten girilmiş değerleri toplar.

**Tasarım:**
- **Backend:** `table_visit.guest_count` (yeni migration, nullable, default yok). `TableVisit.
  setGuestCount(Integer)` `null` veya `>=1` kabul eder, `0`/negatif `IllegalArgumentException` (mevcut
  `ApiExceptionHandler.handleBadRequest` ile 400'e düşer). Yeni `PATCH /api/table-visits/{tableVisitId}/
  guest-count` (mevcut `CartController` ile aynı desende - anonim, `qrmenu_session` cookie'siyle
  `CustomerSessionService.getOwnedTableVisit` ownership kontrolü, tableVisitId erişim credential'ı
  değil). Check-in response'una (`TableVisitResponse.guestCount`) de eklendi ki aynı session bir masaya
  geri döndüğünde zaten girilmiş değeri görüp tekrar sorulmasın.
- **Reporting:** `ReportingService`/`BranchSalesReportView` mevcut `tableVisitCount`'un yanına **ayrı**
  iki yeni alan alıyor: `guestCountTotal` (yalnızca `guestCount IS NOT NULL` olan ziyaretlerin toplamı)
  ve `guestCountRecordedVisitCount` (kaç ziyarette gerçekten girildiği - UI'da "X ziyaretin Y'sinde
  kişi sayısı girildi" şeffaflığı için, toplamın kaç ziyaretten geldiğini gizlememek amacıyla).
  `tableVisitCount` değişmeden kalıyor - iki metrik birbirinden türetilmiyor, gerçekten ayrı gösteriliyor.
- **Frontend (customer-web):** `VisitHeader`'a küçük bir "Kaç kişisiniz?" kontrolü eklenecek
  (`guestCount` doluysa "X kişi · değiştir", boşsa "Ekle"); ziyarette ilk kez `guestCount === null`
  görüldüğünde bir `BottomSheet` + `QuantityStepper` ile bir kez otomatik sorulacak (atlanabilir,
  `sessionStorage`'da tableVisitId bazında "bir daha otomatik sorma" işaretlenecek ama header'daki
  kontrolden her zaman değiştirilebilir kalacak). Mevcut QR/check-in/menü akışı değişmiyor.
- **Frontend (staff-web):** `reports/[branchId]` KPI grid'ine `tableVisitCount`'un yanına ayrı "Misafir
  sayısı" kartı eklenecek.
- **Kapsam dışı:** `DailyBranchCloseReport.guestCount`, zincir (`ChainComparisonService`) footfall
  karşılaştırması, unique-session metriği - bunlar ayrı roadmap maddeleri (M14.1, #7), bu gap yalnızca
  Section 13.3'ün asıl istediğini kapatıyor.

**Uygulama:** Yukarıdaki tasarım aynen uygulandı.

- **Backend:** V22 migration (`table_visit.guest_count`, nullable). `TableVisit.setGuestCount`/
  `getGuestCount`; `CustomerSessionService.setGuestCount/sumGuestCountBetween/
  countVisitsWithGuestCountBetween`; `TableVisitRepository`'ye
  `countByBranchIdAndStartedAtBetweenAndGuestCountIsNotNull` (derived) +
  `sumGuestCountByBranchIdAndStartedAtBetween` (`@Query`, yalnızca `guestCount IS NOT NULL`
  satırları topluyor). Yeni `TableVisitController` → `PATCH /api/table-visits/{tableVisitId}/
  guest-count` (`SetGuestCountRequest.guestCount` nullable + `@Min(1)`), `QrCheckinController`'ın
  check-in yanıtına `guestCount` eklendi. `BranchSalesReportView`/`BranchSalesReportResponse`
  mevcut `tableVisitCount`'un yanına `guestCountTotal` + `guestCountRecordedVisitCount` aldı
  (`ReportingService.buildReport`, `StaffReportingController.toResponse`).
- **Frontend (customer-web):** `VisitHeader`'a "Kaç kişisiniz? Ekle" / "X kişi · Değiştir"
  kontrolü; yeni `GuestCountSheet` (`BottomSheet` + `QuantityStepper`, "Atla"/"Kaydet").
  `page.tsx` check-in sonrası `guestCount === null` ve bu ziyaret için daha önce
  `sessionStorage`'da atlanmamışsa sheet'i bir kez otomatik açıyor; atlama da onaylama da
  `sessionStorage`'a "bir daha otomatik sorma" işareti koyuyor, header'daki kontrol her zaman
  açık kalıyor. `lib/api.ts`'e `setGuestCount` (PATCH) eklendi.
- **Frontend (staff-web):** `reports/[branchId]` KPI grid'inde "Masa ziyareti"nin yanına ayrı
  "Misafir sayısı" kartı (`guestCountRecordedVisitCount === 0` ise "-", hint'te "X ziyarette
  girildi"/"Henüz girilmedi").
- **Testler:** yeni `TableVisitGuestCountFlowIntegrationTest` (boş check-in `guestCount`
  taşımıyor, geçerli değer set edilip sonraki check-in'de aynı değerin döndüğü, değiştirme +
  `null` ile temizleme, 0/negatif değerin 400 ile reddi, başka session'ın 404 alması);
  `ReportingFlowIntegrationTest`'e iki ziyaretten yalnızca birinde `guestCount` girildiğinde
  `tableVisitCount=2` ama `guestCountTotal=3`/`guestCountRecordedVisitCount=1` kaldığını
  doğrulayan assertion'lar eklendi. Mevcut QR/check-in/cart/reporting akışları değişmeden geçiyor.
- **Doğrulama:** backend `mvn test` - tüm suite 135/135 yeşil (0 hata/0 başarısız, +5 yeni
  test); customer-web ve staff-web `tsc --noEmit` + `eslint` + `next build` temiz.

---

## Production Readiness — CRITICAL #1-3 (API base URL, TLS reverse proxy, healthcheck/restart) — ✅ COMPLETED

Production-readiness değerlendirmesinin en kritik 3 maddesi kapatıldı. Backup/scheduler-isolation/logging/CI/
resource-limit işleri bu turun **kapsamı dışında** bırakıldı (kullanıcı talimatı). Mevcut domain/business logic'e
dokunulmadı; yalnızca `infra/` + iki frontend Dockerfile'ı + `application.yml`'de bir health-indicator ayarı
değişti. Local `docker compose up` akışı değişmeden çalışıyor (aşağıda doğrulandı).

**Tasarım:**

1. **API base URL (customer-web/staff-web):** `NEXT_PUBLIC_API_BASE_URL` Next.js standalone build'inde
   **build-time'da** JS bundle'a gömülüyor, container start'ta okunmuyor - `lib/api.ts`'teki
   `?? "http://localhost:8080"` fallback'i bu yüzden yalnızca Docker olmadan `next dev` çalıştırıldığında devreye
   giriyordu, ama Dockerfile'larda hiç `ARG` tanımlı olmadığı için **production build'i de sessizce aynı
   fallback'i inliyordu**. Çözüm kod tarafında değil, build/deploy tarafında: her iki Dockerfile'a
   `ARG NEXT_PUBLIC_API_BASE_URL` + `ENV` eklendi (build stage, `npm run build`'dan hemen önce); local
   `docker-compose.yml` bunu `${NEXT_PUBLIC_API_BASE_URL:-http://localhost:8080}` default'uyla build arg olarak
   geçiyor (local akış değişmiyor), yeni `docker-compose.prod.yml` ise aynı değişkeni
   `${NEXT_PUBLIC_API_BASE_URL:?...}` ile **zorunlu** kılıyor - eksikse `docker compose` build'e hiç girmeden
   loud-fail veriyor (mevcut `INTERNAL_ADMIN_TOKEN` deseniyle aynı disiplin).
2. **TLS termination / reverse proxy:** nginx yerine **Caddy** seçildi - otomatik Let's Encrypt sertifikası/yenileme
   (ayrı certbot container/cron gerekmiyor), HTTP→HTTPS redirect default, tek küçük `Caddyfile`. Üç subdomain
   (`API_DOMAIN`, `CUSTOMER_WEB_DOMAIN`, `STAFF_WEB_DOMAIN`) → sırasıyla `backend:8080`/`customer-web:3000`/
   `staff-web:3002`'ye reverse proxy. Subdomain seçimi bilinçli: backend zaten `ResponseCookie.secure(true)
   .sameSite("Lax")` kullanıyor (`QrCheckinController`, `StaffAuthController`) ve `CorsConfig` credential'lı,
   explicit-origin CORS uyguluyor - aynı kayıtlı domain altındaki subdomain'ler SameSite=Lax için "same-site"
   sayıldığından, mevcut cookie/CORS kodu **hiç değiştirilmeden** çalışmaya devam ediyor; tek gereken production
   domain'lerini `CORS_ALLOWED_ORIGINS`/`NEXT_PUBLIC_API_BASE_URL`/`MEDIA_STORAGE_PUBLIC_BASE_URL` olarak doğru
   girmek.
3. **Healthcheck + restart policy + gerçek readiness:** `postgres` zaten healthcheck'liydi; `backend`'e
   `curl .../actuator/health` (actuator zaten pom'da vardı, `management.endpoints.web.exposure.include: health,info`
   zaten açıktı, Spring Security yok → korumasız, container-içi curl için sorun değil), `customer-web`/`staff-web`'e
   yeni `GET /api/health` route (Next.js server'ın ayakta olduğunu doğrulayan, bağımlılıksız minimal endpoint) +
   `wget` healthcheck'i eklendi. Üç servise de `restart: unless-stopped`. `depends_on` gerçek readiness'e göre
   düzenlendi: frontend'ler artık `backend: condition: service_healthy` bekliyor (önceden yalnızca `service_started`
   yani backend henüz DB migration'ı bitirmeden/ayakta olmadan frontend'ler başlıyordu).

**Uygulama sırasında bulunan iki yan-etki düzeltmesi (tasarımın doğal sonucu, kapsam dışına çıkmadan):**

- Next.js standalone `server.js` dinleme adresi için `$HOSTNAME` env'ini okuyor; Docker her container'a otomatik
  `HOSTNAME=<container-id>` set ettiğinden, bu olmadan server yalnızca container'ın kendi arayüz IP'sinde dinliyor,
  `localhost`/`127.0.0.1`'de değil - container-içi healthcheck bu yüzden "Connection refused" veriyordu. Her iki
  Dockerfile'ın runtime stage'ine `ENV HOSTNAME=0.0.0.0` eklendi (resmi Next.js Docker örneğindeki bilinen düzeltme).
- Alpine'in BusyBox `wget`'i `localhost`'u önce `::1`'e çözüyor ve IPv4'e fallback yapmıyor (Node server IPv4-only
  dinliyor) - healthcheck komutlarında `localhost` yerine `127.0.0.1` kullanıldı.
- Spring Boot Actuator'ın default `MailHealthIndicator`'ı her health check'te SMTP sunucusuna bağlanmayı deniyor;
  production compose'da dev-only `mailhog` servisi yok, gerçek SMTP sağlayıcısı yavaş/geçici olarak erişilemez
  olabilir - Section 15'in "email blocker değildir" ilkesiyle tutarlı olarak `management.health.mail.enabled: false`
  eklendi, yoksa SMTP kesintisi backend'in Docker healthcheck'ini (→ restart policy + frontend'lerin
  `depends_on: service_healthy`'si) email'le hiç ilgisi olmayan bir sebeple kırardı.

**Yeni/değişen dosyalar:**

- `frontend/customer-web/Dockerfile`, `frontend/staff-web/Dockerfile`: `ARG`/`ENV NEXT_PUBLIC_API_BASE_URL`,
  `ENV HOSTNAME=0.0.0.0`.
- `frontend/customer-web/app/api/health/route.ts`, `frontend/staff-web/app/api/health/route.ts`: yeni, minimal
  `GET → {status:"ok"}`.
- `infra/docker-compose.yml`: backend/customer-web/staff-web'e healthcheck + `restart: unless-stopped`;
  frontend'lerin build'ine `NEXT_PUBLIC_API_BASE_URL` build-arg'ı (local default korunuyor); `depends_on` →
  `service_healthy`.
- `infra/docker-compose.prod.yml` (yeni): standalone production stack - `postgres`/`backend`/`customer-web`/
  `staff-web`/`caddy`. Tüm önceden opsiyonel/default'lu env değişkenleri `:?...` ile zorunlu; backend/frontend/
  postgres portları host'a açılmıyor, yalnızca Caddy'nin 80/443'ü açık. Ayrı overlay değil bilinçli olarak
  standalone dosya - compose'un `ports`/`depends_on` merge semantiği overlay'de host'a sızabilirdi.
- `infra/Caddyfile` (yeni): 3 subdomain reverse proxy, otomatik HTTPS.
- `infra/.env.prod.example` (yeni): production için gereken tüm değişkenlerin dokümantasyonu (gerçek secret yok).
- `.gitignore`: `infra/.env.prod` eklendi (daha önce yalnızca `infra/.env` kapsanıyordu).
- `backend/src/main/resources/application.yml`: `management.health.mail.enabled: false`.

**Doğrulama:**

- Backend `mvn test`: tüm suite 135/135 yeşil (0 hata/0 başarısız) - `application.yml` değişikliği sonrası.
- `customer-web`/`staff-web`: `tsc --noEmit` + `eslint` (yeni `api/health/route.ts` dahil) temiz; her iki Dockerfile
  `docker compose build` ile gerçekten build edildi (Next `next build` başarıyla `/api/health` route'unu üretti).
- **Local senaryo:** `docker compose up -d` ile gerçek stack ayağa kaldırıldı - `postgres`→`backend`→
  `customer-web`/`staff-web` sırasıyla `healthy` oldu (`depends_on: service_healthy` doğrulandı), üçü de host
  portlarından (3000/3002/8080) 200 döndü.
- **Production-benzeri senaryo:** izole bir Docker Compose projesinde (`-p qrmenu-prod-test`, ayrı network/volume,
  local stack'e dokunulmadı) `docker-compose.prod.yml` gerçek domain isimleriyle (`*.qrmenu.test`) build edilip
  ayağa kaldırıldı; Caddy `tls internal` (yalnızca bu doğrulama için, gerçek dosyada yok - Let's Encrypt ACME bu
  sandbox'tan internete çıkamıyor) ile self-signed sertifika üretti. Sonuç: `https://api.qrmenu.test/actuator/health`
  → `{"status":"UP"}`, `https://order.qrmenu.test/` ve `https://staff.qrmenu.test/` → 200, `http://order.qrmenu.test/`
  → 308 ile otomatik `https://`'ye redirect. Zorunlu env değişkeni eksik bırakıldığında `docker compose config`'in
  loud-fail verdiği ayrıca doğrulandı (`INTERNAL_ADMIN_TOKEN` deseniyle tutarlı). İzole test stack'i ve imajları
  doğrulama sonrası temizlendi.
- Kapsam dışı bırakılanlar (kullanıcı talimatıyla): backup, scheduler isolation, logging, CI, resource limits.

---

## Ürün Kararı — Ayrı Mutfak/KDS Ekranı ve `KITCHEN_STAFF` Rolü Kaldırıldı — ✅ COMPLETED

Kullanıcı kararı: sipariş operasyon modeli sadeleştirildi. Ayrı bir Mutfak/KDS bölümü artık yok;
`KITCHEN_STAFF` rolü kaldırıldı; sipariş operasyonunun tamamı (görüntüleme, kabul/red,
PREPARING → READY → COMPLETED akışı) **Kasa** ekranından, `BUSINESS_ADMIN`/`BRANCH_MANAGER`/
`CASHIER` (işletme sahibi/müdürü/çalışanı) tarafından yürütülüyor.

**Tasarım (uygulamadan önce):** Ayrı bir `kitchen` backend modülü/controller'ı yerine, mevcut
`OrderControlController` (ordering modülü, `/api/staff/branches/{branchId}/orders`) genişletildi -
eski `KitchenController`'ın (`/api/kitchen/**`, `Permission.KITCHEN_DECIDE`) sorgu/komut
uçları (queue/decide/ready/served/stream) buraya taşındı, `Permission.KITCHEN_DECIDE` →
`Permission.ORDER_PREPARE` olarak yeniden adlandırılıp CASHIER'a da verildi (önceden yalnızca
BUSINESS_ADMIN/BRANCH_MANAGER'da vardı). `OrderingService`'in preparation state machine'i
(`getKitchenQueue`/`decideOrderItem`/`markOrderItemReady`/`markOrderItemServed`, `OrderStatus.
IN_KITCHEN`, `OrderItemStatus.PENDING_REVIEW/PREPARING/READY/SERVED`) bilinçli olarak
**değiştirilmedi** - bunlar hâlâ gerçekten ihtiyaç duyulan iş mantığı, yalnızca web katmanı
sadeleşti. `RefundController`'ın `/api/kitchen/branches/{branchId}/orders/{search,refunds,
complete}` uçları da bilinçli olarak **taşınmadı** - zaten kendi javadoc'unda "çalışan test
edilmiş bir URL'yi kozmetik nedenle yeniden adlandırmak gereksiz churn olurdu" diye belgelenmiş
bir önceki karar, aynı gerekçe burada da geçerli.

**Uygulama:**

- **Backend:** `Permission.KITCHEN_DECIDE` → `ORDER_PREPARE` (rename). `StaffRole.KITCHEN_STAFF`
  enum değeri tamamen kaldırıldı; `CASHIER` artık `ORDER_PREPARE`'ı da alıyor. `com.qrmenu.kitchen`
  paketi (`KitchenController` + 4 DTO) silindi; `OrderControlController`'a yeni uçlar eklendi:
  `GET /orders/in-progress` (eski `getKitchenQueue`, ORDER_VIEW), `GET /orders/ready` (yeni -
  aşağıya bkz.), `GET /orders/stream` (eski kitchen SSE kanalı, artık ORDER_VIEW ile - önceden
  CASHIER bu kanala KITCHEN_DECIDE eksikliğinden 403 alıyordu, fark edilmemiş bir hataydı, bu
  değişiklikle kendiliğinden düzeldi), `POST /orders/items/{id}/{decide,ready,served}`
  (ORDER_PREPARE). `DecideOrderItemRequest` DTO'su `ordering.web.dto`'ya taşındı;
  `KitchenOrderResponse`/`KitchenOrderItemResponse` yerine zaten var olan (superset)
  `OrderControlOrderResponse`/`OrderControlOrderItemResponse` reuse edildi. Yeni `V23__remove_
  kitchen_staff_role.sql` migration: var olabilecek `role='KITCHEN_STAFF'` satırlarını
  `CASHIER`'a taşıyıp `staff_user_role_check` constraint'ini daraltıyor.
- **Backend (Kasa'nın "Hazır" gap'i - uygulama sırasında bulunan tasarım eksiği):**
  `getKitchenQueue` (in-progress) yalnızca `IN_KITCHEN` durumundaki siparişleri döndürüyor - bir
  sipariş son kalemi de "Hazır" işaretlenince otomatik `READY`'e yükselip bu listeden düşüyor
  (mevcut, değiştirilmeyen davranış). Eski ayrı KDS ekranında da aynı durum vardı ve "teslim
  edildi" asıl tamamlama işlemi zaten ayrı bir ekrandan (İadeler/sipariş arama) yapılıyordu - ama
  kullanıcının "Kasa, PREPARING → READY → COMPLETED akışının **tek** operasyon ekranı olsun"
  talimatıyla bu yeterli değildi. Yeni `OrderingService.getReadyOrders` + `GET /orders/ready`
  eklendi (branch teslimat modelinden bağımsız, pickup board'un aksine WAITER_DELIVERY dahil her
  READY sipariş) - Kasa'daki üçüncü bölüm ("Hazır · Teslim Bekliyor") artık `Permission.
  ORDER_COMPLETE` ile korunan mevcut complete uç noktasını (RefundController, değişmedi)
  çağırarak siparişi tek ekrandan tamamlayabiliyor.
- **Frontend (staff-web):** `app/kitchen/[branchId]` route'u tamamen silindi. `app/cashier/
  [branchId]/page.tsx` üç bölümlü tek ekrana genişletildi: "Onay Bekleyen Siparişler" (mevcut) +
  "Hazırlanıyor" (eski KDS board, item bazlı decide/ready/served) + "Hazır · Teslim Bekliyor"
  (yeni, complete butonu). Tek SSE bağlantısı (`buildOrderStreamUrl`, eski `buildKitchenStreamUrl`
  yerine) her üç listeyi de "bir şey değişti, yeniden çek" sinyaliyle tazeliyor. `lib/staffNav.ts`
  içinden "Mutfak" nav item'ı ve `KITCHEN_STAFF` rol referansları kaldırıldı. `lib/api.ts`:
  `StaffContext.role`/`StaffRole` tiplerinden `KITCHEN_STAFF` düşürüldü; `KitchenOrderItem`/
  `KitchenOrder` tipleri kaldırılıp `OrderControlItem`/`OrderControlOrder` tek tip ailesi olarak
  birleştirildi (`KitchenOrderItemOption` → `OrderItemOptionSummary`); `getKitchenQueue`/
  `decideOrderItem`/`markOrderItemReady`/`markOrderItemServed`/`buildKitchenStreamUrl` yeni
  endpoint'lere taşındı, yeni `getReadyOrders` eklendi. `app/staff/page.tsx` (personel oluşturma
  formu), `app/refunds/[branchId]`, `app/branches` sayfalarındaki "Mutfak" linkleri/seçenekleri
  kaldırıldı; `app/page.tsx` (login) ve `app/pickup/[branchId]` yorumlarındaki "mutfak" referansı
  düzeltildi. `getKitchenFinancialSummary` çağrısı (Gap-Analysis #14 ciro özeti) Kasa ekranına
  taşındı - endpoint/DTO adı (`kitchen-summary`/`KitchenFinancialSummaryResponse`) RefundController
  ile aynı "çalışan URL'yi kozmetik nedenle değiştirme" gerekçesiyle bilinçli olarak değiştirilmedi.
- **Testler:** `KitchenFlowIntegrationTest` silinip yerine `OrderPreparationFlowIntegrationTest`
  (yeni uç noktalarla, + yeni `aReadyOrderAppearsInTheReadyListUntilCompleted` testi) yazıldı.
  `OrderControlFlowIntegrationTest`, `StaffAccessFlowIntegrationTest`, `ReportingFlowIntegrationTest`,
  `RefundFlowIntegrationTest`, `DailyCloseFlowIntegrationTest`, `PickupToCompletionEndToEndTest`,
  `CrossTenantBranchAccessIntegrationTest` yeni endpoint'lere güncellendi.
  `Announcement`/`ChainComparison`/`Expense`/`MediaUpload`/`BulkAssignBranches`/`BusinessSettings`
  Flow testlerindeki "KITCHEN_STAFF izni yok" senaryoları CASHIER'a çevrildi (CASHIER de aynı
  permission'lardan yoksun). **Dört test tamamen silindi** (`DailyCloseFlowIntegrationTest.
  kitchenStaffCannotAccessDailyClose/kitchenStaffCannotExportDailyCloseExcel`,
  `ReportingFlowIntegrationTest.kitchenStaffCannotViewReports`,
  `OwnerNotificationFlowIntegrationTest.kitchenStaffCannotAccessNotificationEndpoints`,
  `OrderControlFlowIntegrationTest.kitchenStaffCannotAcceptOrRejectOrders`) - bunların test ettiği
  sınır ("REPORT_VIEW/ORDER_* iznine sahip olmayan bir personel rolü") KITCHEN_STAFF kaldırıldıktan
  sonra artık **imkansız**: kalan üç rolün (BUSINESS_ADMIN/BRANCH_MANAGER/CASHIER) hepsi zaten
  REPORT_VIEW + tüm ORDER_* izinlerine sahip - bu doğrudan kullanıcının "üç rol de sipariş
  operasyonunu tamamen yapabilsin" talimatının bir sonucu, test eksikliği değil.
- **`product-requirements.md` güncellendi:** Bölüm 1 (dört kullanıcı grubu → üç), Bölüm 8
  ("Mutfak Akışı" → "Sipariş Hazırlama Akışı (Kasa üzerinden)"), Bölüm 11 (`KITCHEN_STAFF` satırı
  kaldırıldı, `KITCHEN_VIEW`/`KITCHEN_UPDATE` örnek permission'ları `ORDER_PREPARE` ile
  değiştirildi), Bölüm 19.3 (nav bilgi mimarisinden "Mutfak" kaldırıldı, "Kitchen Display System"
  alt bölümü Kasa'nın üçüncü bölümü olarak yeniden yazıldı), modül tablosu (`kitchen` satırı
  `ordercontrol` ile birleşti), M7 roadmap notuna sonradan-kaldırıldı notu eklendi, Bölüm 28'e
  19. madde eklendi.

**Doğrulama:**

- Backend `mvn test`: tüm suite **131/131 yeşil** (0 hata/0 başarısız - önceki 130'dan +9 yeni/
  değişen `OrderPreparationFlowIntegrationTest` testi, -8 silinen obsolete test = net +1).
- `staff-web`: `next build` (tsc + Turbopack) ve `eslint .` temiz; route tablosunda `/kitchen`
  artık hiç yok.
- **Gerçek Docker Compose + Chrome doğrulaması (uçtan uca):** Yeni bir business/branch/masa/QR/
  ürün + `BUSINESS_ADMIN` staff user internal API ile oluşturuldu. `customer-web`'de gerçek QR
  check-in → sepete ekle → mock ödeme akışı çalıştırılıp sipariş `AWAITING_STORE_ACCEPTANCE`'a
  düştü. `staff-web`'de giriş yapılıp **Kasa** ekranına gidildi: sol navigasyonda "Mutfak" linki
  yok (yalnızca "Kasa"); Kasa ekranı üç bölümlü (Onay Bekleyen / Hazırlanıyor / Hazır · Teslim
  Bekliyor) tek sayfa olarak doğrulandı. Sırasıyla: **Kabul Et** → sipariş anlık (SSE, "Canlı"
  göstergesi) "Hazırlanıyor" bölümüne düştü; **Onayla** (item decide) → "Hazırlanıyor" rozetine
  geçti; **Hazır** → sipariş "Hazırlanıyor" bölümünden kayboldu (READY'e yükseldiği için, beklenen
  davranış) ve "Hazır · Teslim Bekliyor" bölümünde göründü (yeni eklenen üçüncü bölüm); **Teslim
  Edildi / Tamamlandı** → sipariş oradan da kayboldu (`COMPLETED`). Şube/staff listelerinde
  "Mutfak" kısayolu yok, yalnızca "Kasa"/"İadeler"/"Raporlar". Personel oluşturma formunda rol
  seçenekleri: İşletme Yöneticisi/Şube Sorumlusu/Kasa (Mutfak Personeli yok).
- **Kapsam dışı, ayrıca bulunan (düzeltilmedi):** customer-web'in ödeme başarı ekranı hâlâ eski
  "Siparişiniz mutfağa iletildi" metnini gösteriyor - bu, Gap-Analysis #1'in (kasa kabul/red
  kapısı) customer-web tarafında hiç güncellenmemiş bir kalıntı kopya metni, bu revizyonun
  kapsamına girmiyor (customer tracking/SSE akışını bozmama talimatı + "başka feature'a geçme").
- Git commit/push kullanıcı istemedikçe yapılmadı.

---

## Ürün Kararı — Item Bazlı Kitchen Decision Adımı Kaldırıldı + Son `/api/kitchen/**` Kalıntıları Taşındı — ✅ COMPLETED

Bir önceki revizyon (yukarıda) ayrı Mutfak/KDS ekranını ve `KITCHEN_STAFF` rolünü
kaldırmıştı ama iki kalıntı bırakmıştı: (1) Kasa'nın "Hazırlanıyor" bölümünde hâlâ
item bazlı bir "Onayla" (decide) + ayrı "Hazır"/"Teslim Edildi" adımları vardı, (2)
`RefundController` (search/refunds/complete) hâlâ `/api/kitchen/**` altındaydı, kendi
javadoc'unda bilinçli olarak taşınmadığı belirtilmişti. Kullanıcı bu ikisini de
kapsam dışı bırakmadı, açıkça kaldırılmasını istedi; ayrıca customer-web'in ödeme
başarı ekranındaki eski "Siparişiniz mutfağa iletildi" metni de bu revizyonun kapsamına
alındı.

**Tasarım (uygulamadan önce):** Akış artık tam olarak `AWAITING_STORE_ACCEPTANCE →
ACCEPT → PREPARING → READY → COMPLETED` - PREPARING iç adı hâlâ `IN_KITCHEN` (DB
constraint + tüm mevcut kod, kozmetik enum rename'i gereksiz churn olurdu, aynı
gerekçe `RefundController`/`kitchen-summary` kalıntı isimlerinde zaten kullanılmıştı).
Kasa ACCEPT ettiğinde her `OrderItem` otomatik ve tam adette kabul edilir
(`OrderItem.acceptFully()`, eski `decide(int)`'in yerine) - artık `acceptedQuantity`
her zaman `orderedQuantity`'ye eşit, `rejectedQuantity` her zaman 0 (item bazlı kısmi
red imkansız hale geldi, yalnızca kasa REJECT ile tüm sipariş reddedilebilir).
PREPARING → READY, item bazlı bir rollup değil, tek bir sipariş bazlı Kasa aksiyonu
(`OrderingService.markOrderReady` - tüm item'ları da bulk `READY`'e taşır).
`completeOrder` (READY → COMPLETED) de aynı şekilde tüm item'ları bulk `SERVED`'a
taşır - böylece customer tracking'in item bazlı status'ü (`OrderTrackingController`)
hâlâ doğru bilgi veriyor, sadece artık manuel bir personel adımı değil, otomatik bir
yan etki. `OrderItemStatus.REJECTED`/`SERVED` değerleri DB constraint'te kalıyor
(kullanılmaya devam ediyor, sadece artık kimin tetiklediği değişti) - enum'dan
kaldırmak gereksiz migration churn'ü olurdu.

**Uygulama:**

- **Backend:** `OrderItem.decide(int)` → `acceptFully()` (parametresiz, her zaman tam
  kabul). `OrderingService.acceptOrder` artık order'ı `IN_KITCHEN`'a taşırken tüm
  item'ları da `acceptFully()` ile geçiriyor. `decideOrderItem`/`markOrderItemReady`/
  `markOrderItemServed`/`transitionOrderItem`/`recalculateOrderReadiness` silindi;
  yerine tek `markOrderReady(branchId, orderId)` geldi (order + tüm item'lar bulk
  READY). `completeOrder` tüm item'ları bulk SERVED'a taşıyacak şekilde genişledi.
  `OrderControlController`: `POST /items/{id}/decide` ve `POST /items/{id}/ready`/
  `/served` kaldırıldı; yerine `POST /{orderId}/ready` geldi (Permission.ORDER_PREPARE,
  değişmedi). `DecideOrderItemRequest` DTO'su silindi. `RefundController`'ın
  `@RequestMapping`'i `/api/kitchen/branches/{branchId}/orders` →
  `/api/staff/branches/{branchId}/orders` (OrderControlController ile aynı prefix,
  route suffix'leri çakışmıyor - search/{orderId}/refunds/{orderId}/complete vs.
  pending-acceptance/accept/reject/in-progress/ready/stream/{orderId}/ready). Artık
  hiçbir backend endpoint'i `/api/kitchen/**` altında değil.
- **Frontend (customer-web):** `PaymentSheet.tsx`'teki "Ödeme başarılı. Siparişiniz
  mutfağa iletildi." → "Ödeme başarılı. Siparişiniz işletmeye iletildi, onay
  bekleniyor." (gerçek durum `AWAITING_STORE_ACCEPTANCE`, doğrudan `IN_KITCHEN` değil).
- **Frontend (staff-web):** `lib/api.ts`: `decideOrderItem`/`markOrderItemReady`/
  `markOrderItemServed` kaldırıldı, yerine tek `markOrderReady(branchId, orderId)`
  (`POST /{orderId}/ready`); `searchOrderByNumber`/`createRefund`/`completeOrder`
  `/api/kitchen/**` → `/api/staff/**`. `cashier/[branchId]/page.tsx`: "Hazırlanıyor"
  bölümündeki item bazlı Onayla/Hazır/Teslim Edildi UI'ı (adet input'u, per-item
  status badge/sıralama) tamamen kaldırıldı - artık item'lar salt okunur listeleniyor,
  kart başına tek bir "Hazır" butonu var (aynı "Onay Bekleyen"/"Hazır · Teslim
  Bekliyor" bölümlerindeki sade item listesi deseniyle tutarlı). Kullanılmayan
  `.itemTop`/`.itemName`/`.itemActions`/`.quantityInput` CSS sınıfları silindi.
- **Testler:** `OrderPreparationFlowIntegrationTest`: decide/ready/served item
  çağrıları kaldırılıp tek `POST /{orderId}/ready` ile değiştirildi;
  `aFullyRejectedSingleItemOrderStillRollsUpToReady` testi tamamen silindi (test ettiği
  senaryo - item bazlı kısmi red - artık imkansız). `RefundFlowIntegrationTest`,
  `PickupToCompletionEndToEndTest`, `DailyCloseFlowIntegrationTest`,
  `ReportingFlowIntegrationTest`, `CrossTenantBranchAccessIntegrationTest`: kalan
  `/api/kitchen/**` çağrıları `/api/staff/**`'e taşındı, decide adımları kaldırıldı
  (accept zaten otomatik tam kabul ediyor).
- **`product-requirements.md` güncellendi:** Bölüm 6 (akış diyagramı `ACCEPT →
  PREPARING → READY → COMPLETED`), Bölüm 7.1 (Order state diyagramı), Bölüm 7.3 (item
  bazlı red artık kısmi refund önkoşulu değil, ayrı bir kasiyer aksiyonu), Bölüm 8
  (item bazlı kabul/red modelinin kaldırıldığı açıkça belirtildi), Bölüm 9 (müşteri
  bildirim listesinden "mutfakta" kaldırıldı), Bölüm 11 (`ORDER_PREPARE` açıklaması),
  Bölüm 19.3 (Kasa "Hazırlanıyor" bölümü açıklaması), M7 roadmap notu, Bölüm 28'e 20.
  madde eklendi.

**Doğrulama:**

- Backend `mvn test`: tüm suite **130/130 yeşil** (0 hata/0 başarısız - önceki
  131'den net -1: bir obsolete test silindi, hiçbiri eklenmedi).
- `staff-web`: `tsc --noEmit`, `eslint .`, `next build` temiz. `customer-web`:
  `tsc --noEmit`, `eslint .` temiz.
- **Gerçek Docker Compose + Chrome doğrulaması (uçtan uca, iki ayrı sipariş):**
  Backend/staff-web/customer-web image'ları yeniden build edilip container'lar
  yeniden başlatıldı. Yeni bir business/branch/masa/QR/ürün + `BUSINESS_ADMIN` staff
  user internal API ile oluşturuldu. **Sipariş #1:** `customer-web`'de QR check-in →
  sepete ekle → mock ödeme; ödeme başarı ekranında yeni metin doğrulandı ("işletmeye
  iletildi, onay bekleniyor", eski "mutfağa iletildi" yok). `staff-web`'de giriş
  yapılıp Kasa'ya gidildi - sol navda "Mutfak" yok. **Kabul Et** → sipariş anında
  (SSE) "Hazırlanıyor" bölümüne düştü, **item bazlı hiçbir Onayla/karar adımı
  olmadan** doğrudan tek "Hazır" butonuyla göründü. **Hazır** → "Hazır · Teslim
  Bekliyor"e geçti. **Teslim Edildi / Tamamlandı** → sipariş tüm listelerden kayboldu.
  Müşterinin takip sayfasında (`/order/track/{token}`) 5 adımlı timeline (Ödeme →
  İşletme onayı → Hazırlanıyor → Hazır → Tamamlandı) hepsi ✓ ve item satırında
  "Teslim edildi" doğru göründü (bulk-served otomasyonu doğrulandı). **Sipariş #2:**
  aynı akışla ödenip Kabul Et'e kadar götürüldü, ardından **İadeler** ekranından
  (`/api/staff/branches/{id}/orders/search`, artık `/api/kitchen/**` değil) sipariş
  numarasıyla arandı ve 1 adet kısmi refund başlatıldı (`/api/staff/.../refunds`) -
  "İade tamamlandı: ₺150,00" ve "Geçmiş İadeler" listesinde doğru göründü; taşınan
  uç noktaların gerçek tarayıcıda çalıştığı doğrulandı.
- Git commit/push kullanıcı istemedikçe yapılmadı.

---

## Production Readiness — CRITICAL #4 (PostgreSQL backup/restore) — ✅ COMPLETED

CRITICAL #1-3'ün (yukarıda) kapsam dışı bıraktığı backup/restore maddesi bu turda kapatılıyor.
S3/cloud storage entegrasyonu kullanıcı talimatıyla kapsam dışı - backup'lar host'ta local kalıyor.
Diğer production-readiness maddelerine (scheduler isolation, logging, CI, resource limits) bu turda
geçilmiyor.

**Tasarım (uygulamadan önce):**

1. **Otomatik backup + retention — ayrı bir sidecar container (`postgres-backup`):** `backend`
   (uygulama) container'ından tamamen bağımsız, `postgres:16-alpine` tabanlı yeni bir servis
   (`infra/docker/postgres-backup/`). `postgres` servisiyle aynı image ailesi olduğundan `pg_dump`
   zaten mevcut, ekstra bağımlılık gerekmiyor. Cron yerine basit bir `entrypoint.sh` loop'u
   (`backup.sh` çalıştır → `sleep $BACKUP_INTERVAL_SECONDS`) tercih edildi - busybox `crond`'un env
   inheritance'ı ekstra dolambaç gerektiriyor, halbuki bu servisin tek işi periyodik dump, cron'un
   sunduğu zamanlama esnekliğine ihtiyaç yok. `backup.sh`: `pg_dump --format=plain --no-owner
   --no-privileges | gzip` ile `${POSTGRES_DB}_<UTC-timestamp>.sql.gz` üretir (geçici `.tmp` adıyla
   yazılıp atomically `mv` edilir - yarım kalan dosya asla "tamamlanmış backup" gibi görünmez),
   ardından `find ... -mtime +$BACKUP_RETENTION_DAYS -delete` ile eski dosyaları temizler (default 7
   gün). Depolama, adlandırılmış bir Docker volume değil, host bind-mount (`infra/backups/`) - operatör
   bunu doğrudan `rsync`/`scp` ile başka bir yere kopyalayabilir, `docker volume` API'siyle uğraşmaz
   (S3 entegrasyonu kapsam dışı olduğundan bu, "backup'ı host dışına taşımanın" en basit yolu).
   `docker-compose.yml`/`docker-compose.prod.yml`'de `depends_on: postgres: condition: service_healthy`
   ile postgres hazır olmadan ilk backup denenmiyor.
2. **Manuel backup/restore script'leri (`infra/scripts/backup.sh`, `restore.sh`):** Host'tan
   `docker compose exec postgres pg_dump/psql` çağırır - host'a ayrıca postgres-client kurulması
   gerekmez, tüm iş zaten postgres image'ında var olan araçlarla container içinde yapılır. Dev
   (`docker-compose.yml` + `infra/.env`) ve prod (`docker-compose.prod.yml` + `infra/.env.prod`)
   ikisini de `[dev|prod]` argümanıyla destekler - CRITICAL #1-3'teki `docker compose --env-file
   .env.prod -f docker-compose.prod.yml` deseniyle tutarlı. `restore.sh` yıkıcı bir işlem olduğundan
   (hedef DB drop+recreate edilir) veritabanı adının elle yazılmasını isteyen bir onay adımı var.
3. **Doğrulama planı:** Gerçek dev stack'te (`docker compose up`) mevcut veriye bakılıp bir referans
   snapshot alınacak (örn. `pg_dump` çıktısının satır sayısı/hash'i veya bir tablo `SELECT count(*)`),
   `scripts/backup.sh` ile gerçek bir backup üretilecek, ardından DB kasıtlı olarak drop edilip temiz
   bir DB'ye `scripts/restore.sh` ile geri yüklenecek, restore sonrası veri referans snapshot'la
   karşılaştırılacak. Ayrıca `postgres-backup` sidecar'ının build olup ilk otomatik backup'ı gerçekten
   ürettiği de ayrı doğrulanacak.

**Uygulama:** Tasarım aynen uygulandı, yol boyunca tasarımı değiştiren bir bulgu çıkmadı.

**Yeni/değişen dosyalar:**

- `infra/docker/postgres-backup/Dockerfile` (yeni): `postgres:16-alpine` tabanlı, `backup.sh` +
  `entrypoint.sh` kopyalanıp executable yapılıyor.
- `infra/docker/postgres-backup/backup.sh` (yeni): `pg_dump | gzip` → `.tmp` → atomic `mv`, ardından
  `find -mtime +$BACKUP_RETENTION_DAYS -delete` (busybox `find`'ın `-mtime`/`-delete` desteği
  doğrulandı - `docker run --rm postgres:16-alpine find --help`).
- `infra/docker/postgres-backup/entrypoint.sh` (yeni): `mkdir -p $BACKUP_DIR` → sonsuz `backup.sh` →
  `sleep $BACKUP_INTERVAL_SECONDS` loop'u; `backup.sh` başarısız olsa da loop kırılmıyor (bir sonraki
  interval'de tekrar denenir).
- `infra/docker-compose.yml`, `infra/docker-compose.prod.yml`: yeni `postgres-backup` servisi
  (`depends_on: postgres: condition: service_healthy`, `./backups:/backups` bind-mount); prod'da
  `POSTGRES_DB`/`USER`/`PASSWORD` diğer servislerdeki gibi `:?...` ile zorunlu. `volumes:` bloğuna,
  backup'ların bilinçli olarak named volume değil bind-mount kullandığını açıklayan bir not eklendi.
  Her iki dosya `docker compose config` ile doğrulandı.
- `infra/scripts/backup.sh`, `infra/scripts/restore.sh` (yeni): host'tan `docker compose exec postgres
  pg_dump`/`psql` çağıran manuel script'ler, `[dev|prod]` argümanıyla `infra/.env`/`infra/.env.prod`'u
  okuyor. `restore.sh`: `pg_terminate_backend` ile açık bağlantılar kapatılıyor → `DROP DATABASE` →
  `CREATE DATABASE` → `gunzip | psql -v ON_ERROR_STOP=1`; DB adının elle yazılmasını isteyen bir onay
  adımı var (yanlışlıkla yanlış ortamda çalıştırmaya karşı).
- `infra/.env.example`, `infra/.env.prod.example`: opsiyonel `BACKUP_INTERVAL_SECONDS`/
  `BACKUP_RETENTION_DAYS` dokümante edildi (default 86400s/7 gün).
- `.gitignore`: `infra/backups/` eklendi (gerçek yedekler asla commit'lenmemeli).
- `README.md`: "Backup / restore" başlığı altında otomatik sidecar'ın davranışı + manuel
  `backup.sh`/`restore.sh` kullanımı özetlendi ("Production'a çalıştırma" bölümünden hemen sonra).

**Doğrulama (gerçek Docker Compose, çalışan dev stack üzerinde):**

- `docker compose -f docker-compose.yml config` ve `--env-file .env.prod.example -f
  docker-compose.prod.yml config`: ikisi de hatasız (syntax + zorunlu değişken doğrulaması).
- `docker compose build postgres-backup` + `docker compose up -d postgres-backup`: gerçek stack'e
  (postgres/backend/customer-web/staff-web/mailhog zaten ayaktaydı) eklendi, `depends_on:
  service_healthy` bekleyip **ilk otomatik backup'ı gerçekten üretti** (log: `dump complete:
  /backups/qrmenu_20260813T211406Z.sql.gz (32.0K)`), dosya host'ta `infra/backups/`'ta göründü ve
  `gunzip -c ... | head` ile gerçek bir `pg_dump` çıktısı olduğu doğrulandı.
- **Referans snapshot:** restore öncesi gerçek (o an backend'in kullandığı) veritabanının tüm 34
  public tablosu için `information_schema` üzerinden **exact** (estimate değil) `COUNT(*)` alındı (35
  satır → örn. `business:14, staff_user:12, table_visit:28, ...`), ayrıca `business` ve `staff_user`
  tablolarının içerik `md5` checksum'ı hesaplandı.
- **Manuel backup:** `./scripts/backup.sh` → `backups/qrmenu_manual_<ts>.sql.gz` gerçekten üretildi.
- **Yıkıcı restore testi:** `./scripts/restore.sh backups/qrmenu_manual_<ts>.sql.gz dev` çalıştırıldı -
  script açık bağlantıları sonlandırdı, **gerçek `qrmenu` veritabanını drop edip temiz olarak yeniden
  oluşturdu**, ardından manuel backup'tan restore etti (`COPY 15`, `COPY 51`, `COPY 14`, ... - her satır
  sayısı canlı `pg_dump` çıktısıyla bire bir eşleşti).
- **Restore sonrası doğrulama:** aynı exact-`COUNT(*)` sorgusu tekrar çalıştırıldı → **34 tablonun
  tamamında satır sayısı referansla birebir aynı** (`diff` boş çıktı verdi). `business`/`staff_user`
  içerik `md5` checksum'ları da **birebir eşleşti** (satır sayısının ötesinde gerçek veri içeriğinin de
  değişmediğini kanıtlıyor).
- **Uygulama düzeyinde doğrulama:** `backend` container'ı restore edilmiş DB'ye karşı yeniden başlatıldı
  - `GET /actuator/health` → `{"status":"UP"}`, `customer-web`/`staff-web` → 200 (restore edilen
  veritabanının gerçekten çalışan uygulama tarafından sorunsuz kullanılabildiği doğrulandı, yalnızca SQL
  seviyesinde değil).
- **Retention testi:** `infra/backups/`'a mtime'ı 10 gün öncesine ayarlanmış sahte bir `.sql.gz` dosyası
  bırakıldı, sidecar'ın `backup.sh`'ı container içinde manuel tetiklendi → yeni bir dump üretildi **ve**
  10 günlük sahte dosya `retention (7d) deleted:` logu ile silindi; aynı anda duran diğer (güncel)
  dosyalar dokunulmadan kaldı.
- Test sırasında oluşan fazladan yedek dosyası temizlendi, `infra/backups/`'ta bir otomatik + bir manuel
  örnek yedek bırakıldı (kanıt olarak, gitignore'lu). Stack'in geri kalanı (postgres/backend/frontend'ler)
  test boyunca kesintisiz `healthy` kaldı.
- Kapsam dışı bırakılanlar (kullanıcı talimatıyla): S3/cloud storage entegrasyonu, diğer
  production-readiness maddeleri (scheduler isolation, logging, CI, resource limits).
- Git commit/push kullanıcı istemedikçe yapılmadı.

---

## Kasa Ekranı Görsel Kimlik Yenilemesi — "sıcak" tema — ✅ COMPLETED

Kullanıcı isteği: mevcut siyah/gri "Tide" kimliğinden memnun değil, bir Dribbble referansına
(`Restaurant POS UI`, Tedi Kurniadi) benzer açık/sıcak/modern bir restoran-POS hissi istiyor. Kapsam
bilinçli olarak yalnızca Kasa ekranıyla sınırlı tutuldu ("bu turda diğer staff ekranlarına yayma").
Referans canlı Chrome ile incelendi (WebFetch boş döndü, JS-render'lı sayfa) - sıcak turuncu/kiremit
accent, krem/şeftali nötr yüzeyler, beyaz kartlar, güçlü tipografi hiyerarşisi öne çıkan unsurlardı.
Referans bir menü-seçim POS'u, Kasa ise bir sipariş kuyruğu/triyaj ekranı olduğundan layout birebir
kopyalanmadı - yalnızca görsel dil (palet/kart stili/ikonografi/CTA'lar) uyarlandı.

**Kapsam çelişkisi çözümü:** Kullanıcının "profesyonel sidebar/application shell" hedefi ile "diğer
ekranlara yayma" kısıtı doğrudan çelişiyordu (AppShell tüm staff-web ekranlarında ortak). Kullanıcıya
soruldu, "AppShell'i de bu turda yeniden tasarla" onayı alındı - ama bunu literal global bir değişiklik
yerine, `AppShell`'e `theme?: "default" | "warm"` prop'u ekleyerek çözdük: `.warm` class'ı yalnızca
`--color-*`/`--font-family-display` custom property'lerini override ediyor, tüm shared component'ler
(Button/Badge/Card/Select/Textarea/EmptyState/ErrorState/IconButton) zaten yalnızca bu token'ları
okuduğu için tek bir override bloğu sidebar+topbar+içerik zincirinin tamamını temalıyor. Yalnızca Kasa
sayfası (`app/cashier/[branchId]/page.tsx`) `<AppShell theme="warm">` çağırıyor - başka hiçbir route bu
prop'u geçmediğinden diğer tüm ekranların "Tide" kimliği (renk + font) birebir korundu; canlı testte
`/dashboard` ziyaret edilerek doğrulandı.

**Tasarım kararı - imza öğesi.** Sipariş kartları zarif bir "mutfak fişi/adisyon" motifiyle tasarlandı:
kartın üst kenarında ince bir tırtık/dikiş çizgisi (`repeating-linear-gradient` ile 1px yükseklikte,
dekoratif ve tek başına - okunabilirliği hiç etkilemiyor) ve sipariş numarası sistem monospace stack'iyle
(termal fiş yazıcısı referansı, ekstra font yükü yok). Kullanıcı geri bildirimiyle ("adisyon metaforunu
zarif tut") bu tek dekoratif detayla sınırlı tutuldu - bekleme süresi urgency mantığı (Adım 4 Tasarım
Yenilemesi'nin "tide edge"i) hiç değişmeden aynen taşındı.

**Tasarım kararı - üç durumun ayrımı.** Kullanıcı geri bildirimiyle ("yalnızca kahverengi tonlarla değil,
güçlü ama paletle uyumlu status accent renkleri") üç kanban kolonu (Onay Bekleyen/Hazırlanıyor/Hazır) her
biri kendi `--column-accent`'ini taşıyor - sırasıyla ember/kiremit (primary, zaten urgency'nin "normal"
tonu), hardal-altın (warning) ve orman yeşili (success); kolon üst kenar çizgisi + ikon + sayaç rozeti bu
renkte. Kart bazındaki urgency (ember→amber→kırmızı) mantığı ayrı ve değişmedi - üç kolonun kimliği ile
tek bir kartın bekleme aciliyeti iki farklı, birbiriyle çakışmayan sinyal.

**Layout.** Üç bölüm (önceden alt alta tam genişlik) 3 sütunlu bir kanban board'a dönüştürüldü
(≥1100px yan yana, altında `1fr`'e yığılıyor - `page.module.css` `.board` media query). `--container-
width-kiosk` (1920px, KDS ekranı kaldırıldığından beri kullanılmayan bir token) yeniden kullanıldı - kanban
board'un geniş POS/kiosk ekranlarında nefes almasına izin veriyor. Finansal özet üç ayrı KPI kartına
(ikon + değer) dönüştürüldü. `lucide-react` eklendi (proje daha önce hiç ikon kütüphanesi kullanmıyordu) -
`Timer`/`ChefHat`/`BellRing` kolon kimliği ikonları, `Check`/`X`/`Send` aksiyon ikonları,
`UtensilsCrossed` masa etiketi ikonu. Display fontu Kasa'ya özel: `Bricolage Grotesque` (next/font,
`--font-display-warm` değişkeninde, yalnızca `.warm` scope'unda `--font-family-display`'i işaret ediyor)
- diğer ekranların display fontu (Plus Jakarta Sans) etkilenmedi.

**Erişilebilirlik düzeltmesi.** İlk taslak palet (`#CC5023`/`#C98A1D`/`#3F7D4E`) düz renk olarak iyi
görünüyordu ama kendi %10-16 tint arka planları üzerinde (badge metni, kanban sayaç rozeti) WCAG AA
4.5:1'in altına düşüyordu - `warning` en kötü durumda yalnızca ~2:1'e kadar iniyordu. Python'da
`(L1+0.05)/(L2+0.05)` kontrast formülüyle her rengin hem beyaz/kart hem kanban tepsi zemini üzerindeki en
kötü durumu hesaplandı, üçü de ikisinde birden ≥4.5:1'e geçecek şekilde koyultuldu (`#963B1A`/`#775111`/
`#31623D`) - üç hue hâlâ net ayrışıyor (kiremit/zeytin-hardal/orman yeşili), yalnızca daha az neon.
Primary'nin solid buton arka planı (beyaz metin) de aynı yöntemle 4.43:1'den 7.15:1'e çıkarıldı. Dark mode
paleti ilk taslakta zaten hepsinde ≥4.5:1 veriyordu, değiştirilmedi.

**Uygulama:** Business logic/API çağrıları (`handleAccept`/`handleSubmitReject`/`handleMarkReady`/
`handleComplete`, SSE reconnect, urgency hesaplama) hiç değişmedi - yalnızca JSX yapısı/className'ler/
ikonlar ve CSS. Adım adım commit edildi: (1) `lucide-react` bağımlılığı, (2) `AppShell` warm theme
scaffolding, (3) `Bricolage Grotesque` font yükleme, (4) Kasa sayfası kanban+fiş kartı yeniden yazımı,
(5) WCAG kontrast düzeltmesi.

**Doğrulama:** `npx tsc --noEmit` + `npx eslint` + `npm run build` (staff-web) her commit'te temiz. Canlı
Chrome testi yapıldı - `infra-staff-web-1` container'ı geçici durdurulup yerine `npx next dev -p 3002`
başlatıldı, `/internal/businesses/{id}/staff-users` ile geçici bir `BUSINESS_ADMIN` test hesabı
(`kasa-warm-verify@qrmenu.local`) oluşturuldu. Var olan bir şubeye (ürünleri/masası olan "Kadikoy Subesi")
doğrudan Postgres'e 5 test siparişi seed edildi - iki "Onay Bekleyen" (biri normal, biri branch timeout'unu
aşmış "kritik" - kırmızı nabız + sol kenar), iki "Hazırlanıyor" (biri normal, biri "warning" eşiğini aşmış)
ve bir "Hazır". Ekran görüntülerinde üç kolonun accent renkleri, fiş kartı tırtık çizgisi, monospace
sipariş no, KPI şeridi ve sidebar+topbar'ın warm temaya geçtiği doğrulandı; aynı oturumda `/dashboard`
ziyaret edilip o ekranın hâlâ eski "Tide" kimliğinde olduğu (scope sızıntısı yok) doğrulandı. Fonksiyonel
doğrulama: "Kabul Et" butonuna gerçekten tıklanıp siparişin canlı API çağrısıyla "Onay Bekleyen"den
"Hazırlanıyor"a taşındığı (SSE/refetch dahil) doğrulandı. Reddet formu (Select/Textarea/Button) açılıp
kapatılarak tema geçişinin form kontrollerine de uygulandığı görüldü. Test sonunda seed edilen 5 sipariş/
order-item/table-visit/anonymous-session, test audit-log/staff-session satırı ve test hesabı DB'den
silindi (her DELETE ayrı transaction olarak - ilk denemede çoklu-statement `psql -c` çağrısının FK
hatasında tüm batch'i implicit olarak rollback ettiği fark edildi, tek tek yeniden çalıştırılıp
doğrulandı), yerel `next dev` kapatıldı, `infra-staff-web-1` container'ı yeniden başlatılıp `healthy`
durumuna döndüğü doğrulandı. Işık temasında canlı Chrome testi yapılmadı (Adım 1-6 Tasarım Yenilemesi
turlarıyla aynı düşük-risk gerekçesi) - bunun yerine tüm renk çiftleri programatik WCAG hesabıyla
doğrulandı (yukarıya bakınız). Backend değişikliği yok.

## Dev Veritabanı Temizliği — Tek Kalıcı Personel Hesabı — ✅ COMPLETED

Kullanıcı isteği: DB'deki dağınık test `staff_user` kayıtları (12 adet, çeşitli test işletmelerine ait)
tamamen silinsin, yerine tek bir kalıcı personel hesabı oluşturulsun ve bundan sonraki tüm manuel/canlı
testlerde o hesap kullanılsın.

**Uygulama:** `staff_session` (20) ve `staff_user_branch` (4) satırları silindi, `audit_log_entry`daki
`actor_staff_user_id` FK'leri (34 satır, `NO ACTION` kısıtı var) NULL'landı, ardından `staff_user`
tablosundaki 12 kayıt da silindi. Yerine `/internal/businesses/{id}/staff-users` (internal admin token ile)
üzerinden `BUSINESS_ADMIN` rolünde tek bir kalıcı hesap oluşturuldu — **"Test Restoran" işletmesi / "Merkez
Şube"** altında (bu işletme zaten geliştirme günlüğünde en sık referans verilen dev/test işletmesiydi).
Giriş `/api/staff/auth/login` ile doğrulandı (200 OK). Hesap bilgileri (email + şifre) buraya **bilerek
yazılmadı** — repo GitHub'a push ediliyor; kimlik bilgileri yalnızca yerel hafıza kaydında tutuluyor.
Bundan sonraki tüm manuel/canlı test ihtiyaçlarında yeni geçici hesap açmak yerine bu tek hesap kullanılmalı.

## staff-web Tema Davranışı — Light Varsayılan + Opsiyonel Dark Toggle — ✅ COMPLETED

Kullanıcı isteği: staff-web sistem `prefers-color-scheme: dark` nedeniyle otomatik dark açılmasın,
varsayılan tema Light olsun, AppShell topbar'a Light/Dark toggle eklensin, seçim localStorage'da
saklansın, Kasa'nın warm görsel dilinin light varyantı ana deneyim olsun, dark opsiyonel kalsın,
business logic'e dokunulmasın.

**Tasarım:** Var olan tema mimarisi zaten CSS custom-property override'larına dayanıyordu
(`app/globals.css` içinde `@media (prefers-color-scheme: dark) { :root { ... } }`, Kasa'nın kendi
`.warm` scope'unda da aynı deseni tekrar eden ikinci bir `@media` bloğu) - sorunun kaynağı bu iki
blok, OS tercihini doğrudan okuyordu. Çözüm: `@media (prefers-color-scheme: dark)` yerine açık bir
`data-theme="dark"` attribute'una (varsayılan yok, yalnızca kullanıcı seçerse `<html>`'e eklenir)
geçildi - `:root[data-theme="dark"]` (globals.css) ve `:global(html[data-theme="dark"]) .warm`
(AppShell.module.css, CSS Modules'de global bir attribute selector'ı yerel `.warm` class'ıyla
birleştirmek için `:global()` gerekiyor). Yeni dosyalar: `lib/theme.ts` (tip + localStorage anahtarı
+ `useSyncExternalStore` için store fonksiyonları + `<html>`'e uygulanan blocking init script'in
string hali) ve `components/layout/ThemeToggle.tsx` (AppShell topbar'a eklenen ikon buton, `lucide-react`
Sun/Moon). `app/layout.tsx`'in `<body>`'sindeki ilk eleman olarak bu init script senkron çalıştırılıyor
- yalnızca localStorage'daki *kayıtlı* seçimi okuyup uyguluyor (`prefers-color-scheme`'e hiç bakmıyor),
böylece dönen bir kullanıcı dark seçmişse hydration'dan önce bile flaş olmadan dark açılıyor, ama hiçbir
zaman salt OS ayarından dolayı dark açılmıyor.
**`ThemeToggle`'da `useEffect` içinde `setState` çağırmak** (`document.documentElement`'ten okunan
gerçek tema ile mount sonrası senkronize etmek için) `react-hooks/set-state-in-effect` lint kuralına
takıldı - React'ın önerdiği çözüm olan `useSyncExternalStore` kullanıldı (`lib/theme.ts`'teki modül
seviyesi listener `Set`'i + `applyTheme`'in bunları bildirmesi), bu hem lint'i geçti hem de hydration
mismatch riskini ortadan kaldırdı (server/pre-hydration snapshot hep `"light"`, DOM zaten farklıysa
React kendisi güvenle senkronize ediyor). Business logic dosyalarına dokunulmadı - yalnızca
`app/globals.css`, `components/layout/AppShell.{tsx,module.css}`, iki yeni dosya.

**Doğrulama:** `npx tsc --noEmit`, `npx eslint . --max-warnings 0`, `npm run build` temiz. Docker
image yeniden build edilip `infra-staff-web-1` yeniden başlatıldı (`healthy`). Gerçek Chrome'da: sistem
dark iken login sayfası ve Dashboard **Light** açıldı (regression doğrulandı); topbar'daki toggle'a
tıklanınca **Dashboard gerçekten Dark'a geçti**, sayfa yenilenince (`navigate` ile reload) flaşsız
şekilde Dark kaldı (`localStorage`/`data-theme` ikisi de `"dark"` - JS ile doğrulandı); Kasa ekranına
gidilip **warm kimliğin dark varyantı** (koyu kahve zemin + turuncu accent) doğrulandı; toggle tekrar
Light'a çevrilip **Kasa'nın light warm kimliği** (Adım 4/5'te WCAG için koyultulmuş tonlar dahil) bozulmadan
göründüğü, Menü sayfasına geçilince Dark/Light tercihinin diğer (warm olmayan) ekranlara da tutarlı
uygulandığı doğrulandı; konsolda hydration/hata mesajı çıkmadı. Test sonunda tercih **Light**'a
bırakıldı. Backend'e dokunulmadı.

## staff-web Görsel Yön Değişikliği — Warm/Tide'dan Modern POS Kimliğine — ✅ COMPLETED

Kullanıcı isteği: Mevcut warm/Tide görsel yönünden memnun değil - bunu iyileştirmek değil, **yön
değiştirmek** isteniyor. Referans: dribbble.com/shots/26146760 ("Restaurant POS UI"). Hedefler: beyaz/çok
açık nötr zemin, temiz beyaz yüzeyler, koyu charcoal tipografi, tek güçlü modern primary accent; net durum
renkleri (bekliyor=amber/orange, hazırlanıyor=blue/indigo, hazır=green, kritik=red); daha kompakt KPI
kartları; daha güçlü sidebar hiyerarşisi; daha modern spacing/typography; Kanban kolonları büyük bej kutular
gibi görünmemeli; sipariş kartlarında masa/bekleme süresi/tutar/ana aksiyon saniyeler içinde okunabilmeli;
büyük boş alanlar azalmalı; restoran hissi tüm ekranı beje/kahverengiye boyayarak verilmemeli; jenerik
SaaS ya da eski POS görünümünden kaçınılmalı. Business logic/API davranışlarına dokunulmayacak. Bu turda
yalnızca **ortak staff-web AppShell + Kasa ekranı** bu yeni dile geçiyor - diğer sayfaların içeriği redesign
edilmiyor, yalnızca AppShell paylaşımlı olduğu için diğer ekranlara sızan "ortak shell etkisi" kabul
edilebilir. `frontend-design` skill'i kullanıldı.

**Mimari karar - Kasa'nın ayrı "warm" kimliği tamamen kaldırılıyor:** Kasa artık AppShell'in scoped
`--color-*` override'ı olmadan, TÜM staff-web ile aynı paylaşılan kimliği kullanacak. Bu, "restoran hissini
tüm ekranı beje boyayarak verme" isteğini mimari düzeyde karşılıyor: renk artık kanban kolonunun koca zeminine
değil yalnızca durum rozetine/ikon chip'ine taşınıyor. `AppShell.tsx`'teki `theme="warm"` prop'u ve
`AppShell.module.css`'teki `.warm` + dark-warm override blokları kaldırılacak; Kasa sayfası düz `<AppShell>`
kullanacak. Warm-only `Bricolage Grotesque` display fontu kaldırılıyor - tek display fontu (Plus Jakarta
Sans) tüm app'te tutarlı kalıyor (font ailesini değiştirmek istekte açıkça talep edilmedi, mevcut ikili zaten
"jenerik AI görünümü" kategorilerinden hiçbirine girmiyor - risk/kapsamı büyütmemek için korunuyor).

**Token sistemi (yeni paylaşılan kimlik, `app/globals.css` - hem Kasa hem diğer tüm ekranlar):**
- Nötr: `--color-bg #ffffff`, `--color-surface #f4f5f7`, `--color-surface-raised #ffffff`,
  `--color-fg #1c1f26` (koyu charcoal), `--color-fg-muted #667085`, border `rgba(28,31,38,.09/.18)`.
- Primary: `#5b3df0` / strong `#4527c9` - canlı indigo-mor, tek güçlü modern accent; hiçbir durum renginin
  hue'suna değmiyor ve Tailwind'in birebir indigo/violet default'u değil (jenerik-SaaS hissinden kaçınmak
  için özel ton).
- Durum renkleri (dördü de hem beyaz kart hem kendi %12 tint zemini üzerinde WCAG AA 4.5:1'i geçecek şekilde
  hesaplanarak seçildi - bkz. aşağıdaki kontrast tablosu): `--color-warning`(bekliyor/amber) `#a34b08`,
  **yeni** `--color-info`(hazırlanıyor/blue-indigo) `#1d4ed8`, `--color-success`(hazır/green) `#146c34`,
  `--color-danger`(kritik/red) `#b91c1c`. `--color-info`/`--color-info-bg` yeni token - önceki tasarımda
  "hazırlanıyor" durumu `--color-warning`'i ödünç alıyordu, dört durumun (bekliyor/hazırlanıyor/hazır/kritik)
  görsel olarak net ayrışması için ayrı bir "info" ekleniyor.
  - Kontrast (WCAG AA, beyaz zemin / kendi %12 tint'i): fg 16.5:1, fg-muted 4.97:1, primary 6.23:1 (buton
    metni beyaz-üzerinde-primary de 6.23:1), warning 5.89:1 / 4.93:1, info 6.70:1 / 5.57:1, success 6.51:1 /
    5.44:1, danger 6.47:1 / 5.29:1.
- Radius sıkılaştırıldı (`--radius-lg` 20px→14px, `--radius-md` 12px→10px) - warm'ın yumuşak/organik hissi
  yerine daha kesin/modern köşeler; `--radius-full` (999px) pill/badge için korunuyor.
- Gölgeler biraz daha düz/sade (daha düşük blur/opacity) - flat modern POS hissi.
- Dark mode: aynı yapı, nötr koyu lacivert-gri zemin + parlak indigo/durum tonları (önceki Tide dark
  mantığıyla aynı desen - `data-theme="dark"` explicit attribute, `prefers-color-scheme` yok - ayrı warm
  dark bloğu kaldırılıyor).

**Layout kararları (Kasa sayfası):**
- KPI şeridi: ayrı ayrı border/shadow'lu üç kart yerine TEK bir yüzey içinde bölünmüş kompakt segmentler -
  daha az boşluk, tek bakışta 3 metrik.
- Kanban kolonu: zemin `--color-surface` (nötr açık gri, "büyük bej kutu" değil); renk yalnızca kolon
  başlığındaki ikon chip + sayaç rozetinde yaşıyor.
- Sipariş kartı: fiş/perforasyon dekorasyonu (dashed çizgi) kaldırıldı - yeni yönle uyumsuz fazla dekoratif
  bir warm-kimlik detayıydı. Yerine: net tek satırlık başlık (masa + sipariş no + tutar aynı satırda),
  urgency hue'suna göre sabit kalınlıkta renkli sol kenar, tek satır durum rozeti + bekleme süresi, geniş
  tek-tıkla ana aksiyon butonu - "saniyeler içinde okunabilir" hedefine yönelik.
- Sidebar: marka bloğu güçlendirildi, grup başlıkları daha belirgin (letter-spacing + weight artırıldı),
  aktif link dolgun tint arka plan + accent metin + sol accent bar ile daha güçlü hiyerarşi.

Adım adım commit edilecek: (1) design token overhaul (`app/globals.css`), (2) AppShell warm kimliğinin
kaldırılması + shell restyling (sidebar/topbar), (3) Kasa sayfası (KPI şeridi + kanban + kart) yeniden yazımı,
(4) font/dark-mode temizliği, (5) gerçek Chrome'da light mode doğrulaması.

**Uygulama:** Tasarım kararı aynen uygulandı, 5 adımın tamamı ayrı commit'lerle tamamlandı. Business
logic/API çağrılarına dokunulmadı - yalnızca `app/globals.css`, `components/layout/AppShell.{tsx,module.css}`,
`app/cashier/[branchId]/page.module.css`, `app/layout.tsx`, `components/ui/Badge.{tsx,module.css}` (yeni
"info" tone eklendi). Her component zaten yalnızca `var(--color-*)` token'larını okuduğu için (repo genelinde
hardcoded hex renk taraması sıfır sonuç verdi), token overhaul tek başına tüm staff-web'e (Kasa dahil) yeni
kimliği taşıdı - Kasa'nın ayrı `.warm` override bloğunu kaldırmak yeterli oldu, başka hiçbir dosyaya
dokunmaya gerek kalmadı.

**Doğrulama:** `npx tsc --noEmit`, `npx eslint . --max-warnings 0`, `npm run build` (staff-web) her adımda
temiz. Canlı Chrome testi yapıldı - `infra-staff-web-1` container'ı geçici durdurulup yerine `npx next dev -p
3002` başlatıldı, standing hesapla (`admin@qrmenu.local`, bkz. yerel hafıza) giriş yapıldı. Kasa'nın dört
durumunu da (onay bekleyen normal, onay bekleyen kritik, hazırlanıyor uyarı eşiğinde, hazır) gerçek verilerle
görmek için "Merkez Şube"ye 4 geçici sipariş seed edildi. Doğrulanan: Dashboard + Menü + Kasa'da yeni beyaz/
charcoal/indigo kimlik, sidebar'ın açık gri zon + koyu kesin aktif-link imzası, KPI şeridinin tek yüzeyde
bölünmüş segmentleri, kanban kolonlarının artık "büyük renkli kutu" değil nötr zemin + renkli başlık chip'i
olması (bekliyor=amber, hazırlanıyor=blue/indigo, hazır=green), kritik siparişin kırmızı nabız+kenar+rozetiyle
öne çıkması, sipariş kartının tek satırlık başlığı (masa+no+tutar) ve tam genişlik tek-tıkla aksiyon butonu.
Fonksiyonel doğrulama: "Kabul Et" butonuna gerçekten tıklanıp siparişin canlı API çağrısıyla (SSE refetch
dahil) "Onay Bekleyen"den "Hazırlanıyor"a taşındığı doğrulandı. Dark mode toggle'ı da test edildi, yeni
paletle birlikte sorunsuz çalıştığı görüldü, sonra Light'a geri alındı. Test sonunda seed edilen 4 sipariş/
order-item/table-visit/anonymous-session DB'den silindi (her DELETE ayrı komut olarak), yerel `next dev`
kapatıldı, `infra-staff-web-1` container'ı yeniden başlatılıp `healthy` durumuna döndüğü doğrulandı (container
imajı henüz yeniden build edilmedi - hâlâ eski koddan çalışıyor, bir sonraki deploy/rebuild'de yeni tasarımı
alacak).

**Bilinen not:** Doğrulama sırasında "Merkez Şube"de, bu görevle ilgisiz iki eski test siparişi fark edildi -
`order_number=1` (durum `IN_KITCHEN`, `last_activity_at` ~08-13 06:46, Kasa'da "16 sa+ bekliyor" olarak
görünüyordu) ve `order_number=2` (durum `REJECTED_BY_STORE`, 08-13 07:53). Bunlar bu oturumda oluşturulmadığı
için silinmedi - önceki bir test/doğrulama oturumundan kalmış olabilirler (muhtemelen tam temizlenmemiş).
İsterseniz ayrı bir adımda temizlenebilir.

**Düzeltme - stale container image (kullanıcı geri bildirimi):** Kullanıcı, yukarıdaki doğrulamadan sonra
`infra-staff-web-1`'in gerçek tarayıcı görünümünün hâlâ eski warm/bej kimlikte olduğunu bildirdi. Kök neden
gerçek Chrome'da computed style incelemesiyle kesin olarak teşhis edildi: `.shell` elemanının `className`'i
hâlâ `AppShell-module__R3Ra8G__warm` içeriyordu ve `:root`'un `--color-primary`'si `#0e7c86` (eski Tide),
`.shell` üzerindeki override ise `#963b1a` (eski warm/kiremit) olarak ölçüldü - hem `.warm` class'ı hem eski
hex değerleri güncel kaynak kodda **hiç yok**, yani sorun kod/specificity değil, doğrudan yukarıdaki
doğrulama notunda zaten işaretlenen "container imajı henüz rebuild edilmedi" durumuydu - `docker compose up
-d --force-recreate` olmadan yapılan restart, eski image'ı aynen çalıştırmaya devam etmiş. Çözüm: `docker
compose build staff-web` + `docker compose up -d staff-web` ile image gerçekten yeniden build edilip
container yeniden oluşturuldu (kod değişikliği yok, yalnızca deploy). Yeniden doğrulama: hard-navigate sonrası
computed style'lar `--color-primary: #5b3df0`, `--color-info: #1d4ed8`, `.shell` class listesinde `.warm`
YOK; ekran görüntüsü beyaz zemin + charcoal metin + indigo primary + nötr kanban kolonları + doğru durum
renklerini gösteriyor. Kod tarafında hiçbir değişiklik yapılmadı - yalnızca deploy edilen image güncellendi.

Backend'e dokunulmadı.

---

## Kasa Ekranı — Görsel Referansa Uyarlama (2026-08-14) — ✅ COMPLETED (canlı doğrulama/commit bekliyor)

Kullanıcının sağladığı yeni mockup'a (`docs/design/QR-Code-Kasa Ekranı Tasarımı.png`) göre `/cashier/[branchId]`
sayfası yeniden düzenlendi - önceki "sıcak tema" kimlik yenilemesinden (yukarıdaki bölüm) farklı, aynı
indigo/charcoal design token'ları üzerinde salt **layout/bilgi mimarisi** uyarlaması.

**Değişenler:**
- Yeni bir üst toolbar eklendi: sipariş/masa arama input'u (`Search`), bugünün tarihi chip'i (`CalendarDays`),
  manuel "Yenile" butonu (`RefreshCw`). Arama, üç kolonun (`Onay Bekleyen`/`Hazırlanıyor`/`Hazır`) her birini
  masa etiketi + sipariş no üzerinden client-side filtreliyor; sonuç yoksa kolonun `EmptyState`'i arama-özel
  mesaja dönüyor.
- KPI şeridi `kpiStrip` yerine `kpiGrid`; kartların sırası ve etiketleri mockup'a göre değişti (Günlük Ciro →
  Toplam Sipariş → Net Satış; ikonlar buna göre yeniden atandı).
- Sipariş kartları yeniden tasarlandı: eski `cardHeader`/`Badge` tabanlı meta satırı kaldırıldı, yerine
  `cardTop` (masa chip ikonu + masa adı/sipariş no + geçen süre/saat), madde işaretli `items` listesi ve
  `cardFooter` (toplam tutar + tek aksiyon butonu) geldi. "Hazırlanıyor" kartındaki aksiyon metni "Hazır" →
  "Hazırlığı Tamamla" olarak güncellendi.
- Mockup'ın backend karşılığı olmayan alanları (kişi sayısı, ortalama hazırlık süresi, dünkü güne göre %,
  bildirim rozeti sayısı) **eklenmedi** - yalnızca gerçek API verisiyle doldurulabilen alanlar taşındı (kod
  içi yorum olarak da işaretlendi).

**AppShell/sidebar navigasyon güncellemesi (aynı adımın parçası):** Her `NavItem`'a `lucide-react` ikonu eklendi
(`staffNav.ts`); topbar'daki kullanıcı e-postası/rolü + "Çıkış Yap" butonu kaldırılıp sidebar'ın altına, avatar
baş harfleri (`initialsFromEmail`) + e-posta/rol + logout ikon-butonundan oluşan bir kullanıcı kartına taşındı.
Ayrıca üç nav etiketi `product-requirements.md` Bölüm 20'deki (bkz. aşağıdaki "Ürün Kararı" bölümü) yeni
branch-scoped bilgi mimarisiyle uyumlu olacak şekilde kısaltıldı: "Dashboard" → "Özet", "Şubeler / Masalar / QR"
→ "Masalar", "Satış Raporları" → "Raporlar". Bu yalnızca isimlendirme/etiket düzeyinde bir hizalama - aşağıdaki
Gap-Analysis #16'nın gerektirdiği branch-selector kaldırma/backend context zorunluluğu bu adımda **uygulanmadı**.

**Bilinen durum:** Bu adım kod tarafında tamamlandı ama bu konuşma öncesinde ne canlı Chrome doğrulaması ne de
commit yapılmıştı (oturum `/clear` ile kesilmiş, adım loglanmadan kalmıştı) - bu not o boşluğu kapatıyor.
Backend'e dokunulmadı; yalnızca `staff-web` (cashier sayfası + AppShell + staffNav) değişti.

---

## 2026-08-14 Gap #16 — Sıkı Şube İzolasyonu + Masalar/QR UX — ✅ COMPLETED

`BUSINESS_ADMIN`, `BRANCH_MANAGER` ve `CASHIER` için aktif şube artık staff session/context'ten çözülüyor; schema tek atamayı enforce ediyor ve eski branch-parametreli backend alias'ları başka şubeyi reddediyor. Staff-web'den şube seçimi kaldırıldı; kasa, rapor, gider, personel ve menü doğrudan aktif şubeyi kullanıyor. Duyuru ve audit ekranları korunarak active-branch scope'una alındı; chain karşılaştırma/rapor kodu korunup mevcut user-facing rollerden ve nav'dan kapatıldı. QR üretme/yenileme/revoke/PNG indirme/yazdırma işlemleri `/tables` altındaki masa kartlarına taşındı. Kasa route'u branch parametresiz çalışıyor ve referans tasarım düzenini koruyor.

Doğrulama: backend `./mvnw test` (**130 test, 0 failure**), staff-web `npm run lint` ve `npm run build` temiz. Aynı işletmedeki başka şubenin sipariş, rapor, masa ve QR kaynaklarına erişim için negatif integration testleri eklendi.

---

## Finansal Veri Akışı Uçtan Uca Audit — Sipariş → Ödeme → Ciro → İade → Gider → Rapor → Özet/Kasa KPI — ✅ COMPLETED

Kullanıcı talebiyle finansal veri akışı uçtan uca denetlendi: backend servis/query katmanı (`OrderingService`,
`PaymentService`, `RefundService`, `ExpenseService`, `RecurringExpenseScheduler`/`Generator`/`DuePolicy`,
`ReportingService`, `DailyCloseService`, `StaffReportingController`), DB şeması, API response'ları ve
frontend mapping'i (Özet/Kasa/Raporlar/Giderler) birlikte okundu; backend `./mvnw test` (**tüm modüller,
0 failure**) ile doğrulandı.

**Backend tarafı: hata bulunmadı.** Brüt satış → net satış → refund toplamı → gider (manuel+tekrarlayan) →
Yönetimsel Net Sonuç zinciri (`ReportingService.buildReport` + `ExpenseService.expenseBreakdown` +
`StaffReportingController.operatingResult`) doğru kuruluydu: ödenmiş ama henüz kabul edilmemiş/reddedilmiş
siparişler de brüt satışa dahil (red bir tam refund'la nötrleniyor), refund yalnızca `COMPLETED` statüsünde
toplanıyor, tarih aralıkları branch'in kendi `ZoneId`'siyle günün başlangıcına çevriliyor, silinmiş/pasif
recurring template'lerin geçmişte üretilmiş `Expense` satırları sorgudan hiç etkilenmiyor (template join'i yok),
scheduler idempotent (period+template unique check). Özet/Kasa/Raporlar üçü de aynı `ReportingService.
getBranchReport`'u (`DailyCloseService` de dahil) çağırıyor, ayrı bir hesaplama yolu yok.

**Bulunan hata (frontend, gerçek): "bugün" hesaplaması UTC'ye kayıyor.** 7 dosyada `new Date().
toISOString().slice(0, 10)` (veya yerel y/a/g'den kurulan bir `Date`'i aynı şekilde `toISOString()`'a
vermek) kalıp olarak "bugünün tarihi"ni üretiyordu - ama `toISOString()` her zaman UTC döndürür. Pozitif
UTC ofsetli bir branch timezone'unda (örn. Europe/Istanbul, UTC+3) bu iki farklı şekilde bozuluyordu: (1)
yerel gece yarısı-03:00 arası her gün "bugün" bir gün geriye kayıyordu, (2) ayın 1'i gibi yerel y/a/g'den
inşa edilen herhangi bir tarih için ofset her saatte koşulsuz bozuluyordu (örn. "bu ay" filtresinin
varsayılan başlangıcı her zaman bir önceki ayın son gününü gösteriyordu). Etkilenen yerler: Raporlar'ın
"Bugün/Dün/Bu Hafta/Bu Ay" preset'leri (`DateRangePresets.tsx` - sayfa ilk açıldığında varsayılan aralık),
Özet'in "Bugünün Özeti" KPI'ları (`dashboard/page.tsx`), Raporlar'ın gün sonu kapatma akışı
(`reports/[branchId]/page.tsx` - `handleCloseToday`/`todayAlreadyFinal`, yanlış iş gününü kapatma riski),
manuel gider formunun varsayılan tarihi (`ExpenseForm.tsx`), gider listesinin varsayılan "bugün"/"bu ay"
filtreleri (`ExpenseList.tsx`), ve tekrarlayan gider şablonunun varsayılan başlangıç tarihi + "sıradaki
vade" önizlemesi (`RecurringTemplates.tsx`). Kasa ekranı (`cashier/[branchId]/page.tsx`) zaten doğru
yerel-tarih fonksiyonunu kullanıyordu - referans implementasyon oradan alındı.

**Düzeltme:** `lib/time.ts`'e tek bir paylaşılan `localIsoDate(date = new Date())` eklendi (yerel
`getFullYear`/`getMonth`/`getDate` bileşenlerinden string kuruyor, hiç `toISOString()` kullanmıyor); yukarıdaki
7 dosyadaki hatalı yerel fonksiyon/çağrı bu ortak fonksiyona yönlendirildi, Kasa'daki doğru-ama-tekrarlanan
yerel fonksiyon da aynı ortak fonksiyona taşındı (tek kaynak, aynı hatanın başka bir yerde tekrar
girmesini önlüyor). `RecurringTemplates.tsx`'teki `toIsoDate(year, monthIndex, dayOfMonth)` (y/a/g'den
`Date.UTC` ile inşa edip `toISOString()`'a veren takvim tarihi kurucusu) bilinçli olarak dokunulmadı - o zaten
UTC-tutarlı, "şimdiki an"a değil sabit y/a/g'ye dayanıyor, dolayısıyla doğru.

**Doğrulama:** `TZ=Europe/Istanbul node -e ...` ile hem "yerel 01:00" hem "ayın 1'i yerel gece yarısı"
senaryosu izole reprodüksiyonla doğrulandı (düzeltme öncesi bir gün geri kayıyor, düzeltme sonrası doğru).
`npx tsc --noEmit`, `npx eslint` (değişen dosyalar, `--max-warnings 0`) ve `npm run build` (staff-web) temiz.
Backend `./mvnw test` tüm modüllerde 0 failure (bu hata frontend-only olduğu için backend testleri zaten
etkilenmiyordu, ama regresyon olmadığını doğrulamak için tekrar koşuldu). staff-web'de hiç test runner'ı
kurulu değil (`package.json`'da `test` script'i yok, jest/vitest yok) - bu yüzden repo'ya yeni bir test
altyapısı eklemek yerine (kapsam dışı bir altyapı kararı olurdu) düzeltme izole node reprodüksiyonuyla
kanıtlandı; kullanıcı isterse ayrı bir adımda staff-web'e bir test runner kurulması teklif edilebilir.

---

## Sipariş Görünürlüğü ve Customer Order-Status Akışı Uçtan Uca Audit — ✅ COMPLETED (commit/push bekliyor)

Kullanıcı talebiyle sipariş görünürlüğü (staff tarafı) ve customer order-status akışı uçtan uca denetlendi:
backend order/payment/refund domain'i (`CustomerOrder`, `OrderNumberGenerator`, `OrderingService`,
`OrderControlController`, `RefundController`, `OrderTrackingController`, SSE notifier) ile customer-web'in
sipariş oluşturma/tracking ekranları ve staff-web'in Kasa/İadeler ekranları birlikte okundu.

**Bulunan hata (backend, gerçek): sipariş numarası araması günler arası çakışınca 500 atıyordu.**
`OrderNumberGenerator`, okunabilir sipariş numarasını şube+gün bazlı bir sayaçla üretiyor
(`branch_daily_order_sequence`, her gün 1'den başlıyor) - yani `orderNumber` yalnızca aynı şube+aynı gün
içinde benzersiz, zaman içinde değil. Ama `OrderRepository.findByBranchIdAndOrderNumber` tek sonuç bekleyen
bir derived query'ydi (`Optional<CustomerOrder>`). Bir şube bir günden fazla açık kaldığı anda (ör. her gün en
az 1 sipariş alan bir şubede "sipariş #1" her gün tekrar üretilir), aynı numarayı taşıyan ikinci sipariş
oluşur oluşmaz `GET /api/staff/orders/search?orderNumber=` (İadeler ekranının tek arama yolu) İadeler ve yeni
Siparişler ekranındaki arama için `IncorrectResultSizeDataAccessException` ile 500 dönmeye başlıyordu -
sessiz bir veri bütünlüğü sorunu değil, doğrudan üretimde bir haftadan uzun çalışan her şubede tetiklenecek
aktif bir çökme.

**Düzeltme:** `findByBranchIdAndOrderNumber` → `findAllByBranchIdAndOrderNumberOrderByCreatedAtDesc` (liste
döner), `OrderingService.getOrderByNumber` ilk (en yeni) eşleşmeyi alacak şekilde güncellendi - staff bir
numarayla ararken pratikte neredeyse her zaman en güncel siparişi arıyor, o yüzden çakışma artık hata değil
"en yeniye çöz" davranışına dönüşüyor. Regresyon testi (`OrderHistoryIntegrationTest.
searchByOrderNumberResolvesToTheMostRecentOrderWhenTheDailyCounterHasRecycled`) gerçek bir çakışmayı
`branch_daily_order_sequence`'ın günlük resetini simüle ederek (aynı sipariş `created_at - 1 gün` ile
klonlanıp) reprodüksiyon eder.

**Bulunan gap (staff): tamamlanan/reddedilen siparişler Kasa'dan düşünce hiçbir yerde görünmüyordu.** Kasa
yalnızca `AWAITING_STORE_ACCEPTANCE`/`IN_KITCHEN`/`READY` sorguluyor (ürün kararı: tek operasyon ekranı); bir
sipariş `COMPLETED`/`REJECTED_BY_STORE` olunca backend'de bu statüleri listeleyen hiçbir endpoint yoktu - tek
erişim yolu sipariş numarasını ezbere bilip İadeler'den aratmaktı. **Düzeltme:** `OrderRepository`'ye
`findAllByBranchIdAndStatusInAndCreatedAtBetweenOrderByLastActivityAtDesc`,
`OrderingService.getOrderHistory` (branch-local `LocalDate` aralığı, `ReportingService.buildReport` ile aynı
zone-handling deseni) ve `OrderControlController`'a yeni `GET /api/staff/orders/history` endpoint'i eklendi
(`OrderHistoryResponse` - orderNumber, tableLabel, rejectionReasonCode/Note, en son refund statüsü, item
listesi). staff-web'e yeni bir **Siparişler** ekranı eklendi (`app/orders/page.tsx`, nav: Operasyon grubu,
Kasa'nın yanına) - Aktif/Tamamlanan/Reddedilen/İade sekmeleri (Aktif mevcut 3 endpoint'i salt-okunur birleştirir,
diğer üçü yeni `/history`'yi `DateRangePresets` ile besler; İade sekmesi `latestRefundStatus != null` olan
kayıtları filtreler), sipariş no ile arama (mevcut `/search`'ü İadeler ile paylaşır) ve tıklanan satırın
detayını (kalemler, red nedeni, iade durumu) gösteren bir `Dialog`. Kasa'ya dokunulmadı - hâlâ yalnızca aktif
akışı yönetiyor.

**Customer tarafı: büyük ölçüde zaten doğruydu, bir kopya güncellemesi yapıldı.** `OrderTrackingController`
zaten GET-by-token + SSE ikilisini destekliyordu (SSE yalnızca "refetch sinyali", sayfa her mount/reconnect'te
REST'ten güncel durumu çekiyor - transient event'e güvenmiyor); `latestRefundStatus` zaten aynı response'ta
geliyordu (REQUESTED/PROCESSING/COMPLETED/FAILED → "İadeniz işleniyor" vb. Türkçe mesajlar); tamamlanan/
reddedilen sipariş tracking token'ıyla sonradan tekrar açılabiliyordu (terminal state'te redirect/clear yok).
Tek değişiklik: `OrderStatusTimeline.tsx`'teki red mesajı kullanıcının talep ettiği tam ifadeyle
("İşletme siparişi reddetti" → **"Siparişiniz işletme tarafından reddedildi"**) eşleşecek şekilde güncellendi.

**Doğrulama:** Backend `./mvnw test` (tüm modüller, **0 failure**, yeni `OrderHistoryIntegrationTest` dahil -
çakışma regresyonu + `/history` endpoint'i status filtresi/tarih aralığı/refund durumu için). staff-web ve
customer-web'de `tsc --noEmit`, `eslint . --max-warnings 0`, `npm run build` üçü de temiz (staff-web'in yeni
Siparişler sayfası ilk yazımda `react-hooks/set-state-in-effect` hatası verdi - async/await tabanlı veri
yükleme fonksiyonu doğrudan effect'ten çağrılıyordu; `app/tables/page.tsx`'teki mevcut desene uyacak şekilde
`.then()` zincirine çevrilip mount-only effect + kullanıcı-tetikli handler'lara (tab/tarih değişimi) ayrıldı).

Gerçek senaryo, Docker'da yeniden build edilen `backend`/`staff-web`/`customer-web` image'larına karşı canlı
API çağrılarıyla uçtan uca koşuldu (Chrome uzantısı bu oturumda bağlantısını kaybettiği için tarayıcı yerine
doğrudan HTTP ile; geçici bir `BUSINESS_ADMIN` smoke-test hesabı kullanıldı, standing hesabın (bkz. yerel
hafıza) şifresi hâlâ 401 veriyor): (1) **ödeme → red**: sipariş #3 ödendi, kasa reddetti → customer tracking
anında `REJECTED_BY_STORE` + `latestRefundStatus=COMPLETED` gösterdi, İadeler araması ve yeni Siparişler'in
"Reddedilen" sekmesi (`/history?status=REJECTED_BY_STORE`) siparişi refund detayıyla birlikte buldu; (2)
**ödeme → kabul → hazırlanıyor → hazır → tamamlandı**: sipariş #4 her adımda customer tracking'de doğru
durumu gösterdi (`IN_KITCHEN`→"Hazırlanıyor", `READY`, `COMPLETED`), tamamlanınca Kasa'nın aktif listelerinden
düştü ve yeni "Tamamlanan" sekmesinde (`/history?status=COMPLETED`) tüm detaylarıyla göründü. Test sonunda
oluşturulan 2 sipariş/payment/table-visit/anonymous-session ve geçici staff hesabı DB'den temizlendi
(audit_log_entry.actor_staff_user_id nulled, staff_session/staff_user_branch/staff_user silindi - [[reference_standing_staff_account]] ile
aynı disiplin).

Branch isolation, payment/refund akışı ve order state machine'e (ACCEPT/REJECT order-level kaldı, item-level
red eklenmedi) dokunulmadı.

---

## 2026-08-21 Müşteri Menü Görsel Yönü — Referans Görsele Yeniden Yaklaştırma — ✅ COMPLETED

**Bağlam:** Önceki (loglanmamış, `/clear` ile kesilen) bir oturumda `app/t/[token]/*`'a Favoriler/Arama/
"En Çok Tercih Edilenler" özellikleri eklenmiş ama görsel yön referans mockup'tan (`docs/design/
customer-menu-reference.png` - "Lalezar Coffee", açık/sıcak kahve dükkânı estetiği) uzaklaşmıştı: kullanıcı
"dark/kahverengi tema"yı reddetti, referansı tek doğru kaynak olarak işaretledi.

**Kök neden - tema:** `page.module.css`'teki `.page` sınıfı zaten referansın renklerini taşıyordu (`#fff9f5`
zemin, `#e85d24` accent) **ama** bir `@media (prefers-color-scheme: dark)` bloğu bunu sistem karanlık
modunda koyu kahverengiye (`#201812`) çeviriyordu - müşteri kendi telefonunun karanlık modunda QR okuttuğunda
gördüğü şey buydu. Referans tek bir sıcak/açık kimlik öneriyor, adaptif bir koyu varyant değil - blok
tamamen kaldırıldı (`color-scheme: light` sabitlendi). Diğer ekranların (tracking sayfası, staff-web) karanlık
modu dokunulmadı.

**Ürün görselleri neden yüklenmiyordu:** Kod tarafında hata yok - `ProductCard`'ın `<img>`/`onError` fallback'i
doğru çalışıyor (`PublicMenuController` de `imageUrl`'i doğru map'liyor). Gerçek sebep: DB'deki mevcut demo
ürünlerin (`Meydan Bistro` iş yeri) `image_url` alanı tamamen boştu - hiç görsel yüklenmemişti, bu bir görüntüleme
hatası değil bir veri boşluğuydu.

**Yeniden tasarlanan bileşenler (`frontend/customer-web/app/t/[token]/`):**
- `VisitHeader` - referanstaki güçlü hero yeniden kuruldu: sıcak degrade + doku zemin (gerçek işletme/şube
  görsel yükleme altyapısı backend'de yok - sahte bir fotoğraf yerine kasıtlı olarak dokulu degrade, referansın
  kompozisyon ağırlığını taşıyor), ortalanmış işletme adı/şube/masa + yeşil noktalı "Oturum aktif" pill.
  Referanstaki hamburger ikonunun yerine **işlevsiz bir dekor değil**, gerçek ve mevcut bir aksiyon kondu: sol
  üstte ziyaretçi sayısı ikon-butonu (`onEditGuestCount`, rozet olarak mevcut sayıyı gösteriyor); sağ üstte
  "Siparişlerim" (değişmedi, her zaman görünür).
- `page.tsx` - sıra referansa uyacak şekilde değişti: hero artık sticky değil (yukarı kaydırılınca sayfayla
  birlikte kayboluyor), Arama + kategori pill'leri birlikte sticky.
- `SearchBar` - beyaz, gölgeli, hero'nun altına hafifçe taşan (negative margin) yuvarlak pill.
- `CategoryNav` - aktif chip artık accent turuncu değil referanstaki gibi koyu/ink dolgu + beyaz metin.
- `ProductCard` - üç varyant birbirinden ayrıştırıldı: `grid` (kategori listesi - kare foto, fiyat+"+ Ekle"
  aynı satırda alt kısımda), `featured` (En Çok Tercih Edilenler - 4:3 foto, ★ rozet + kalp, "+ Ekle" yok,
  yalnızca fiyat), `compact` (Favoriler - referanstaki gibi tamamen yatay mini kart: küçük kare thumbnail +
  isim/fiyat + kalp aynı satırda, eskiden `featured` ile aynı dikey markup'ı paylaşıyordu).

**Kapsam dışı bırakılanlar (bilinçli):** Arama input'unun sağındaki filtre/slider ikonu (referansta var, ama
karşılık gelen bir filtre özelliği yok - işlevsiz ikon eklenmedi). Kategori pill'lerindeki ikonlar (referansta
kahve/tatlı ikonları var ama bunlar business-specific; generic bir kategori-adı→ikon eşlemesi kırılgan bir
heuristic olurdu, eklenmedi).

**Görsel doğrulama (canlı Chrome, 400×850 mobil viewport):** `/internal/**` bootstrap API'siyle (aynı desen:
[[reference_standing_staff_account]]) geçici bir "Lalezar Coffee (design-verify)" işletmesi + şube + referanstaki
kategoriler (Sıcak/Soğuk Kahveler, Tatlılar, Sandviçler) + gerçek Unsplash CDN görselli 8 ürün oluşturuldu -
mevcut `Meydan Bistro` verisine dokunulmadı. Hero, arama, kategori pill'leri, kare fotoğraflı grid kartları,
Favoriler şeridi (bir ürün favorilendi) ve sepet/ödeme akışı (gerçek mock ödeme + staff accept) üzerinden "En
Çok Tercih Edilenler" kartı canlı olarak referansla karşılaştırıldı - hepsi eşleşti. Doğrulama sonunda oluşturulan
işletme/şube/masa/QR/kategori/ürün/sipariş/ödeme/table-visit/geçici staff hesabı tek transaction'da DB'den
temizlendi (FK sırasına uyularak: audit_log_entry nullanıp silindi, payment_webhook_event/payment/order_item/
customer_order, table_visit/anonymous_customer_session, branch_product/product_option(_group)/product/
menu_category, table_qr_token/restaurant_table, staff_session/staff_user_branch/staff_user, branch_business_hours/
branch_daily_order_sequence, branch, business - [[reference_standing_staff_account]] ile aynı disiplin).

**Doğrulama:** `tsc --noEmit`, `eslint app/t/[token]/ --max-warnings 0`, `npm run build` üçü de temiz.
Business logic/checkout/tracking akışına dokunulmadı; "En Çok Tercih Edilenler" hâlâ yalnızca gerçek 30 günlük
satış verisiyle doluyor (heuristic/fake fallback eklenmedi - o bölüm gerçek satış geçmişi olmayan bir şubede
hâlâ görünmez kalıyor, bu beklenen davranış). Commit/push yapılmadı (kullanıcı talebi).

**Takip düzeltmesi (aynı gün): başlıklar sistem karanlık modunda beyaz görünüyordu.** Kullanıcı canlı ortamda
(kendi telefonu/tarayıcısı sistem karanlık modundaydı) kategori/bölüm başlıklarının (`h2` - "En Çok Tercih
Edilenler", "Başlangıçlar" vb.) neredeyse görünmez, açık/beyaza yakın renkte olduğunu bildirdi. Kök neden CSS
custom property miras zinciriyle ilgili bir kesişim hatasıydı: `globals.css`'teki `body { color: var(--color-fg);
}` kuralı `color`'ı **body seviyesinde**, o anki (kök/sistem) `--color-fg` değeriyle çözüp o hesaplanmış rengi
alt elementlere miras bırakıyor - `.page`'in kendi `--color-fg`'yi override etmesi bu zaten çözülmüş `color`
mirasını geri almıyor, çünkü `.page`'in kendisi hiç `color` bildirmiyordu (yalnızca `background`). Sonuç: kendi
`color`'ını set etmeyen her element (tüm `h2` başlıklar dahil) body'den miras kalan sistem-teması rengini
kullanmaya devam ediyordu - açık modda tesadüfen doğru görünüyordu (iki değer birbirine yakın), karanlık modda
görünmez oluyordu. **Düzeltme:** `page.module.css`'teki `.page` kuralına `color: var(--color-fg);` eklendi -
artık `color` da `.page` seviyesinde .page'in kendi (sıcak/açık) `--color-fg`'siyle yeniden çözülüyor ve doğru
şekilde aşağı miras kalıyor. Canlı Chrome'da (`getComputedStyle`, sistem karanlık modu açıkken) doğrulandı:
düzeltme öncesi `rgb(237, 243, 241)` (neredeyse beyaz), sonrası `rgb(43, 33, 28)` (doğru koyu mürekkep).
`tsc`/`eslint`/`npm run build` temiz. Commit/push yapılmadı (kullanıcı talebi).

---

## 2026-08-21 Müşteri Menü - Sipariş Takip Teması, Ürün Görselleri, Kahve Dükkânı Kataloğu, Kart/Kontrol İyileştirmeleri — ✅ COMPLETED

Kullanıcının aynı gün ilettiği 9 maddelik eksik listesi çözüldü (customer-web, `frontend/customer-web`).

**Kök neden - sipariş takip/makbuz ekranı hâlâ koyu temaydı, masaüstünde yan boşluklar siyahtı:** Önceki
oturumda yalnızca `app/t/[token]/page.module.css`'teki `.page` sınıfına sıcak/açık palet lokal olarak
override edilmişti; `app/order/track/[token]/*` ve `globals.css`'teki `body` hâlâ `@media
(prefers-color-scheme: dark)` bloğuyla sistem karanlık moduna uyarlanan orijinal "Tide" (teal) paletini
kullanıyordu. Masaüstünde `.page`'in `max-width` sınırının dışında kalan yan boşluklar da body'nin (karanlık
modda neredeyse siyah) zeminini gösteriyordu. **Düzeltme (kapsamlı, tek seferlik):** sıcak/açık palet
(`#fff9f5` zemin, `#e85d24` accent, vb.) `globals.css`'teki `:root`'un kendisine taşındı, `@media
(prefers-color-scheme: dark)` bloğu tamamen kaldırıldı (`color-scheme: light` sabitlendi) - bu app (customer-web)
yalnızca QR menü + sipariş takip ekranlarından oluşuyor, tek bir sıcak/açık kimlik dışında bir varyanta
gerek yok. `app/t/[token]/page.module.css`'teki artık gereksiz kalan lokal değişken override'ı silinip yalnızca
yapısal kurallar bırakıldı. Sonuç: order/track + receipt ekranları otomatik olarak doğru temaya geçti, masaüstü
yan boşlukları da body ile aynı sıcak renge döndü - iki ayrı şikayet tek kök nedene bağlıydı.

**Ürün görselleri neden yüklenmiyordu (yeniden doğrulandı):** Önceki oturumda zaten teşhis edilen aynı sebep -
`Meydan Bistro`'nun 24 demo ürününün `image_url` alanı tamamen boştu, kod tarafında hata yoktu. Bu kez kalıcı
çözüm için 24 ürünün tamamına gerçek, içerikle eşleşen Unsplash CDN görseli eklendi (aşağıdaki kategori
dönüşümüyle birlikte, tek SQL script).

**Test/catalog kategorileri kahve dükkânına çevrildi:** `Meydan Bistro`'nun jenerik restoran kataloğu (6
kategori: Başlangıçlar/Ana Yemekler/Pizzalar/Salatalar/Tatlılar/İçecekler, 24 ürün - steak/pizza/salata vb.)
tamamen bir kahve dükkânı kataloğuna dönüştürüldü: **Sıcak Kahveler** (Espresso/Latte/Cappuccino/Amerikano/
Türk Kahvesi/Filtre Kahve), **Soğuk Kahveler** (Buzlu Latte/Cold Brew), **Kahvaltılıklar** (Ekmek Sepeti/
Kruvasan/Tarçınlı Rulo/Çikolatalı Kurabiye), **Sandviçler & Atıştırmalıklar** (Izgara Tost/Karidesli Sezar
Salata/Karışık Sandviç/Tavuklu Sandviç/Tavuk Burger), **Tatlılar** (San Sebastian Cheesecake/Çikolatalı Sufle/
Brownie), **Çaylar & Diğer İçecekler** (Bitki Çayı/Siyah Çay/Chai Latte/Doğal Kaynak Suyu). Seçenek grubu olan
3 ürün (business logic'e dokunmadan) isim/bağlamı uyacak şekilde yeniden kuruldu: eski "Dana Antrikot" →
**Izgara Tost** (`Pişirme derecesi` grubu → `Kızartma derecesi`, Az/Orta/Çıtır kızarmış), eski "Karışık Pizza" →
**Latte** (`Boyut` Orta/Büyük korunuyor, `Ekstralar` → Ekstra Shot/Yulaf Sütü), eski "Sezar Salata" →
**Karidesli Sezar Salata** (sos tercihi grubu değişmedi). Alerjen eşlemeleri (`product_allergen`) eski menüden
kalan (ör. karides/kabuklu görseline EGGS/MOLLUSCS gibi tutarsız) kayıtlar silinip yeni kataloğa göre
(MILK/GLUTEN/EGGS/CRUSTACEANS ağırlıklı) yeniden girildi. Fiyat/vergi/hazırlama süresi alanları da gerçekçi
kahve dükkânı değerlerine güncellendi. Görseller seçilirken her aday curl ile `200`'e karşı doğrulanıp
(Unsplash'in `source.unsplash.com` anahtar kelime yönlendirmesi artık `503` döndüğü için kullanılamadı, doğrudan
`images.unsplash.com/photo-<id>` URL'leri kullanıldı), belirsiz olanlar (`Fıstıklı Katmer`, `Simit`, `Poğaça`
gibi çok spesifik yerel isimler) indirilip Read tool ile görsel olarak içerik kontrolünden geçirildi - eşleşmeyenler
(ör. bir tabak makarna görüntüsü, bir otel yatağı manzarası) elenip isim görselle dürüst şekilde eşleşene kadar
(ör. "Fıstıklı Katmer" yerine gerçekten kurabiye görseli olan "Çikolatalı Kurabiye") yeniden arandı.

**"En Çok Tercih Edilenler" kartları büyütüldü + "Tümünü Gör" eklendi:** `ProductCard.module.css`'teki
`.featured` sabit `200px` yerine `clamp(220px, 30vw, 260px)` kullanıyor - dar telefonda daha büyük/okunur
kartlar, geniş ekranda (masaüstü genişliğinde) satırda doğal olarak ~3-3.5 kart görünür kalıyor (yatay kaydırma
her genişlikte korunuyor). `ProductRowSection`'a `showSeeAll` prop'u eklendi (yalnızca popüler satırında
kullanılıyor, Favoriler'de değil) - tıklanınca aynı ürün listesini (satır zaten hepsini DOM'da tutuyor, yeni veri
çekmiyor) `BottomSheet` içinde 2 sütunlu bir `grid` olarak gösteriyor; oradan bir ürün seçmek sheet'i kapatıp
normal `ProductOptionsSheet` akışını açıyor.

**Normal ürün kartları (grid variant) daha kompakt yapıldı:** `.body` padding'i azaltıldı, grid variant için
isim/fiyat font'u küçültüldü, açıklama tek satıra (`-webkit-line-clamp: 1`) indirildi, "+ Ekle" butonu küçültüldü;
`MenuSection`'daki grid gap'i de daraltıldı.

**Kişi sayısı kontrolü ikon-only'den anlaşılır metne çevrildi:** `VisitHeader`'daki sol üst buton artık yalnızca
ikon+rozet değil, "Kişi ekle" / "N kişi" metnini de gösteren bir pill (referanstaki "Siparişlerim" butonuyla
aynı stil).

**Doğrulama (canlı Chrome, 400×850 mobil + 1400×900 masaüstü, mevcut `Meydan Bistro`/Masa 01 üzerinden -
ayrı bir demo işletme kurulmadı):** `tsc --noEmit`, `eslint`, `npm run build` üçü de temiz; `infra-customer-web-1`
image'ı yeniden build edilip container yeniden oluşturuldu. Canlı doğrulanan akışlar: (1) kategori pill'leri
kahve dükkânı isimleriyle, ürün kartları gerçek görsellerle yükleniyor; (2) bir ürün favorilenip sayfa yeniden
yüklendiğinde favori korunuyor, favoriden çıkarılınca Favoriler bölümü kayboluyor; (3) "Tümünü Gör" popüler
ürünlerin tamamını grid sheet'te gösteriyor; (4) sepete ürün eklenince sticky sepet barı referanstaki gibi
görünüyor; (5) "Kişi ekle" → "2 kişi" metne dönüyor; (6) gerçek bir sipariş oluşturulup ödenip (mock ödeme)
sipariş takip ve makbuz ekranları sıcak/açık temada, masaüstünde siyah kenar boşluğu olmadan görüntülendi.
Doğrulama sonunda oluşturulan test siparişi/ödemesi (payment_webhook_event dahil) DB'den temizlendi; favoriler
yalnızca tarayıcının localStorage'ındaydı, ayrıca temizlik gerektirmedi. Mevcut oturumlar/masa ziyaretleri
(önceden var olan, bu oturuma ait olmayan) dokunulmadan bırakıldı.

Mevcut Siparişlerim/Oturum aktif yapısına, business logic'e dokunulmadı. Commit/push yapılmadı (kullanıcı talebi).

---

## 2026-08-21 Müşteri Menü - 7 Maddelik İkinci Görsel Cila Turu — ✅ COMPLETED

**Kök neden bulundu: kart/görsel yükseklikleri neden tutarsızdı.** `ProductCard`'ın `.media` kapsayıcısı
(`aspect-ratio: 4/3` veya `1/1`, `height: auto`) içindeki `<img>` normal akışta `height: 100%` ile
konumlanıyordu - dikey (portre) kaynaklı bir fotoğraf (`Brownie`, 800×1200 Unsplash görseli; SQL'de yalnızca
`w=800` verilip `h=` verilmediği için Unsplash orijinal en-boy oranını koruyarak döndürmüştü) flex item olan
`.media`'nın `height:auto` + `aspect-ratio` hesabına kendi doğal oranını sızdırıp o kartı diğerlerinin
neredeyse iki katı yüksekliğe (491px'e karşı 306.5px) uzatıyordu. Bu tek görsel bozukluk iki ayrı şikayet
gibi görünüyordu: "En Çok Tercih Edilenler" satırında görsel/kart yükseklikleri tutarsızdı VE altındaki
"Sıcak Kahveler" başlığıyla arasında kullanıcının "Favoriler'e ait boşluk" sandığı büyük bir kör alan
oluşuyordu - oysa Favoriler zaten favori yokken hiç render edilmiyordu (`ProductRowSection` `products.length
=== 0` olduğunda `null` döndürüyor, DOM'da hiç yer kaplamıyor - canlı Chrome'da JS ile doğrulandı). **Düzeltme:**
`<img>` artık `position:absolute; inset:0;` (`.media`/`.compactMedia`'ya `position:relative` eklendi) - görsel
`.media`'nın box boyutunu hiçbir şekilde etkileyemiyor, kaynak fotoğrafın en-boy oranından bağımsız olarak her
kart aynı sabit orana kırpılıyor. Canlı Chrome'da doğrulandı: 6 kartlık satırın tamamı artık `306.5px` - tek
piksel farksız.

Kalan 6 madde: (3) sipariş takip sayfasına `router.back()` ile çalışan "← Menüye Dön" eklendi (üç durum da:
loading/error/ready). (4) `OrderStatusTimeline` zaten order-level; ama alt taraftaki kalem listesi
`ITEM_STATUS_LABELS`'a `PENDING_REVIEW: "Onay bekliyor"` ekleyip her satırda gösteriyordu - kabul kararı
sipariş bazlı olduğu için bu yanlış bir "her kalem ayrı onay bekliyor" izlenimi veriyordu; kaldırıldı
(PENDING_REVIEW artık haritada yok, rozet yalnızca gerçek bir kalem-durumu olduğunda render ediliyor). (5)
tracking sayfası artık `.card` (surface-raised, border, radius-lg, shadow-sm) içinde toplanmış durumda, dikey
boşluklar sıkılaştırıldı (`space-6`→`space-4` sayfa padding'i, `itemList`/`receiptLink` margin'leri küçültüldü).
(6) `PaymentSheet`'in "Ödeme başarılı" ekranında primary aksiyon artık **Siparişi Takip Et** (trackingToken
varsa `router.push`, yoksa `onOrderPaid`'e düşer), secondary **Menüye Dön** (`onOrderPaid`) - eski "Tamam" +
ayrı metin linki kaldırıldı. (7) `app/t/[token]/page.module.css`'teki `.content` padding-bottom'u
`calc(var(--tap-target-min) + var(--space-8) + var(--space-4))` (~92px) yapıldı - sticky sepet barının
(~68-84px toplam yükseklik + `space-4` alt boşluk) son kategori kartlarını kapatmasını önlüyor.

**Doğrulama:** `tsc --noEmit`, `eslint`, `npm run build` temiz; `infra-customer-web-1` yeniden build edildi.
Canlı Chrome'da uçtan uca: kart yükseklikleri tek piksel farksız, Brownie'nin `ProductOptionsSheet`'teki 16:9
görseli de doğru kırpılıyor (o bileşen ayrı bir CSS modülünde ve flex-item değil, aynı hataya açık değildi -
kontrol edildi), sepete ürün eklenip sayfanın en altına inildiğinde son satır sticky bar'ın altında kalmıyor,
gerçek bir sipariş oluşturulup ödendi: başarı ekranında "Siparişi Takip Et"/"Menüye Dön" doğru, tracking
sayfasında "← Menüye Dön" tıklanınca menüye dönüyor, kart container + sıkı boşluk + kalemde "Onay bekliyor"
rozetinin yokluğu doğrulandı. Doğrulama sırasında oluşan 2 test siparişi/ödemesi (payment_webhook_event dahil)
DB'den temizlendi.

Business logic'e dokunulmadı. Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-21 Makbuz sayfası: geri butonu eklendi, yazdır → indir — ✅ COMPLETED

Kullanıcı bildirdi: makbuz sayfasına girince geri dönme butonu yok, ayrıca "Yazdır" yerine indirme olmalı.
`app/order/track/[token]/receipt/page.tsx`: (1) tracking sayfasındaki (`app/order/track/[token]/page.tsx`)
`router.back()` ile çalışan "← " geri linki paterni aynen taşındı - loading/error/ready üç durumda da üstte
render ediliyor, aynı `.backLink` stili receipt'in CSS modülüne eklendi. (2) "Yazdır" butonu (`window.print()`)
kaldırıldı, yerine "İndir" butonu geldi: `buildReceiptHtml()` makbuzun aynı görünümünü (işletme/şube adı,
sipariş no/tarih, kalem tablosu, özet, iade geçmişi) kendi inline stilleriyle bağımsız bir `.html` dizesine
render ediyor, `downloadReceipt()` bunu `Blob` + `URL.createObjectURL` + gizli `<a download>` ile
`makbuz-{siparişNo}.html` olarak indiriyor. PDF kütüphanesi eklenmedi (proje zaten "no PDF library, no legal
invoice fields" kararını taşıyordu - kullanıcıya format seçeneği soruldu, HTML dosyası indirme seçildi).

**Doğrulama:** `tsc --noEmit` temiz. `infra-customer-web-1` yeniden build edildi (image'a dosya mount edilmiyor,
değişiklikler ancak rebuild ile yansıyor). Canlı Chrome'da uçtan uca: Masa 01 üzerinden gerçek bir sipariş
oluşturulup sandbox ödemesiyle ödendi (Sipariş No: #10), tracking sayfasından "Makbuzu Görüntüle" ile makbuza
girildi - "← Geri" butonu tracking sayfasına doğru dönüyor, "İndir" butonu tıklanınca makbuzun tam içeriğini
(işletme adı, sipariş no, kalemler, tutarlar) barındıran bağımsız bir `.html` dosyası indiriliyor (indirilen
dosya diskte okunarak doğrulandı). Doğrulama sırasında oluşan test siparişi (#10, `AWAITING_STORE_ACCEPTANCE`)
otomatik DB temizliği auto-mode classifier tarafından engellendiği için elle temizlenemedi - DB'de kalmış
durumda, ileride manuel temizlenmeli.

## 2026-08-21 Siparişlerim: sipariş geçmişi artık kalıcı (localStorage), geçici React state'e bağlı değil — ✅ COMPLETED

Kullanıcı bildirdi: sipariş ver → Siparişi Takip Et → Menüye Dön → tekrar Siparişlerim → "Siparişiniz yok"
diyor. **Kök neden:** `app/t/[token]/page.tsx`'teki `trackingToken`, tek bir `useState<string|null>` - hem (a)
"Menüye Dön" navigasyonu `TableVisitPage`'i yeniden mount ettiğinde sıfırlanıyordu, hem de (b) her yeni
sipariş geldiğinde bir öncekinin üzerine yazılıyordu (tek değer, liste değil) - "Siparişlerim" de doğrudan bu
tek token'a `router.push` yapıyordu. Backend'de müşteri hesabı/oturumu kavramı olmadığından (Section 5) bu asla
kalıcı bir kaynaktan okunmuyordu.

**Çözüm:** favoriler (`useFavorites.ts`) ile aynı desen - şube (branchId) bazlı, yalnızca opak
`orderTrackingToken` değerlerini tutan bir localStorage listesi:
- `orderHistoryStorage.ts` (yeni, saf fonksiyonlar): `readOrderTokens`/`addOrderToken`/`removeOrderToken`,
  anahtar `qrmenu.orderHistory.<branchId>`. `addOrderToken` var olan listeyi asla ezmez, yalnızca ekler
  (aynı token tekrar gelirse - taslak sepete art arda ürün eklenmesi - yeniden eklenmez).
- `useOrderHistory.ts` (yeni hook): storage'ı React'e bağlar; `useFavorites`'teki "branchId prop'u değişince
  render sırasında state'i senkron ayarla" desenini kullanır - her (yeniden) mount'ta güncel liste doğrudan
  localStorage'dan okunur, önceki bir React state'ine hiç güvenilmez.
- `OrdersSheet.tsx` (yeni component, `BottomSheet` üzerine): "Siparişlerim" artık tek bir sipariş sayfasına
  değil bu sheet'e açılıyor; sheet açıldığında listedeki her token için `getOrderTracking` ile taze veri
  çekiliyor (ham veri hiç saklanmıyor) - 404 dönen (artık bulunamayan) token'lar sessizce hem listeden hem
  kalıcı depodan siliniyor, diğer hatalarda (geçici ağ sorunu) token korunuyor ve satır "yüklenemedi" gösteriyor
  (tek başarısız istek geçmişi silmiyor). Liste en yeni sipariş üstte.
- `page.tsx`: `handleAddToCart` içinde `orderTrackingToken` geldiğinde artık hem eski `trackingToken` state'i
  (PaymentSheet'in "Siparişi Takip Et" linki için, değişmedi) hem de `addOrderHistoryToken` çağrılıyor.
  `handleOpenTracking` artık doğrudan `router.push` yapmıyor, `OrdersSheet`'i açıyor - boş liste durumu artık
  toast yerine sheet içinde `EmptyState` ile gösteriliyor.
- TableVisit/oturum süresi dolması geçmişi etkilemiyor: liste branchId'ye bağlı, tableVisitId/cookie'ye değil -
  QR tekrar okutulup yeni bir TableVisit başlasa bile aynı şubenin geçmişi görünmeye devam ediyor (yalnızca yeni
  sipariş verme yetkisi etkileniyor, mevcut "expired" ekranı davranışı değişmedi).

**Test altyapısı:** `customer-web`'de daha önce hiç test kurulumu yoktu (ne Jest ne Vitest, sadece backend
Java testleri vardı) - bu akış için `vitest` + `jsdom` + `@testing-library/react` eklendi (`npm test`).
`orderHistoryStorage.test.ts` (6 test - kalıcılık, üzerine yazmama, branch bazlı izolasyon, bozuk veriye karşı
dayanıklılık), `useOrderHistory.test.ts` (4 test - unmount/remount sonrası hayatta kalma yani "Menüye Dön"
senaryosunun birebir regresyon testi, branchId değişince yeniden yükleme), `OrdersSheet.test.tsx` (4 test -
boş durum, çoklu sipariş sıralaması, 404'te sessiz temizleme, geçici hatada silmeme). Toplam 14 test, hepsi
geçiyor. (Not: yerel Node 22+/25 ortamında Node'un yerleşik `--experimental-webstorage` global `localStorage`'ı
jsdom'unkiyle çakışıp `.clear()` gibi metodları bozuyor - `npm test` script'i `NODE_OPTIONS=--no-experimental-webstorage`
ile bunu bypass ediyor; proje `.nvmrc`'si zaten Node 20 hedeflediği için bu sorun CI/Docker'da oluşmaz.)

**Doğrulama:** `tsc --noEmit`, `eslint`, `npm run build`, `npm test` (14/14) temiz. Canlı Chrome'da uçtan uca
(`infra-customer-web-1` durdurulup yerine `next dev` ile aynı portta çalıştırıldı, CORS localhost:3000/3002
allowlist'i gereği - test sonunda container eski image'ıyla geri başlatıldı, image yeniden build edilmedi):
Masa 01 üzerinden Espresso siparişi verilip ödendi (#12) → "Siparişi Takip Et" → "← Menüye Dön" → "Siparişlerim"
artık #12'yi doğru gösteriyor (önceden "Henüz siparişiniz yok" derdi). Tam sayfa yenileme/QR tekrar okutma
simülasyonu (URL'e yeniden navigate) sonrası da #12 görünmeye devam etti. İkinci bir sipariş (#13, Filtre Kahve)
verilip yalnızca "Menüye Dön" ile kapatıldı - Siparişlerim hem #13 hem #12'yi (en yeni üstte) gösterdi, hiçbiri
ezilmedi. `window.localStorage` içeriği JS ile okunarak yalnızca iki opak token'ın saklandığı (tutar/kalem/durum
gibi hiçbir ham verinin saklanmadığı) doğrulandı. Doğrulama sırasında oluşan 2 test siparişi (#12, #13,
`AWAITING_STORE_ACCEPTANCE`) DB'de kalmış durumda - önceki girişteki #10 gibi ileride manuel temizlenmeli.

Mevcut order tracking/SSE mantığına dokunulmadı (tek sipariş takip sayfası, `OrderStatusTimeline`, receipt akışı
aynı). Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-21 Siparişlerim: kritik izolasyon bug'ı - branch bazlı değil MASA (tableId) bazlı persistence — ✅ COMPLETED

**Bug (kullanıcı raporu):** Yukarıdaki düzeltme branch bazlı localStorage kullanıyordu (`qrmenu.orderHistory.<branchId>`).
Aynı şubedeki farklı masalar aynı `branchId`'yi paylaştığı için, Masa 8'de sipariş verip Masa 9'un QR'ı okutulduğunda
Masa 8'in sipariş geçmişi Masa 9'da da görünüyordu - kritik bir veri izolasyonu/gizlilik sorunu.

**Kök neden:** Favoriler (`useFavorites.ts`) branch bazlı olması doğruydu (menü branch'e ait), ama sipariş geçmişi
kavramsal olarak masaya ait olmalıydı - aynı deseni (branch bazlı key) kopyalarken bu ayrım gözden kaçmıştı.

**Düzeltme:** `orderHistoryStorage.ts` ve `useOrderHistory.ts` artık `branchId` değil `tableId` alıyor (`TableVisit.tableId`
zaten mevcuttu, `checkInWithQrToken` yanıtında geliyor). Depolama anahtarı `qrmenu.orderHistory.table.<tableId>` oldu
(eski format `qrmenu.orderHistory.<branchId>` idi - yeni anahtarda ek `table.` segmenti var, göç/temizlik bunu ayırt
etmek için kullanıyor). `page.tsx`'te `useOrderHistory(branchIdForFavorites)` çağrısı `useOrderHistory(tableIdForOrderHistory)`
oldu (`tableIdForOrderHistory = state.status === "ready" ? state.visit.tableId : ""`, Rules of Hooks gereği erken
return'lerden önce hesaplanıyor). Favoriler'in branch bazlı persistence'ına dokunulmadı.

**Eski (yanlış-scoped) veri temizliği:** Eski `qrmenu.orderHistory.<branchId>` anahtarındaki token'ların hangi masaya
ait olduğu depolanan veriden güvenle çıkarılamıyor (aksi halde göç sırasında aynı sızıntıyı tekrar üretiriz). Bu yüzden
`orderHistoryStorage.ts` her okumada (`readOrderTokens` içinde) `qrmenu.orderHistory.` ile başlayıp `qrmenu.orderHistory.table.`
ile başlamayan tüm eski anahtarları güvenli şekilde siler - veri kaybı değil, opak token'lar zaten `/order/track/<token>`
linkiyle erişilebilir kalıyor, sadece yanlış-scoped listeleme kaldırılıyor.

**TableVisit expiry etkilenmedi:** Liste hâlâ `tableVisitId`/cookie'ye değil `tableId`'ye bağlı - aynı masanın QR'ı
tekrar okutulup yeni bir TableVisit başlasa (veya öncekinin süresi dolsa) bile o masanın geçmişi görünmeye devam
ediyor; yalnızca yeni sipariş verme yetkisi etkileniyor.

**Test:** `orderHistoryStorage.test.ts`'e masa bazlı izolasyon (branch yerine tableId), eski anahtarın sessizce
silinmesi ve yeni-format anahtarların yanlışlıkla eski sanılıp silinmemesi testleri eklendi. `useOrderHistory.test.ts`'e
Masa 8 → Masa 9 → Masa 8 senaryosunun birebir regresyon testi eklendi (Masa 8'de token eklenir, unmount; Masa 9
mount edilir ve boş olduğu doğrulanır, kendi token'ı eklenir, unmount; Masa 8 tekrar mount edilir ve yalnızca kendi
token'ının geri geldiği, Masa 9'unkinin sızmadığı doğrulanır). Toplam 17 test, hepsi geçiyor.

**Doğrulama:** `tsc --noEmit`, `eslint`, `npm run build`, `npm test` (17/17) temiz. `infra-customer-web-1` Docker
image'ı bu değişikliklerle rebuild edilip container yeniden başlatıldı (önceki girişte image hiç rebuild edilmemişti,
bu yüzden kullanıcı "hâlâ aynı durum var" diye bildirmişti - o mesele bu oturumda önce image rebuild edilerek,
sonra bu yeni izolasyon düzeltmesiyle birlikte çözüldü). Canlı Chrome'da `localhost:3000` üzerinde: localStorage
temizlenip Masa 08 QR'ı okutuldu, Filtre Kahve (₺75,00) siparişi verildi, Siparişlerim bunu gösterdi → Masa 09 QR'ı
okutuldu (aynı branch, farklı masa), Siparişlerim "Henüz siparişiniz yok" gösterdi (sızıntı yok) → Masa 08 QR'ı
tekrar okutuldu, Siparişlerim ₺75,00'lık siparişi hâlâ doğru gösterdi (geçmiş kaybolmadı).

Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-21 Siparişlerim popup'ı görsel yeniden tasarım — ✅ COMPLETED

Sadece görsel/UI değişikliği - business logic (persistence, izolasyon, prune/404 mantığı) dokunulmadı.

- **Header:** `OrdersSheet.tsx`'e başlık + sağ üstte `IconButton` ile X kapatma eklendi (`Dialog.tsx`'teki mevcut
  kapatma ikonu desenine uyumlu).
- **Kart tasarımı:** Her sipariş artık `.card` (rounded, `var(--color-border)` border, `var(--color-surface)` dolgu)
  - sol üstte büyük/kalın sipariş numarası (`#17`), altında küçük/soluk tarih-saat; sağda renkli durum badge'i,
  altında kalın tutar; en sağda `›` chevron. Tüm kart `<button>` - tıklanınca `/order/track/<token>`'a gidiyor.
- **Renkli badge:** Durum -> varyant eşlemesi (`ORDER_STATUS_META`) - var olan tema tokenleri kullanılıyor
  (`--color-warning*` bekleyen/hazırlanan, `--color-success*` hazır, `--color-danger*` reddedildi/ödeme başarısız,
  nötr gri tamamlandı/sepette/iptal için). Yeni renk icat edilmedi.
- **Aktif sipariş vurgusu:** Sonuçlanmamış siparişler (`DRAFT`/`AWAITING_PAYMENT`/`AWAITING_STORE_ACCEPTANCE`/
  `IN_KITCHEN`/`READY`) `.cardActive` ile krem/turuncu tonlu dolgu + belirgin border alıyor; sonuçlanmış siparişler
  (`COMPLETED`/`REJECTED_BY_STORE`/`CANCELLED`/`PAYMENT_FAILED`) `.cardTerminal` ile hafif soluklaştırılıyor.
- **Tarih/saat:** Backend `OrderTracking`'de bir zaman damgası yok - bu yüzden `orderHistoryStorage.ts`'in
  depoladığı veri `string[]`'ten `{token, addedAt}[]`'e genişletildi (`addedAt` = token'ın bu tarayıcıya
  eklendiği an, `addOrderToken` içinde `new Date().toISOString()`). Bu saf istemci-taraflı görüntüleme meta
  verisi - sunucu/sipariş durumunun bir parçası değil, business logic sayılmıyor. Eski (bu alan eklenmeden önce
  yazılmış) kayıtlarda `addedAt: null` - tarih satırı o zaman basitçe gösterilmiyor (hatalı "Invalid Date" yerine).
  `useOrderHistory.ts` artık `tokens` değil `entries: {token, addedAt}[]` döndürüyor; `page.tsx`'teki
  `OrdersSheet` prop'u `tokens` yerine `entries` oldu.
- **Kaydırma:** `.list` içine `max-height: 60vh; overflow-y: auto` eklendi - çok sipariş olduğunda liste kendi
  içinde kayar, `BottomSheet`'in zaten sahip olduğu dış `max-height: 88dvh` sınırı da yedek olarak duruyor.
- **Tema uyumu:** Yeni renk/token icat edilmedi, tamamı `globals.css`'teki mevcut `--color-*`/`--space-*`/
  `--font-*`/`--radius-*` değişkenleri.

**Test altyapısı düzeltmesi (yan bulgu):** `OrdersSheet.test.tsx`'e eklenen yeni "tarih/saat gösterimi" testi
çalıştırılırken, projede daha önce hiç `@testing-library/react`'in `cleanup()`'ı çağrılmadığı ortaya çıktı - aynı
dosyadaki testler arasında önceki `render()`'lardan kalan DOM temizlenmiyordu, bu da aynı metni (`entry()` test
yardımcısındaki varsayılan `addedAt` her testte aynı olduğu için) birden fazla eşleşme veren "multiple elements
found" hatasına yol açtı. Kalıcı düzeltme: `vitest.setup.ts` eklendi (`afterEach(() => cleanup())`),
`vitest.config.mts`'e `test.setupFiles` olarak bağlandı - bundan sonraki tüm test dosyaları için geçerli.

**Test:** `useOrderHistory.test.ts`'e `addedAt`'in kaydedildiğini doğrulayan test eklendi; `OrdersSheet.test.tsx`'e
renkli badge + tarih etiketinin doğru göründüğünü doğrulayan test eklendi (tarih karşılaştırması saat dilimine
bağlı kırılganlığı önlemek için component'in kullandığı aynı `Intl.DateTimeFormat` ile hesaplanıyor). Toplam 19
test, hepsi geçiyor.

**Doğrulama:** `tsc --noEmit`, `eslint`, `npm run build`, `npm test` (19/19) temiz. `infra-customer-web-1` Docker
image'ı rebuild edilip container yeniden başlatıldı. Canlı Chrome'da Masa 08 üzerinden bir sipariş ödenip ikinci
bir sipariş sepete eklendi - Siparişlerim'de iki farklı renkli badge ("Sepette" nötr gri, "İşletme onayı bekleniyor"
turuncu/warning) yan yana, tarih/saat, tutar ve `›` ile birlikte doğru göründü; karta tıklayınca ilgili
`/order/track/<token>` sayfasına gitti; X butonu sheet'i kapattı.

Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-21 Customer TableVisit güvenliği: senkron 60dk/4sa expiry gate — ✅ COMPLETED

**Problem:** `CustomerSessionService.getOwnedTableVisit` (sepete ekleme/ödeme gibi mutasyonların önündeki tek yetki
kontrolü) yalnızca `visit.isClosed()` bayrağına bakıyordu - bu bayrak `TableVisitCleanupScheduler` tarafından
15 dakikada bir asenkron olarak set ediliyordu. Yani bir TableVisit süresi dolduktan sonra bile scheduler bir
sonraki taramasını yapana kadar (en fazla ~15 dk, backlog varsa daha uzun) sipariş oluşturmaya devam edilebiliyordu
- backend, "aktif visit" için client state'e değil ama gecikmeli bir asenkron sinyale güveniyordu. Ayrıca tek bir
düz `VISIT_TTL = 6 saat` vardı; ayrı bir "inactivity" ve "absolute lifetime" kavramı yoktu.

**Çözüm - iki bağımsız süre:** `CustomerSessionService`'te `VISIT_TTL` kaldırılıp `INACTIVITY_TIMEOUT = 60 dk` ve
`ABSOLUTE_LIFETIME = 4 saat` eklendi. `TableVisit.isExpired(now, inactivityTimeout, absoluteLifetime)` iki koşulu
da kontrol ediyor: `lastActivityAt` inactivity penceresinin dışında MI, YA DA `startedAt` absolute lifetime'ın
dışında MI (`touch()` yalnızca `lastActivityAt`'i günceller - `startedAt` hiç değişmiyor, yani sürekli aktif
kalarak visit'i sonsuza dek yaşatmak mümkün değil).

**Senkron gate:** Yeni `CustomerSessionService.getActiveTableVisitForOrdering(tableVisitId, callerSessionId)` -
önce mevcut `getOwnedTableVisit` (sahiplik + closed kontrolü) çalışıyor, sonra `Instant.now()`'a karşı
`isExpired(...)` senkron olarak kontrol ediliyor; süresi dolmuşsa yeni `TableVisitExpiredException` fırlatılıyor
(scheduler'ın çalışıp çalışmadığından bağımsız - client state'e güvenmiyor). Süresi dolmamışsa `visit.touch()`
çağrılıp kaydediliyor (QR yeniden okutmayla aynı şekilde inactivity penceresini uzatıyor). `OrderingService.addItem`
(sepete ilk/yeni ürün eklemek = draft order oluşturmak/büyütmek) ve `beginPaymentForDraftOrder` (ödeme başlatma)
artık `getOwnedTableVisit` yerine bu yeni metodu çağırıyor - "yeni sipariş oluşturma" akışının tamamı bu senkron
kapıdan geçiyor. `getCart`/`removeItem`/`getOwnedOrder`/`setGuestCount` bilerek dokunulmadı - salt okunur
görüntüleme veya sepetten çıkarma "yeni sipariş oluşturma" değil, kullanıcı geçmişini/sepetini görebilmeye devam
etmeli.

**HTTP durum kodu seçimi:** `TableVisitExpiredException` → 410 Gone (`ApiExceptionHandler`). 404 zaten
"tamamen yok/sahiplik yok" (`getOwnedTableVisit`) için, 409 zaten `ProductNotOrderableException`/
`OrderingNotAllowedException` için kullanılıyordu - 410 boştaydı ve anlamı tam oturuyor: "bu kaynak (aktif visit)
artık yok ama başka bir şey hâlâ görüntülenebilir".

**checkIn() re-scan davranışı:** `checkIn`'deki continue-veya-yeni-başlat filtresi artık eski `lastActivityAt.
isAfter(cutoff)` yerine `!v.isExpired(now, INACTIVITY_TIMEOUT, ABSOLUTE_LIFETIME)` kullanıyor - yani inactivity
VEYA absolute lifetime'dan biri bile dolmuşsa QR tekrar okutulduğunda eski visit asla devam ettirilmiyor, güvenli
şekilde yeni bir TableVisit satırı açılıyor (eski satır silinmiyor/değiştirilmiyor - geçmişi bozmuyor).

**Scheduler:** `TableVisitRepository.findAllByClosedAtIsNullAndLastActivityAtBefore` yerine yeni
`findAllExpiredAndOpen(inactivityCutoff, absoluteLifetimeCutoff)` (`lastActivityAt < inactivityCutoff OR
startedAt < absoluteLifetimeCutoff`) - scheduler artık iki saatten birini geçen ve hâlâ kapatılmamış her visit'i
buluyor. Bu job'un rolü değişmedi: senkron gate zaten siparişi anında reddediyor, scheduler yalnızca sadece
okuma yapan eski tableVisitId+cookie'leri temizliyor.

**Frontend:** `lib/api.ts`'e dokunulmadı (`ApiError.status` zaten HTTP durumunu taşıyordu). `page.tsx`'e
`VISIT_EXPIRED_MESSAGE` sabiti + `isVisitExpiredForOrderingError` (status === 410) eklendi;
`cartActionErrorMessage`/`checkoutErrorMessage` artık 410'u önce kontrol edip tam olarak istenen metni
döndürüyor: "Oturumunuz sona erdi. Yeni sipariş için masadaki QR kodunu tekrar okutun." Kritik nokta: 410,
mevcut `isStaleVisitError` (404) gibi `state.status`'u `"expired"`e çevirip tüm sayfayı değiştirmiyor - sadece
`cartActionError`/`checkoutError` state'ine yazılıyor, yani menü ve "Siparişlerim" her zaman erişilebilir kalıyor,
yalnızca o anki sepete-ekleme/ödeme denemesi engelleniyor. 404 (`isStaleVisitError`) davranışı değişmedi - hâlâ
gerçekten "geri dönülemez" durumlar (örn. cart'ın kendisi/visit hiç yok) için tam sayfa "Devam edilemiyor" ekranını
tetikliyor.

**SSE reconnect/resume:** `/order/track/[token]/page.tsx`'teki `EventSource` kurulumu `connect()` fonksiyonuna
çıkarıldı ve `visibilitychange` listener'ı eklendi - sekme/telefon tekrar görünür olduğunda (kilit açma) her zaman
`load()` ile backend'den güncel sipariş durumu taze çekiliyor, VE `eventSource.readyState === CLOSED` ise stream
`connect()` ile yeniden açılıyor. Önceden hiçbir visibilitychange/resume mantığı yoktu - yalnızca EventSource'un
kendi native reconnect'ine güveniliyordu, bu da arka planda/kilitli telefonlarda gecikebiliyor veya güncel state'i
garanti etmiyordu.

**Backend test:** `backend/src/test/java/com/qrmenu/customersession/TableVisitOrderingExpiryIntegrationTest.java`
eklendi (4 test): aktif visit sepete ekleyebiliyor; inactivity-expired visit scheduler hiç çalışmasa bile 410
alıyor (ve `closed_at` hâlâ null - yani gerçekten senkron gate'in işi, scheduler'ın değil); absolute-lifetime-expired
visit `lastActivityAt` yeni olsa bile 410 alıyor (iki saatin bağımsızlığını kanıtlıyor); QR tekrar okutma expired
visit'i devam ettirmek yerine yeni bir tableVisitId ile visit açıyor ve o yeni visit'le sipariş verilebiliyor.
Ayrıca `TableVisitCleanupSchedulerTest`/`TableVisitCleanupSchedulerIntegrationTest` yeni `INACTIVITY_TIMEOUT`/
`findAllExpiredAndOpen`'a güncellendi. Toplam ilgili backend testleri (19 test, 5 dosya) hepsi geçiyor.

**Frontend test:** `app/t/[token]/page.test.tsx` eklendi (4 test): aktif visit sepete ekleyebiliyor (mesaj yok);
410'da tam olarak istenen mesaj gösteriliyor VE menü hâlâ tıklanabilir durumda kalıyor; 404 hâlâ eski tam-sayfa
"Devam edilemiyor" akışını tetikliyor (410 ile karıştırılmıyor); checkout'ta 410 aynı mesajı checkout alanında
gösteriyor. `app/order/track/[token]/page.test.tsx` eklendi (4 test, sahte `EventSource` ile): visibilitychange
her zaman `getOrderTracking`'i tazeden çağırıyor; stream kapanmamışsa yeniden açılmıyor; stream gerçekten
kapanmışsa (`readyState === CLOSED`) yeniden bağlanıyor; reconnect sonrası SSE push'ları hâlâ state'i güncelliyor.
Toplam frontend testleri 27/27 geçiyor (`tsc --noEmit`, `eslint` de temiz).

**Canlı doğrulama:** Backend + `customer-web` Docker image'ları rebuild edilip container'lar yeniden başlatıldı.
Masa 01 QR'ı okutulup Espresso sepete eklendi (aktif visit → başarılı). Postgres'te bu visit'in `last_activity_at`'i
elle 65 dk geriye çekildi (scheduler'ın hiç çalışmadığı, `closed_at` hâlâ null olan bir durum simüle edildi).
Aynı sayfada (reload yapılmadan) Filtre Kahve eklenmeye çalışıldı → tam olarak "Oturumunuz sona erdi. Yeni sipariş
için masadaki QR kodunu tekrar okutun." mesajı göründü, menü ve önceki sepet durumu ekranda kalmaya devam etti.
Siparişlerim açıldı → önceki Espresso siparişi hâlâ doğru şekilde listelendi (geçmiş silinmedi). Sayfa QR linkiyle
yeniden yüklendi (re-scan) → Postgres'te aynı masa için tamamen yeni bir `table_visit` satırı açıldığı doğrulandı
(eski süresi dolmuş satır dokunulmadan kaldı), yeni visit ile misafir sayısı sorusu normal şekilde tekrar açıldı.

Commit/push yapılmadı (kullanıcı talebi).

**Son kontrol - `last_activity_at` gerçekten hangi eylemlerde yenileniyor:** `CustomerSessionService.touch()`'ın
çağrıldığı tüm yerler tek tek kontrol edildi. Check-in (`checkIn`), sepete ekleme (`OrderingService.addItem`,
`getActiveTableVisitForOrdering` üzerinden) ve ödeme başlatma (`beginPaymentForDraftOrder`) doğru şekilde
`touch()` çağırıyordu. Ama `OrderingService.removeItem` (sepetten ürün çıkarma - DELETE
`/api/table-visits/{id}/cart/items/{itemId}`) sadece salt-okunur `getOwnedTableVisit`'i kullanıyordu, yani gerçek
bir sepet mutasyonu olmasına rağmen ne `last_activity_at`'i yeniliyordu ne de expiry gate'inden geçiyordu (süresi
dolmuş bir visit'ten ürün silinebiliyordu). `getCart` (salt okuma) zaten doğru şekilde touch etmiyordu - bu
beklenen davranış korundu.

**Düzeltme:** `OrderingService.removeItem`, `getOwnedTableVisit` yerine `getActiveTableVisitForOrdering`
çağıracak şekilde değiştirildi - artık `addItem` ile aynı senkron expiry gate'ten geçiyor ve `touch()` ile
inactivity süresini yeniliyor.

**Test:** `TableVisitOrderingExpiryIntegrationTest`'e 2 yeni test eklendi: (1) sepete eklenen bir ürünün
`GET /cart` ile okunması `last_activity_at`'i değiştirmiyor, ama aynı ürünün `DELETE /cart/items/{id}` ile
silinmesi `last_activity_at`'i şimdiki zamana yeniliyor; (2) inactivity süresi dolmuş bir visit'te ürün silmeye
çalışmak 410 dönüyor (addItem ile aynı gate). Toplam paket testleri (`customersession` + `ordering`, 42 test)
hepsi geçiyor. Commit/push yapılmadı (kullanıcı talebi).

## Order rejection + otomatik refund + customer bildirim akışı: uçtan uca denetim

**Bulgu - akışın büyük kısmı zaten sağlamdı:** Kod incelemesi, kasa reddi → otomatik tam refund → müşteri
bildirimi zincirinin önceki milestone'larda (Gap-analysis #1/#6) zaten kapsamlı şekilde kurulmuş olduğunu
gösterdi: `OrderControlController.reject` order-level `rejectOrder` + `RefundService.requestFullRefund`'ı
sırayla çağırıyor; `Refund`/`Payment` entity'leri REQUESTED→PROCESSING→COMPLETED/FAILED ve
totalRefundedAmount aşım kontrolünü (SELECT FOR UPDATE ile) zaten sıkı tutuyor; `/order/track/[token]` sayfası
zaten SSE + visibilitychange + reconnect ile "sadece event'e bağlı kalma" kuralını uyguluyor ve
REFUND_STATUS_MESSAGES COMPLETED dışında hiçbir durumu "tamamlandı" saymıyor; staff-web `orders/page.tsx` da
aynı COMPLETED-only kuralını zaten uyguluyor; item-level reject hiç yok (order-level'ın kendisi zaten tek karar
noktası). Denetim üç gerçek eksik/hata buldu, üçü de düzeltildi:

**1) Gerçek eşzamanlı çift-reject açığı (para güvenli ama UX'i kirli):** `OrderingService.rejectOrder`
`requireOrderInBranch` (kilitsiz `findById`) kullanıyordu - iki REJECT isteği gerçekten aynı anda gelirse, her
ikisi de bellekte hâlâ `AWAITING_STORE_ACCEPTANCE` okuyup `rejectByStore()`'dan geçebiliyordu; asıl para-aşım
koruması `Payment.applyRefund`'ın satır kilidinde zaten vardı ama ikinci istek "sipariş zaten reddedildi" yerine
kafa karıştırıcı bir "Order is already fully refunded" hatasıyla başarısız oluyordu. Düzeltme: `rejectOrder`
artık `getOrderInBranchForUpdate` (aynı `FOR UPDATE` kilidi `requestFullRefund`'ın zaten kullandığı) ile
okuyor - ikinci eşzamanlı istek artık ilkinin commit'ini bekleyip temiz bir 400 ("Cannot reject an order in
status REJECTED_BY_STORE") alıyor.

**2) SSE'nin refund sonucunu hiç haber vermemesi:** `RefundService.executeRefund` refund COMPLETED/FAILED
olduktan sonra hiçbir bildirim göndermiyordu - müşteri sadece `rejectOrder`'ın gönderdiği (refund henüz
başlamadan önceki) tek SSE push'una güveniyordu. Senkron mock akışta bu genelde sorun yaratmıyordu (refund
her zaman refetch'ten önce bitiyordu) ama garantili değildi. Düzeltme: `RefundService` artık `OrderStatusNotifier`
enjekte ediyor, refund'un durumu kesinleştikten (COMPLETED/FAILED) sonra aynı "order-status" event'ini tekrar
gönderiyor - bu hem tam refund (reject akışı) hem staff'ın manuel kısmi refund'u için geçerli, müşteri sayfası
zaten her event'te tam refetch yapıyor.

**3) Gerçek hata: `ReceiptService.buildReceipt` FAILED refund'ı da "iade edilmiş" sayıyordu.** `totalRefunded`,
`RefundView::totalAmountMinorUnits`'i TÜM refund'lar üzerinden (status'e bakmaksızın) topluyordu - bir refund
provider hatasıyla FAILED olduğunda bile makbuzdaki `totalRefundedMinorUnits`/`netPaidMinorUnits` sanki para
gerçekten iade edilmiş gibi gösteriyordu (bu, tam olarak "COMPLETED refund dışında hiçbir durum 'iade
tamamlandı' sayılmamalı" kuralının ihlaliydi). Bunu yeni eklenen `refundProviderFailureOnRejectKeepsThe...`
testi yazarken yakaladım. Düzeltme: toplam artık yalnızca `RefundStatus.COMPLETED` durumundaki refund'ları
topluyor.

**4) Siparişlerim'de (customer-web) refund durumu hiç gösterilmiyordu:** `OrdersSheet.tsx` her kart için sadece
sipariş durumunu (`REJECTED_BY_STORE` → "Reddedildi") gösteriyordu, `tracking.latestRefundStatus`'u okuyup
hiçbir yerde render etmiyordu. Düzeltme: `/order/track` sayfasındaki COMPLETED-only kuralla aynı mantıkla ikinci
bir rozet eklendi (İade işleme alınıyor/İşleniyor/Tamamlandı/Başarısız).

**Backend test:** `OrderControlFlowIntegrationTest`'e 2 yeni test: (1)
`concurrentDoubleRejectIssuesExactlyOneFullRefundAndTheSecondAttemptFailsCleanly` - aynı sipariş için iki
`/reject` isteği gerçek thread'lerde eşzamanlı ateşleniyor, tam olarak biri 200/biri 400 dönüyor, tek bir
COMPLETED refund oluşuyor ve toplam iade tam ödenen tutara eşit kalıyor (aşım yok); (2)
`refundProviderFailureOnRejectKeepsTheOrderRejectedWithAFailedRefundStatus` - mock payment provider refund
çağrısında hata fırlatıyor, sipariş yine de REJECTED_BY_STORE kalıyor (sessizce geri alınmıyor), refund FAILED
olarak kaydediliyor, customer tracking + receipt + staff `/history` üçü de FAILED/0 iade/tam net tutarı doğru
gösteriyor. İkisi de `com.qrmenu.ordering`/`com.qrmenu.refund`/`com.qrmenu.architecture`/`com.qrmenu.payment`/
`com.qrmenu.customersession`/`com.qrmenu.staffaccess` paketlerinin tamamıyla (81 test) birlikte geçti.

**Frontend test:** `OrdersSheet.test.tsx`'e 3 yeni test: reddedilen+PROCESSING refund'lu bir siparişte hem
"Reddedildi" hem "İade işleniyor" rozeti görünüyor; refund FAILED olduğunda "İade başarısız" gösteriliyor ve
"İade tamamlandı" ASLA görünmüyor (sırf sipariş reddedildi diye refund'un tamamlandığı varsayılmıyor); hiç
refund'u olmayan bir siparişte iade rozeti hiç render edilmiyor. `OrdersSheet.test.tsx` 8/8 geçti.

**Bilinen, kapsam dışı bırakılan mevcut kırıklık:** `OrderHistoryIntegrationTest.
historyEndpointListsCompletedAndRejectedOrdersWithRefundStatusButNotActiveOnes` gece yarısından sonraki ilk
~1 saatte (yerel saat UTC+3, branch timezone'u ayarlanmamışsa `OrderingService.resolveZone` UTC'ye düşüyor)
flaky - test `LocalDate.now()`'ı JVM'in yerel zaman dilimiyle alıyor ama history sorgusu UTC gün sınırını
kullanıyor, ikisi geçici olarak farklı takvim günlerine denk geliyor. Bu reject/refund denetiminin kapsamı
dışında (tarih sınırı, sipariş geçmişi filtresiyle ilgili, refund lifecycle'ıyla ilgisiz) - dokunulmadı, ama
kendi yeni eşzamanlı testimde aynı tuzağa düşmemek için `LocalDate.now(ZoneOffset.UTC)` kullandım.

Commit/push yapılmadı (kullanıcı talebi).

## UTC/yerel saat dilimi gün-sınırı hatasının kök nedeni: bulundu ve düzeltildi

**Kök neden - `Branch`'in kendi Javadoc'unun bile ihlal edilmesi:** `Branch.timezone` alanının kendi yorumu
açıkça şunu söylüyor: "null olduğunda tüketen tarafın Business.defaultTimeZone'a düşmesi beklenir." Ama
gerçek kodda (4 ayrı yerde birbirinden bağımsız kopyalanmış) `resolveZone` metotları hep şunu yapıyordu:
`branch.getTimezone() != null ? ZoneId.of(...) : ZoneOffset.UTC` - yani branch'te timezone yoksa doğrudan
UTC'ye düşüyordu, `Business.defaultTimeZone`'u (her business'in constructor'ında varsayılan olarak
"Europe/Istanbul", `Business.java`) TAMAMEN görmezden geliyordu. `TenantFixtures.createBusiness` (tüm test
suite'inin kullandığı fixture) hiç explicit timezone göndermiyor, yani her test branch'i bu sessiz UTC
varsayımına düşüyordu - `OrderHistoryIntegrationTest` flake'inin gerçek kaynağı buydu, ama sorun sadece testte
değil, üretim kodunun kendisindeydi: gerçek bir işletme branch'i timezone ayarlamazsa (ki bu "opsiyonel" olduğu
için beklenen durum), raporları/Kasa gün sonunu/sipariş geçmişini/sipariş numarası sıfırlamasını/"şu an sipariş
alınabilir mi" kapısını hep UTC'ye göre hesaplıyordu - Europe/Istanbul için gece yarısından sonraki ~3 saatte
(veya herhangi bir an, saatlik kontroller için) yanlış gün/yanlış saat.

**Bulunan 4 ayrı gerçek hata (hepsi aynı desenin kopyaları):**
1. `ReportingService.resolveZone` (raporlar/Özet) - UTC'ye düşüyordu.
2. `OrderingService.resolveZone` (sipariş geçmişi, `getOrderHistory`) - UTC'ye düşüyordu (flake'in kaynağı).
3. `DailyCloseService.resolveZone` + `DailyCloseScheduler`'daki aynı satırın kopyası (Kasa gün sonu PREVIEW/FINAL
   snapshot'ları + zamanlayıcı) - UTC'ye düşüyordu.
4. `TenantService.assertOrderingCurrentlyAllowed` (Bölüm 9 Milestone 5'in "otoriter, ödeme öncesi son kontrol"ü
   - şu an sipariş alınabilir mi kapısı) - hiç zone bile kullanmıyordu, çıplak `LocalDate.now()`/`LocalTime.now()`
   çağırıyordu, yani sunucunun/JVM'in hangi saat diliminde çalıştığına göre keyfi davranıyordu (UTC de olabilirdi,
   Europe/Istanbul de - deploy ortamına bağlı, kod içinde hiçbir garanti yok). Bu, gerçek parayı etkileyen en
   ciddi bulgu: yanlış saat diliminde çalışan bir sunucu, gerçekten açık olan bir şubede siparişi yanlışlıkla
   reddedebilir ya da kapalı bir şubede siparişi yanlışlıkla kabul edebilirdi.

Ayrıca (5) `OrderingService.markOrderAwaitingStoreAcceptance`'taki günlük sipariş numarası sayacı
(`OrderNumberGenerator` - "per-branch, per-day") `LocalDate.now(ZoneOffset.UTC)` kullanıyordu - yani "Kasa"
ekranındaki okunabilir sipariş numarası (#1, #2, ...) her gün UTC gece yarısında değil, Europe/Istanbul'da
saat 03:00'te sıfırlanıyordu; 00:00-03:00 arası verilen ilk siparişler yeni günün #1'i yerine önceki günün
sayacına devam ediyordu.

**Bilinçli olarak DOKUNULMAYAN, benzer görünen ama farklı olan iki yer:** `ExpenseService`/
`RecurringExpenseScheduler`'daki `LocalDate.now(ZoneOffset.UTC)` kullanımları - bunlar kod içinde açıkça
belgelenmiş, kasıtlı bir ürün kararı ("templates have no branch-timezone requirement in the spec - 'dayOfMonth'
is a business-level calendar concept, not a store-closing instant"). Bu ikisi kendi içinde tutarlı ve
gerekçeli, "reports/Kasa/Özet" kapsamının (ve genel "server timezone varsayımı" hatasının) dışında - dokunulmadı.

**Düzeltme - tek doğruluk kaynağı:** `TenantService`'e `public ZoneId resolveBranchTimeZone(Branch branch)`
eklendi: branch'in kendi timezone'u varsa o, yoksa business'in `defaultTimeZone`'u (asla çıplak UTC/sunucu
varsayımı - `Business.defaultTimeZone` NOT NULL ve yazılırken IANA zone olarak validate ediliyor, yani gerçek
bir cevap her zaman var). Yukarıdaki 4 kopyalanmış `resolveZone` metodu artık bu tek metoda delege ediyor;
`assertOrderingCurrentlyAllowed` artık `LocalTime.now(zone)`/`LocalDate.now(zone)` kullanıyor (çıplak
`.now()` değil); sipariş numarası sayacı artık branch'in kendi zone'unu kullanıyor.

**Test dalgalanması ve düzeltmesi:** Bu düzeltme üretim davranışını gerçekten değiştirdiği için (branch'siz
zone artık UTC değil Europe/Istanbul), tüm test suite'i çalıştırıldığında 7 test aynı gizli varsayımla kırıldı:
`ReportingFlowIntegrationTest` (3), `DailyCloseFlowIntegrationTest` (3), kendi önceki adımımda eklediğim
`OrderControlFlowIntegrationTest` testi (1) - hepsi `LocalDate.now(ZoneOffset.UTC)`/`LocalTime.now(ZoneOffset.UTC)`
kullanıp branch'in eskiden (hatalı olarak) UTC'ye düştüğünü varsayıyordu. Ayrıca `BranchBusinessHoursFlowIntegrationTest`
(5 test) çıplak `LocalDate.now()`/`LocalTime.now()` (JVM varsayılan zone) kullanıyordu - bu makinede JVM zone'u
zaten Europe/Istanbul olduğu için testler hem eski hem yeni kodla "tesadüfen" geçiyordu, ama başka bir zone'da
çalışan bir CI sunucusunda (ör. UTC) kırılırdı. Hepsi `Europe/Istanbul` (business'in gerçek varsayılanı) açıkça
kullanacak şekilde düzeltildi - flake gizlenmedi, testler artık üretimin gerçekte kullandığı zone'u yansıtıyor.

**Yeni regresyon testleri (6):** `BranchTimeZoneResolutionIntegrationTest` (3, yeni dosya) -
`resolveBranchTimeZone`'un branch'siz durumda business default'a düştüğünü (UTC'ye değil), branch'in kendi
zone'u varsa onun kazandığını, ve yeni bir business'in gerçekten "Europe/Istanbul"a varsayılan geldiğini
saf/deterministik şekilde (saat/gün'e bağlı olmadan) kanıtlıyor. Ayrıca 3 uçtan-uca "gece yarısı sınırı" testi
(`OrderHistoryIntegrationTest`, `ReportingFlowIntegrationTest`, `DailyCloseFlowIntegrationTest`'e birer tane) -
her biri gerçek "şimdi"ye bağlı olmadan (`LocalDate.now(Europe/Istanbul)`'dan sabit bir offset'le, örn.
01:30 Europe/Istanbul = önceki gün 22:30 UTC) bir siparişin `created_at`'ini backdate edip o sipariş
Europe/Istanbul'un "bugün"üne dahil ediliyor mu diye kontrol ediyor - testin ne zaman çalıştığından bağımsız,
her zaman deterministik, orijinal flake'in yakaladığı ~1 saatlik pencereye bağımlı değil.

**Doğrulama:** Tüm backend test suite'i (158 test, önceki 152 + yeni 6) `BUILD SUCCESS` ile geçti - hiçbir
regresyon yok. Commit/push yapılmadı (kullanıcı talebi).

## Refund akışı ve staff İadeler ekranı: uçtan uca ikinci denetim

**Bulgu - istenen davranışın neredeyse tamamı önceki milestone'larda zaten doğru kurulmuş:** Kullanıcının
istediği kontrol listesi (orderedQuantity/refundedQuantity/remainingRefundableQuantity hesaplaması, yalnız
COMPLETED refund'ların sayılması, tam iade sonrası yeni refund'un engellenmesi, partial→partial akışı,
transaction seviyesinde aşım koruması, FAILED/PROCESSING'in miktarı düşürmemesi, geçmiş iade listesi, orderNumber
ile arama) `RefundService`/`RefundController`/`Payment`/`RefundFlowIntegrationTest` içinde madde madde zaten
karşılanıyordu - `RefundItemRepository.sumCompletedRefundedQuantityByOrderItem` yalnız `RefundStatus.COMPLETED`
satırlarını topluyor; `Payment.applyRefund` + `findByOrderIdAndStatusForUpdate` (`PESSIMISTIC_WRITE`) +
`OrderingService.getOrderInBranchForUpdate` üçü birlikte hem sipariş hem ödeme satırını kilitleyip aşımı
transaction içinde engelliyor; `RefundFlowIntegrationTest` zaten tam/kısmi/ikinci kısmi/duplicate/
PROCESSING-tüketmiyor/FAILED-tüketmiyor-ve-retry-edilebiliyor/eşzamanlı-çift-refund senaryolarının hepsini
(8 test) kapsıyordu; staff-web `refunds/[branchId]/page.tsx` zaten orderNumber ile arıyor, UUID'yi hiçbir yerde
göstermiyor, tam iade edilmiş kalemi disabled yapıyor, "Bu sipariş tamamen iade edildi." mesajını gösterip
butonu kapatıyor, geçmiş iadelerde tarih/tutar/ürün×adet/durum rozeti listeliyordu. Denetim iki gerçek eksik buldu:

**1) UI'da "İade yok" durumu hiç gösterilmiyordu:** Sipariş kartı yalnızca `fullyRefunded`/`partiallyRefunded`
true olduğunda bir rozet ekliyordu; hiç iade yapılmamış bir siparişte üç durumdan hiçbiri görünmüyordu (istenen
"İade yok / Kısmi İade / Tam İade Edildi" üçlüsü aslında ikiliydi). Düzeltme: `refunds/[branchId]/page.tsx`'e
else dalı olarak nötr `"İade yok"` rozeti eklendi - artık üç durum da her zaman açıkça gösteriliyor.

**2) Refund modülüne özel branch isolation regresyon testi yoktu:** `CrossTenantBranchAccessIntegrationTest`
genel branch-seçim guard'ını (`/orders/search` için 403) zaten kapsıyordu, ama "branch A'ya scope'lu bir staff,
branch B'nin gerçek bir siparişini branch B path'i üzerinden 403, branch A path'i üzerinden 404 alarak asla
göremiyor/refund edemiyor mu" senaryosu (yani `getOrderInBranchForUpdate`'in branch-mismatch kontrolünün refund
uçları için de çalıştığı) hiç test edilmiyordu. Düzeltme: `RefundFlowIntegrationTest`'e
`refundEndpointsCannotReachOrIssueRefundsForAnotherBranchsOrder` eklendi - branch B'de gerçek bir ödenmiş sipariş
oluşturup yalnız branch A'ya yetkili bir staff cookie ile hem branch B path'inden (403, StaffContext hiç
çözülmüyor) hem branch A path'inden aynı orderId ile (404, sipariş branch A'da bulunamıyor) arama/listeleme/refund
denemesi yapıyor, sonunda gerçek siparişin refundedQuantity/remainingRefundableQuantity/refunds listesinin hiç
değişmediğini doğruluyor.

**Kasıtlı olarak dokunulmayan (zaten doğru bulunan) noktalar:** Mock payment provider (`MockPaymentProviderAdapter`)
değiştirilmedi; `Payment`/`Refund` entity state machine'leri değiştirilmedi; backend'in miktar/tutar hesaplama
mantığı değiştirilmedi - denetim sırasında bulunan tek gerçek fonksiyonel eksik yukarıdaki ikisiydi.

**Doğrulama:** `RefundFlowIntegrationTest` 9/9 (önceki 8 + yeni branch-isolation testi) `BUILD SUCCESS` ile
geçti. Frontend `npx tsc --noEmit` staff-web için hatasız. Commit/push yapılmadı (kullanıcı talebi).

## PAYMENT SUCCESS → AWAITING_STORE_ACCEPTANCE → PREPARING → READY → COMPLETED: uçtan uca denetim

**Bulgu - state machine'in kendisi zaten sağlamdı:** `CustomerOrder`/`OrderItem`'ın her geçiş metodu
(`markAwaitingStoreAcceptance`/`markInKitchen`/`markReady`/`markCompleted`, `acceptFully`/`markReady`/
`markServed`) kendi ön-koşul durumunu tutarlı şekilde `IllegalStateException` ile reddediyor
(`ApiExceptionHandler` bunu 400'e çeviriyor) - geçersiz/duplicate (sequential) bir ACCEPT/REJECT/READY/COMPLETE
zaten kendiliğinden reddediliyordu, herhangi bir kod değişikliği gerekmedi. Ödeme başarılı olmadan hiçbir yol
siparişi Kasa'ya düşürmüyor (`PaymentWebhookService` yalnız `WebhookOutcome.SUCCEEDED`'da
`markOrderAwaitingStoreAcceptance`'ı çağırıyor); orderNumber `AWAITING_STORE_ACCEPTANCE`'a geçişte atanıyor ve
Kasa/customer-tracking/refund tarafında hep aynı değer, internal UUID hiçbir yerde müşteriye gösterilmiyor
(frontend her yerde `orderNumber`/`#N` kullanıyor). Branch/table isolation her mutating uçta
`requireOrderInBranch`/`getOrderInBranchForUpdate` ile zaten korunuyordu.

**Test boşluğu bulundu ve kapatıldı:** Sipariş yaşam döngüsünün hiçbir yerinde SSE'nin gerçekten event
yayınladığını doğrulayan bir test yoktu (`grep`: backend'de `SseEmitter`/`text/event-stream` geçen sıfır test).
Yeni `OrderLifecycleAuditIntegrationTest` (5 test, backend) eklendi:
`paidOrderLifecycleEmitsSseAtEveryTransitionAndReconnectAlwaysSeesCurrentState` (MockMvc'nin async desteğiyle
hem customer-tracking hem Kasa `/stream` uçlarına gerçekten abone olup ACCEPT/READY/COMPLETE'in her birinde
doğru `orderStatus` payload'ının geldiğini, ayrıca sonradan açılan bir "reconnect" stream'inin backlog
almadığını ama hemen ardından yapılan düz reload'un her zaman güncel state'i verdiğini kanıtlıyor),
`anUnpaidOrderNeverReachesTheKasaAcceptanceQueue`, `duplicateSequentialAcceptReadyAndCompleteRequestsAreRejectedWithoutChangingState`,
`outOfOrderTransitionsAreRejected` (READY/COMPLETE'i erken çağırmak), `crossBranchOrderActionsAreRejectedAsNotFound`
(accept/reject/ready/complete/refund'ın hepsi başka branch'in siparişinde 404).

**Gerçek bug - staff-web SSE reconnect sonrası state yenilenmiyordu:** `app/cashier/[branchId]/page.tsx` ve
`app/pickup/[branchId]/page.tsx`'de `EventSource`'un `"open"` handler'ı yalnızca `connectionStatus`'u
"live" yapıyordu, veri refetch etmiyordu - bağlantı düşüp (ağ kesintisi, kilitli/arka plana alınmış cihaz)
native `EventSource` kendiliğinden yeniden bağlandığında, kopukluk sırasında kaçırılan bir durum değişikliği
(backlog yok - `SseOrderStatusNotifier`'ın kendi Javadoc'unun da söylediği gibi) ekrana hiç yansımıyor, ta ki
tesadüfen başka bir event tetiklenene kadar. Customer tracking sayfası (`order/track/[token]/page.tsx`) bu
sorunu zaten `visibilitychange`'de koşulsuz `load()` ile çözmüştü; aynı düzeltme (`"open"` handler'ında da
`fetchAll()`/`fetchBoard()` çağırmak - hem ilk bağlantıda hem her reconnect'te tetiklenir) her iki staff-web
sayfasına da uygulandı.

**Doğrulama:** Yeni `OrderLifecycleAuditIntegrationTest` 5/5 geçti; `ordering`+`payment`+`refund` paketlerinin
tamamı (46 test) `BUILD SUCCESS` ile geçti, regresyon yok. staff-web `npx tsc --noEmit` ve `eslint` (değişen iki
dosya) hatasız. Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-23 Manuel Gider Yönetimi: Düzenle + Kaydı İptal Et (soft-cancel) — ✅ COMPLETED

**Eksik:** Gider listesinde satır aksiyonları hep "—" placeholder'dı; `updateExpense` API'si zaten vardı ama
hiçbir UI onu çağırmıyordu, iptal/silme için hiçbir yol yoktu. Hard delete kullanılmadı çünkü finans kaydı
audit edilebilir kalmalı.

**Backend - soft-cancel:** `Expense`'e `cancelled_at`/`cancelled_by_staff_user_id` eklendi (V31 migration);
`cancelledAt != null` iptal bayrağı. `ExpenseService#cancelManualExpense` yeni uç
(`POST /api/staff/expenses/{id}/cancel`) - `requireManualExpense` ile aynı kısıtı kullanıyor (recurring
şablondan üretilmiş satırlar zaten immutable, cancel de reddediliyor), zaten iptalliyse `IllegalStateException`
(400). `updateManualExpense` da artık iptal edilmiş bir kaydı reddediyor. `AuditService.record(...,
"CANCELLED", ...)` çağrılıyor - hard delete yok, satır ve audit trail kalıcı. `ExpenseRepository.sumManualAmount`/
`sumRecurringAmount`'a `AND e.cancelledAt IS NULL` eklendi - `expenseBreakdown` (Toplam Gider/Net Sonuç'u
besliyor) otomatik olarak iptal edilmiş kayıtları dışlıyor; `listExpenses` değişmedi, iptal edilmiş kayıt
listede kalmaya devam ediyor.

**Frontend:** `ExpenseList.tsx`'e Düzenle (mevcut `updateExpense`'i çağıran dialog, `RecurringTemplates`'teki
edit-dialog deseniyle aynı) ve Kaydı İptal Et (yeni `cancelExpense` + `ConfirmDialog`, "bu işlem geri alınamaz"
uyarısıyla) eklendi. İptal edilmiş satırda "İptal Edildi" rozeti gösteriliyor, aksiyon butonları kayboluyor.

**Doğrulama:** `ExpenseFlowIntegrationTest`'e 2 yeni test eklendi
(`cancelledManualExpenseStaysListedAndAuditedButDropsOutOfReports`,
`systemGeneratedRecurringRealizationCannotBeCancelled`) - toplam 10/10 geçti; `reporting`+`expense`+`dailyclose`
paketlerinin tamamı da regresyon olmadan geçti. staff-web `npx tsc --noEmit`, `eslint`, `next build` hatasız.
Docker image'ları (`backend`, `staff-web`) yeniden build edilip container'lar restart edildi, V31 migration
gerçek dev DB'sine uygulandı; canlı ortamda gerçek bir manuel gider düzenlendi ve iptal edildi - liste "İptal
Edildi" rozetini gösterdi, Raporlar'da Manuel Giderler ₺300→₺0 ve Toplam Giderler aynı miktarda düştü. Commit/push
yapılmadı (kullanıcı talebi).

## 2026-08-23 Personel Şifre Yönetimi: Şifremi Değiştir + Admin Şifre Sıfırla — ✅ COMPLETED

**Eksik:** StaffUser şifreleri yalnızca oluşturma anında set edilebiliyordu (`CreateStaffUserRequest`) - ne
personel kendi şifresini değiştirebiliyordu, ne de STAFF_MANAGE yetkili bir admin unutulan/sızmış bir şifreyi
sıfırlayabiliyordu.

**Tasarım kararı (kullanıcı onayıyla):** Admin'in "Şifre Sıfırla" akışında yeni geçici şifreyi sistem
üretmiyor - admin kendisi yazıyor (Personel Ekle formundaki göster/gizle input'un aynısı). Böylece API
response'una hiçbir zaman bir şifre değeri girmiyor; "log/audit/response'ta plaintext parola olmasın" kuralı
tasarım gereği hiç ihlal edilemiyor.

**Backend (`StaffAuthService`):** `PasswordPolicy.MIN_LENGTH` (8) tek merkezi sabit oldu -
`CreateStaffUserRequest`, yeni `ChangePasswordRequest`, yeni `ResetPasswordRequest` üçü de bunu referans
alıyor, ayrı bir 8 hardcode yok. `StaffUser.updatePasswordHash(...)` eklendi (deactivate'teki gibi
`updatedAt`'i de günceller, `active`'e dokunmaz). `changePassword(staffUserId, currentSessionId, ...)`:
mevcut şifreyi doğrular (yanlışsa 401), kendi oturumu hariç `StaffSessionRepository
.deleteAllByStaffUserIdAndIdNot(...)` ile diğer tüm oturumlarını düşürür. `resetPassword(businessId,
branchId, actorStaffUserId, targetStaffUserId, ...)`: `deactivateStaffUser`'daki
`findByIdAndBusinessId`+`hasEffectiveBranchAssignment` deseniyle cross-branch/cross-business reset'i 404'e
düşürür, PLATFORM_ADMIN hedefini ayrıca reddeder, kendi hesabına reset'i (`actorStaffUserId.equals(target)`)
400 ile reddeder (kendi şifreni değiştirmek için change-password kullanılmalı), hedefin tüm oturumlarını
(`deleteAllByStaffUserId`) düşürür. Her iki metot da `AuditService.record(..., "PASSWORD_CHANGED"/
"PASSWORD_RESET", Map.of())` çağırıyor - details her zaman boş, şifre hiçbir audit satırına yazılmıyor.
Yeni uçlar: `POST /api/staff/auth/change-password` (sadece geçerli oturum yeter, Permission gerekmez) ve
`POST /api/staff/staff-users/{id}/reset-password` (Permission.STAFF_MANAGE + aktif branch scoping, mevcut
`deactivate` ucuyla birebir aynı yetki deseni).

**Frontend:** `AppShell.tsx`'in sol alt kullanıcı kartına (email/rol/çıkış yanına) bir "Şifremi Değiştir"
ikon butonu + mevcut/yeni/tekrar alanlı Dialog eklendi; 401 hatası "Mevcut şifre yanlış" olarak gösteriliyor.
`app/staff/page.tsx`'teki her personel satırına (aktif/devre dışı fark etmeksizin) "Şifre Sıfırla" butonu +
yeni/tekrar alanlı Dialog eklendi. `lib/api.ts`'e `MIN_PASSWORD_LENGTH` sabiti eklendi, Personel Ekle
formundaki eski `password.length < 8` hardcode'u da bunu kullanacak şekilde güncellendi.

**Doğrulama:** Yeni `StaffPasswordManagementIntegrationTest` (9 test: doğru/yanlış mevcut şifre, eşleşmeyen
confirm, kendi oturumu hariç diğer oturumların düşmesi, yetkisiz/cross-branch reset 403/404, kendi hesabına
reset 400, disabled kullanıcıya reset sonrası login'in hâlâ kapalı kalması) + mevcut `StaffAccessFlowIntegrationTest`
(9) + `CrossTenantBranchAccessIntegrationTest` (5) - staffaccess paketinin tamamı 23/23 geçti, regresyon yok.
staff-web `npx tsc --noEmit`, `eslint` (üç değişen dosya) ve `next build` hatasız. Commit/push yapılmadı
(kullanıcı talebi).

## 2026-08-23 media_data için backup/restore (postgres-backup deseninin aynısı) — ✅ COMPLETED

**Eksik:** Go-live checklist'inde tespit edildiği üzere `media_data` volume'ü (ürün görselleri +
private gider fişleri) hiç yedeklenmiyordu, sadece Postgres yedekleniyordu.

**Uygulama:** `infra/docker/postgres-backup/`'ın birebir aynı deseninde yeni bir `infra/docker/media-backup/`
sidecar'ı (Dockerfile + entrypoint.sh + backup.sh, alpine tabanlı) eklendi - `media_data`'yı periyodik
olarak (aynı `BACKUP_INTERVAL_SECONDS`/`BACKUP_RETENTION_DAYS`) `infra/backups/media/`'ye timestamp'li
`.tar.gz` olarak arşivliyor, retention'ı aynı şekilde uyguluyor. Volume'e `:ro` bağlı - bu container hiçbir
zaman media_data'ya yazamıyor. Her iki compose dosyasına (`docker-compose.yml`, `docker-compose.prod.yml`)
`media-backup` servisi eklendi, `depends_on: backend: condition: service_healthy` ile - sadece ilk
başlangıçta bekliyor (LocalFileMediaStorageAdapter `/data/media`'yı boot'ta oluşturuyor, aksi halde taze
boş bir volume'de Alpine'in varsayılan `/media` alt dizinleri (`cdrom`/`floppy`/`usb`) ilk arşive
karışabiliyordu - testte tespit edildi), sonrasında postgres-backup gibi backend'in health'inden bağımsız
çalışmaya devam ediyor. `scripts/backup.sh` DB dump'ının hemen ardından `media-backup` container'ına
`exec` ile `media_manual_<timestamp>.tar.gz` üretecek şekilde genişletildi - mevcut Postgres akışına
dokunulmadı. Yeni `scripts/restore-media.sh` (postgres `restore.sh` ile aynı disiplinde ama volume için
tek bir "isim" olmadığından `RESTORE MEDIA` sabit metni onayı istiyor): backend+media-backup'ı durdurur,
`backend` servisinin image/volume'ünü (`media_data:/data/media`) yeniden kullanarak `find -delete` ile
mevcut içeriği siler, arşivi `tar xzf` ile açar, sonra ikisini yeniden başlatır. `.env.prod.example` ve
README'deki Backup/restore bölümü minimum güncellendi (ortak retention/interval, iki script, iki
`.tar.gz`/`.sql.gz` çıktısı); `docs/production-go-live-checklist.md`'deki "media yedeklenmiyor" maddesi
çözüldü olarak işaretlendi.

**Doğrulama:** Gerçek dev stack'te `media-backup` build edilip ayağa kaldırıldı, backend healthy olduktan
sonra gerçek `media_data`'dan (`infra_media_data`) hatasız ilk arşivini üretti; `./scripts/backup.sh`
gerçek DB + media yedeğini aynı anda üretti. `restore-media.sh` yanlış onay metniyle çalıştırıldı - hiçbir
container'a dokunmadan (backend/media-backup durumu değişmeden) 1 exit code ile iptal ettiği doğrulandı.
Restore'un dosya bütünlüğü, gerçek dev verisine dokunmadan, izole `test_media_src`/`test_media_dst` docker
volume'leriyle doğrulandı: gerçek `backup.sh` imajıyla üretilen arşiv, script'in kullandığı birebir aynı
`find -mindepth 1 -delete` + `tar xzf` komutlarıyla ayrı bir hedef volume'e geri yüklendi, hedefteki eski
içeriğin silindiği ve geri yüklenen dosyaların (`product-images/burger.jpg`, `receipts/vendor-invoice.pdf`)
kaynakla `sha256sum` eşleştiği (MATCH) doğrulandı; test volume'leri sonra silindi. Her iki `docker compose
config` (dev + prod, prod için scratch'te geçici dummy `.env.prod`) sözdizimi hatasız. Commit/push yapılmadı
(kullanıcı talebi).

## 2026-08-23 Platform Admin Panel — ✅ COMPLETED

**Amaç:** PLATFORM_ADMIN rolü bugüne kadar sadece `/internal/**` bootstrap API'siyle (shared-secret
token) kullanılıyordu; gerçek bir tarayıcı paneli yoktu. Hedef: işletme/şube/kullanıcı yönetimini
session-authenticated bir PLATFORM_ADMIN'in kendi staff-web login'i üzerinden yapabildiği yeni bir
`/api/platform-admin/**` API yüzeyi + staff-web ekranları. Onaylanan kararlar: (1) panelden yeni
PLATFORM_ADMIN oluşturulamaz veya kimse bu role yükseltilemez — PLATFORM_ADMIN oluşturma yalnız
`/internal/**` üzerinde kalır; (2) panelin rol seçenekleri sadece BUSINESS_ADMIN/BRANCH_MANAGER/CASHIER;
(3) mevcut PLATFORM_ADMIN gerçek cross-business rol olarak ele alınır — kendi `StaffUser.businessId`
değerine göre filtrelenmez, `/api/platform-admin/**` tüm işletmeleri yönetebilir; (4) normal
`/api/staff/**` branch/business isolation kurallarına dokunulmadı; (5) PLATFORM_ADMIN kendi şifresini
mevcut `/api/staff/auth/change-password` ile değiştirir, panelde başka PLATFORM_ADMIN yönetimi yok;
(6) audit log eklendi, internal token frontend'e hiç taşınmadı (browser sadece session cookie kullanır).

**Backend (tamamlandı):** `TenantService.listBusinesses()` (yeni `BusinessRepository
.findAllByOrderByNameAsc()`), `Business.activate()/deactivate()` + `TenantService.activateBusiness/
deactivateBusiness` (audit'li). `StaffUser.activate()` (reaktivasyon) ve `StaffUser.changeRole()` yeni
mutatorlar. `StaffAuthService`'e platform-admin'e özel dört metot eklendi — hepsi ortak
`requireNonPlatformAdminTarget(businessId, staffUserId)` private helper'ından geçiyor (hedef
PLATFORM_ADMIN ise `StaffPermissionDeniedException`): `activateStaffUserAsPlatformAdmin`,
`deactivateStaffUserAsPlatformAdmin` (var olan branch-scoped 2-arg overload'la isim çakışması nedeniyle
`AsPlatformAdmin` soneki zorunlu oldu — Java aynı erasure'a sahip iki overload'a izin vermiyor),
`changeStaffUserRole` (yeni rol PLATFORM_ADMIN ise de reddeder), `resetPasswordAsPlatformAdmin`
(branch şartı yok, mevcut branch-scoped `resetPassword`in aksine). Yeni `com.qrmenu.platformadmin.web`
paketi (ayrı bir modül değil - sadece `TenantService`/`StaffAuthService` public facade'lerini kullanıyor,
`ModuleBoundaryTest` bunu zaten izin veriyor çünkü sadece `.repository` paketlerine doğrudan erişimi
yasaklıyor): `PlatformAdminBusinessController` (`/api/platform-admin/businesses/**` — list/create/get/
activate/deactivate + branches list/create) ve `PlatformAdminStaffController`
(`/api/platform-admin/businesses/{businessId}/staff-users/**` — list/create/activate/deactivate/role/
reset-password). Her iki controller'da paylaşılan private `requirePlatformAdmin(sessionCookie)` guard'ı
`StaffAuthService.resolveStaffContext(sessionId)` + `context.role() != PLATFORM_ADMIN` kontrolü yapıyor
(mevcut `REPORT_CHAIN_VIEW` permission'ını "de facto platform-admin" gate'i olarak reuse etmek yerine
bilinçli olarak açık rol kontrolü seçildi — Permission enum'u business-domain aksiyonları için, panel
erişimi rol kontrolü için ayrı bir kavram). Businesses/branches DTO'ları `tenant.web.dto`'dan,
staff-user DTO'ları `staffaccess.web.dto`'dan reuse edildi (`CreateStaffUserRequest`, `ResetPasswordRequest`,
`StaffUserResponse` zaten public record); tek yeni DTO `platformadmin.web.dto.ChangeStaffUserRoleRequest`.

**Doğrulama:** Yeni `PlatformAdminFlowIntegrationTest` (6 test — businesses create/list/activate/
deactivate; PLATFORM_ADMIN'in kendi `StaffUser.businessId`'sinden başka bir işletmeyi (şube+personel
create/role-change/activate-deactivate/reset-password, gerçek login round-trip'iyle) yönetebildiği;
panelden PLATFORM_ADMIN oluşturma/yükseltme denemesinin 403 ile reddi; panelin var olan bir
PLATFORM_ADMIN hesabını hedefleyememesi (deactivate/reset-password 403); normal BUSINESS_ADMIN
oturumunun panele erişememesi 403; oturumsuz isteğin 401) + tüm proje test suite'i (43 test sınıfı,
`ModuleBoundaryTest` dahil) sıfır regresyonla yeşil. Commit/push yapılmadı (kullanıcı talebi).

**Frontend (tamamlandı):** `lib/api.ts`'e platform-admin bölümü - businesses/branches/staff-users için
`listPlatformBusinesses/createPlatformBusiness/getPlatformBusiness/activatePlatformBusiness/
deactivatePlatformBusiness/listPlatformBranches/createPlatformBranch/listPlatformStaffUsers/
createPlatformStaffUser/activatePlatformStaffUser/deactivatePlatformStaffUser/
changePlatformStaffUserRole/resetPlatformStaffUserPassword` - mevcut `Business`/`Branch`/`StaffUser`/
`StaffRole` tipleri reuse edildi (backend response şekilleri zaten birebir aynı). İki yeni sayfa:
`app/platform-admin/businesses/page.tsx` (liste + oluşturma dialog'u + satır bazlı aktif/pasif) ve
`app/platform-admin/businesses/[businessId]/page.tsx` (tek sayfada özet + şubeler + kullanıcılar -
task 5/6 ayrı ekran yerine tek detay sayfasında birleştirildi, gereksiz gezinme yaratmamak için).
Kullanıcı satırındaki "Rol Değiştir/Şifre Sıfırla/Devre Dışı Bırak" aksiyonları `user.role ===
"PLATFORM_ADMIN"` olduğunda gizlenip yerine "Bu panelden yönetilemez" metni gösteriliyor (browser
testinde backend'in zaten 403 ile reddettiği ama UI'da hâlâ tıklanabilir duran bir buton tespit edildi
- bkz. Doğrulama). `staffNav.ts`'e yeni "Platform" nav grubu (tek item: "İşletmeler" →
`/platform-admin/businesses`, `roles: ["PLATFORM_ADMIN"]`) eklendi - `Building2` ikonu (lucide-react).
Ayrı bir CSS modülü yerine mevcut `styles/admin.module.css` + `app/staff/page.module.css` deseninden
kopyalanan küçük bir paylaşımlı `app/platform-admin/platform-admin.module.css` kullanıldı.

**Doğrulama (backend):** `PlatformAdminFlowIntegrationTest` (6 test) + tüm proje test suite'i (43 sınıf,
`ModuleBoundaryTest` dahil) sıfır regresyonla yeşil.

**Doğrulama (frontend + gerçek tarayıcı):** `npx tsc --noEmit`, `eslint`, `next build` hatasız (her iki
sayfa da route tablosunda görünüyor). Docker image'ları (`backend`, `staff-web`) yeniden build edilip
`infra` stack'inde ayağa kaldırıldı. `/internal/**` ile geçici bir test PLATFORM_ADMIN (kendi
`StaffUser.businessId`'si "Browser Test Seed Business") bootstrap edilip gerçek Chrome'da uçtan uca
test edildi: (1) işletme listesi hem kendi işletmesini hem de ona ait olmayan "Meydan Bistro"yu
gösterdi (cross-business onaylandı); (2) yeni işletme oluşturma, şube ekleme, kullanıcı (CASHIER)
oluşturma, rol değiştirme (CASHIER→BRANCH_MANAGER, dropdown'da sadece BUSINESS_ADMIN/BRANCH_MANAGER/
CASHIER olduğu `read_page` ile doğrulandı - PLATFORM_ADMIN seçeneği yok), şifre sıfırlama, kullanıcı
devre dışı bırak/aktifleştir, işletme aktif/pasif toggle - hepsi gerçek tıklamalarla çalıştı; (3)
"Meydan Bistro"ya (standing PLATFORM_ADMIN `arda@qrmenu.local`'in kendi işletmesi) girildiğinde o
hesabın satırında PLATFORM_ADMIN yönetim aksiyonlarının gizlendiği (düzeltme sonrası) doğrulandı; (4)
oturum kapatıldığında `/platform-admin/businesses`'e direkt URL ile gidiş login'e yönlendirdi (401);
(5) BRANCH_MANAGER (PLATFORM_ADMIN olmayan) girişinde sol navda "Platform" grubu hiç görünmedi, aynı
hesapla direkt URL denemesi backend'den 403 alıp ErrorState + "Tekrar Dene" gösterdi, sayfa çökmedi.
Test için oluşturulan iki işletme (`Browser Test Seed Business`, `Yeni Test Şubeler A.Ş.`) panel
üzerinden pasifleştirildi (silme uç noktası yok - ürün tasarımı gereği hiçbir varlık hard-delete
edilmiyor); test PLATFORM_ADMIN hesabı (`browser-test-pa@example.com`) ve test personeli
(`test-cashier@example.com`) devre dışı bırakılmadı (panel PLATFORM_ADMIN'i deaktive edemiyor, personel
düşük risk). Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-23 Platform Admin Panel — Final Smoke Test ve Gerçek Bug Düzeltmesi

**Kapsam:** Panelin uçtan uca son doğrulaması - sıfırdan işletme/şube/BUSINESS_ADMIN oluşturma, yeni
kullanıcının gerçek staff-web login'i, rol değiştirme/şifre sıfırlama/aktif-pasif (hem işletme hem
kullanıcı seviyesinde), işletme pasifleştirmenin mevcut erişimi ve yeni operasyonları gerçekten
engelleyip engellemediği, audit kaydı doğrulaması. Gerçek Chrome + curl ile test edildi.

**Bulunan ve düzeltilen gerçek bug:** `business.isActive()` hiçbir yerde (login, session resolution,
public menu) kontrol edilmiyordu - işletmeyi panelden pasifleştirmek sadece UI badge'ini değiştiriyordu,
mevcut personel oturumları, yeni personel login'leri ve public müşteri menü uç noktası tamamen normal
çalışmaya devam ediyordu. `StaffAuthService.login()` ve `StaffAuthService.resolveStaffContext(UUID)`'a
yeni bir `requireBusinessActive(StaffUser)` kontrolü eklendi (pasif işletme için
`StaffAuthenticationRequiredException`, "Business is deactivated"); PLATFORM_ADMIN bu kontrolden muaf
tutuldu - aksi halde bir platform admin kendi ana işletmesini pasifleştirerek paneli tekrar
aktifleştiremeyecek şekilde kendini kilitleyebilirdi. Canlı doğrulandı: pasif işletmenin personeli için
hem yeni login hem var olan bir session cookie'siyle `/api/staff/auth/me` artık 401 dönüyor, işletme
tekrar aktifleştirilince normale dönüyor. İlgili mevcut entegrasyon testleri (`StaffAccessFlowIntegrationTest`,
`StaffPasswordManagementIntegrationTest`, `CrossTenantBranchAccessIntegrationTest`) sıfır regresyonla
yeşil; backend Docker image'ı yeniden build edilip container restart edildi.

**Bilinçli olarak düzeltilmeyen, kapsam dışı bırakılan yan:** Public müşteri tarafı (menü görüntüleme,
sipariş verme - `PublicMenuController`, ordering/cart akışı) hâlâ işletmenin aktiflik durumunu kontrol
etmiyor; pasif bir işletmenin şubesi için public menu uç noktası hâlâ 200 dönüyor. Bunun nasıl davranması
gerektiği (QR taramasını tamamen mi engellemeli, "kapalı" sayfası mı göstermeli, var olan sepetlere mi
izin vermeli) ürün kararı gerektiriyor - bu oturumda dokunulmadı, sadece raporlandı.

**Raporlanan, düzeltilmeyen iki eksik (ürün kararı/kapsam gerektiriyor):**
1. Şube düzenleme veya pasifleştirme hiçbir yerde yok - ne UI'da ne backend'de (`PlatformAdminBusinessController`
   sadece şube list/create içeriyor). Bu günlük operasyonel bir ihtiyaç olarak eksik kaldı.
2. `TenantService.createBusiness/createBranch` ve `StaffAuthService.createStaffUser` hiç audit kaydı
   yazmıyor - sadece durum değişikliği aksiyonları (aktif/pasif, rol, şifre) audit'e düşüyor, oluşturma
   olayları audit trail'de tamamen görünmez.

**Test verisi:** "Smoke Test İşletmesi" + "Merkez Şube" + `smoketest-ba@example.com` oluşturuldu, oturum
sonunda üçü de pasif/devre dışı bırakıldı (var olan diğer test işletmeleriyle tutarlı). Commit/push
yapılmadı (kullanıcı talebi).

## 2026-08-23 Platform Admin — kalan 3 eksiğin kapatılması (şube yönetimi, pasif işletme/şube müşteri
davranışı, audit genişletme)

**Kapsam:** Bir önceki oturumda raporlanıp bilinçli olarak ertelenen 3 eksik: (1) şube düzenleme +
aktif/pasif (aktif siparişi olan şube pasife alınamaz), (2) pasif işletme/şube'nin yeni QR check-in,
yeni sipariş ve ödemeyi 503 ile engellemesi (geçmiş sipariş tracking/receipt etkilenmeden), (3)
işletme/şube/kullanıcı oluşturma + şube düzenleme/aktif-pasif'in audit'e yazılması. Kullanıcı onayı
sonrası iki düzeltme uygulanarak devam edildi: (a) aktif sipariş tanımı `OrderingService` içinde tek bir
`ACTIVE_ORDER_STATUSES` sabitinde (AWAITING_PAYMENT/AWAITING_STORE_ACCEPTANCE/IN_KITCHEN/READY) toplandı,
başka yerde hardcode edilmedi; (b) "aktif sipariş kontrolü + deactivate" ile "yeni ödeme" arasındaki race
condition, `Branch` satırında `PESSIMISTIC_WRITE` kilidi paylaştırılarak (yeni
`BranchRepository.findByIdAndBusinessIdForUpdate`, hem `TenantService.assertOrderingCurrentlyAllowed` hem
de yeni `PlatformAdminBranchService.deactivateBranch` bu kilidi alıyor) transactionally güvenli hale
getirildi - iki taraf da aynı satırda serialize olduğu için ikisinin de "başarılı" olduğu tutarsız bir
durum imkansız.

**Backend - şube yönetimi:** `Branch`'e `orderingEnabled`'dan bağımsız yeni bir `active` alanı (migration
`V32__branch_active.sql`), `rename/activate/deactivate` metodları. `TenantService`: `updateBranchInfo`,
`activateBranch` (kilitsiz - reaktivasyonun hiçbir çakışma riski yok), `getBranchForUpdate` +
`deactivateLockedBranch` (kilitli çift, sadece orkestratör tarafından kullanılıyor). Yeni
`PlatformAdminBranchService` (platformadmin modülünde - tenant modülü ordering'e bağımlı olamaz, mevcut
"OrderControlController ordering+refund'u orkestre eder" desenindeki gibi cross-module orkestrasyon
burada yapılıyor): branch'i kilitler → `OrderingService.hasActiveOrders` kontrolü → aktif sipariş varsa
yeni `BranchHasActiveOrdersException` (409), yoksa deactivate. `PlatformAdminBusinessController`'a
`PUT .../branches/{branchId}` (edit) + `.../activate` + `.../deactivate` eklendi.

**Backend - pasif işletme/şube müşteri engeli:** Yeni `BusinessUnavailableException` → 503 (409'dan
kasıtlı olarak ayrı status - customer-web'in "kapalı/saat dışı" ile "deaktive edilmiş"i ayırt edebilmesi
için). `TenantService.assertBusinessAndBranchActive` üç noktada çağrılıyor: `resolveActiveQrToken` (yeni
check-in), `OrderingService.addItem` (yeni sipariş/sepete ekleme), `assertOrderingCurrentlyAllowed`
(ödeme - aynı zamanda branch kilidini de alan tek metod). Sipariş tracking/receipt (`getOrderTrackingView`
vb.) bu kontrole hiç dokunmuyor, kasıtlı olarak.

**Backend - audit genişletme:** `TenantService.createBusiness/createBranch` ve
`StaffAuthService.createStaffUser`'a `actorStaffUserId` parametresi eklendi (internal bootstrap
çağrılarında `null` - zorunlu değil), üçü de artık `CREATED` audit kaydı yazıyor. Şube edit/aktif/pasif
de audit'e giriyor. Not: `AuditLogEntry.branch_id` kolonu bir DB trigger'ı (`assign_audit_branch_from_staff`,
V25) tarafından dolduruluyor ve **PLATFORM_ADMIN aktörlerini kasıtlı olarak hariç tutuyor** - yani
platform admin'in yaptığı hiçbir aksiyon (yeni Branch aksiyonları dahil, önceden var olan Business/
StaffUser aksiyonları gibi) branch-scoped "Denetim Kaydı" ekranında görünmüyor; bu mevcut/kasıtlı bir
tasarım, benim eklediğim bir regresyon değil - canlı Postgres sorgusu ve backend testiyle doğrulandı.

**Backend testleri (13 yeni, hepsi yeşil):** `PlatformAdminBranchManagementIntegrationTest` (4: edit/
activate/deactivate, aktif siparişli branch'in deactivate'inin 409 ile reddi, create/edit/toggle audit
kayıtları, concurrent deactivate-vs-payment-start race testi - `ExecutorService` + `CountDownLatch` ile
gerçek Postgres testcontainer üzerinde, 5 kez ardışık koşturulup flake olmadığı doğrulandı) +
`PlatformAdminCustomerAccessIntegrationTest` (5: pasif şube/işletme yeni check-in'i 503 ile reddediyor,
zaten check-in olmuş bir visit için yeni sepet öğesi 503, DRAFT sepetli branch deactivate edilebiliyor
ama sonra ödeme 503, terminal duruma ulaşmış (REJECTED_BY_STORE) bir siparişin tracking/receipt'i branch
deactive olduktan sonra da 200 dönüyor). Tüm proje test suite'i de koşturuldu: 2 pre-existing/ilgisiz
başarısızlık tespit edildi (`BulkAssignBranchesFlowIntegrationTest#businessAdminCanAssignOnlyToActiveBranch`,
`BranchBusinessHoursFlowIntegrationTest#anOvernightWindowFromYesterdayStillAllowsOrderingJustAfterMidnight`)
- `git stash` ile bu oturumun değişiklikleri geçici olarak geri alınıp aynı iki test orijinal (main)
kodda da başarısız bulunarak benim değişikliklerimle ilgisiz oldukları doğrulandı, sonra stash geri
uygulandı. Düzeltilmedi (kapsam dışı).

**Frontend:** staff-web `lib/api.ts`'e `Branch.active` alanı + `updatePlatformBranchInfo/
activatePlatformBranch/deactivatePlatformBranch`; işletme detay sayfasına şube "Düzenle" dialog'u +
Durum sütunu + aktif/pasif buton (409 aldığında "Şubede devam eden bir sipariş olduğu için pasife
alınamadı." toast'ı). customer-web `page.tsx`/`PaymentSheet.tsx`'teki mevcut status-koduna-göre-mesaj
deseni (`error.status === 409/410` zaten vardı) genişletilip 503 için "Bu işletme/şube şu anda hizmet
vermiyor." eklendi (check-in, sepete ekleme, ödeme başlatma, ödeme retry - 4 nokta). `page.test.tsx`'e 3
yeni unit test (check-in/sepet/ödeme 503 senaryoları, menü/sepetin görünür kalması). Tüm customer-web
(33 test) ve staff-web lib testleri yeşil, her iki frontend `tsc --noEmit` temiz.

**Canlı doğrulama:** Backend + staff-web Docker image'ları yeniden build edilip (staff-web Dockerfile'ın
`COPY --from=build /app/public ./public` adımı boş bir `public/` klasörü beklediği ama repoda hiç
olmadığı - önceden var olan, bu oturumla ilgisiz bir sorun - fark edildi; yerel olarak boş `public/`
klasörü oluşturularak build'i açığa çıkarıldı, commit edilmedi) container'lar restart edildi, migration
V32 otomatik uygulandı. Gerçek Chrome'da PLATFORM_ADMIN (`arda@qrmenu.local`) ile "Meydan Bistro"nun tek
şubesi üzerinde Düzenle → Kaydet, Pasifleştir → Aktifleştir gerçek tıklamalarla test edildi (doğru toast
mesajları, durum rozetleri anında güncellendi), sonra şube tekrar Aktif duruma geri alındı. Audit
yazma tarafı doğrudan `psql` ile teyit edildi. Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-23 Full test suite'teki 2 pre-existing başarısızlığın kök neden analizi ve düzeltilmesi

**Kapsam:** Bir önceki oturumda full suite'te bulunan ve baseline'da (git stash ile main'e dönülüp)
de başarısız olduğu doğrulanan 2 testin kök nedeni araştırıldı (`superpowers:systematic-debugging`
süreciyle). Biri gerçek production bug, diğeri saf test flake'i çıktı - ikisi de düzeltildi, başka
feature/refactor yapılmadı, commit/push yapılmadı.

**1) `BulkAssignBranchesFlowIntegrationTest#businessAdminCanAssignOnlyToActiveBranch` - GERÇEK BUG:**
Kök neden `StaffContext.activeBranchId()`'de: `role == PLATFORM_ADMIN && branchIds.size() == 1` özel
durumu sadece PLATFORM_ADMIN'in TAM OLARAK bir şubesi olduğunda çalışıyordu; 0 veya 2+ şubede genel
"Staff user must have exactly one active branch" exception'ına düşüyordu (→ 403). Ama
`StaffAuthService.createStaffUser` PLATFORM_ADMIN'e oluşturulduğu anda işletmenin **tüm** şubelerini
otomatik atıyor (bkz. bu dosyadaki daha önceki Platform Admin oturumları) - yani "birden fazla şubeli
bir işletmenin platform admin'i" tam olarak NORMAL/beklenen durum, ve bu durumda
`resolveStaffContextForActiveBranch` kullanan HER staff-web endpoint'i (menu bulk-assign, business
settings, branch listeleme vb.) gerçek kullanımda spurious 403 üretiyordu - test verisi hatası değil,
gerçek bir production bug. Düzeltme: `activeBranchId()` artık PLATFORM_ADMIN için branch sayısına
bakmaksızın (boş olmadığı sürece) ilk branch'i döndürüyor - PLATFORM_ADMIN zaten `canAccessBranch()`'te
her şubeye erişebiliyor ve gerçek cross-business işler her zaman path'ten gelen açık businessId/branchId
ile yapılıyor (`PlatformAdminBusinessController`), bu metodun döndürdüğü değer sadece "bir" resolvable
branch olarak kullanılıyor.

**2) `BranchBusinessHoursFlowIntegrationTest#anOvernightWindowFromYesterdayStillAllowsOrderingJustAfterMidnight`
- FLAKY TEST (saat bağımlı, production bug değil):** Kök neden testin fixture kurgusunda:
`TenantService.isWithinYesterdaysOvernightCarryOver` bir günün "gece yarısını aşan" pencere olduğunu
salt `opening.isAfter(closing)` (takvim farkındalığı olmayan düz LocalTime karşılaştırması)
ile anlıyor. Test, dünün closing saatini gerçek `LocalTime.now(Europe/Istanbul).plusMinutes(5)`'ten
türetirken opening'i sabit `23:00` bırakıyordu - gerçek saat İstanbul'da 22:55-24:00 arasına
girdiğinde (bu oturum boyunca test tam da bu aralıkta - 23:1x'ten 23:4x'e - koşturuldu) closing artık
23:00'ü geçiyor, `opening.isAfter(closing)` matematiksel olarak yanlış çıkıyor, fonksiyon `false`
dönüyor, bugünün kapalı satırı devreye girip 409 üretiyordu; test 201 bekliyordu. Bu üretim kodunun
kendisinde bir hata değil - gerçek bir "18:00-02:00" gibi yapılandırılmış overnight pencere için
mantık doğru çalışıyor (aynı dosyadaki `staffCanSaveAndReadBackAnOvernightHoursWindow`,
`todaysScheduleTakesOverOnceYesterdaysOvernightWindowHasEnded` testleri hâlâ yeşil ve saatten bağımsız
sağlam - ayrıca doğrulandı). `TenantService`'te bir Clock/saat enjeksiyonu olmadığından (bu oturumda
eklenmedi - kapsam dışı refactor), testi TAM deterministik yapmak matematiksel olarak imkansız (LocalTime
karşılaştırması takvim-farkındalıksız kaldığı sürece), ama pratik olarak neredeyse tamamen ortadan
kaldırılabilir: sabit "opening" değeri `23:00`'dan mümkün olan en geç temsil edilebilir saate
(`LocalTime.of(23, 59, 59)`) çekilerek riskli pencere ~65 gerçek dakikadan ~1 saniyeye indirildi (yalnızca
"now" tam olarak 23:55'in bir saniye altındayken, yani closing=now+5dk'nın 23:59:59'u aşıp gece yarısını
henüz sarmadığı o bir saniyelik anda hâlâ teorik bir risk var - pratikte hiç yakalanamayacak kadar dar).

**Doğrulama:** Her iki düzeltme de gerçek saat 23:49 (önceden bozuk olan pencerenin tam içinde) iken
tekrar koşturulup yeşil olduğu doğrulandı; overnight testi ayrıca 5 kez daha ardışık koşturulup
kararlı olduğu teyit edildi. Tüm proje test suite'i: **42 test sınıfı, 200 test, 0 hata, 0 failure,
0 skip - tamamen yeşil.** Başka hiçbir feature/refactor yapılmadı. Commit/push yapılmadı (kullanıcı
talebi).

## 2026-08-24 İki düzeltmenin production-öncesi sağlamlaştırılması

**Kapsam:** Bir önceki oturumdaki iki düzeltme, kullanıcı talebiyle daha sağlam hale getirildi -
biri "belirsiz branch seçimi" riskini tamamen ortadan kaldıracak şekilde, diğeri gerçek bir
`Clock` enjeksiyonuyla matematiksel olarak tam deterministik olacak şekilde. Başka feature/refactor
yapılmadı, commit/push yapılmadı.

**1) `StaffContext.activeBranchId()` - "ilk branch'i otomatik seç" yaklaşımı kaldırıldı:** Bir
önceki oturumdaki düzeltme PLATFORM_ADMIN için `branchIds`'ten rastgele/ilk elemanı seçiyordu - bu,
gerçekten branch-scoped bir işlem yapan (ör. `StaffTenantController`, `StaffUserController`,
`OrderControlController`, `RefundController`, `StaffReportingController`, `StaffDailyCloseController`,
`StaffExpenseController`, `AuditController` - hepsi `context.activeBranchId()`'i doğrudan gerçek bir
şube kapsamlaması olarak kullanıyor) bir endpoint'e çok-şubeli bir PLATFORM_ADMIN isteği geldiğinde
sessizce YANLIŞ/keyfi bir şubeyi kullanmasına yol açabilirdi. Düzeltme: `activeBranchId()` artık
role farkı gözetmeksizin herkes için "tam olarak bir branch yoksa reddet" davranışına döndürüldü (özel
PLATFORM_ADMIN dalı komple kaldırıldı - önceki hâli zaten sadece branchIds.size()==1 durumunda anlamlı
bir şey yapıyordu, fonksiyonel olarak ölü koddu). Gerçek çözüm bunun yerine
`StaffAuthService.resolveStaffContextForActiveBranch`'e taşındı: PLATFORM_ADMIN için branch
çözümlemesini/doğrulamasını hiç DENEMEDEN (aktifBranchId() hiç çağrılmadan) context'i doğrudan
döndürüyor - böylece PLATFORM_ADMIN'i açıkça bypass eden endpoint'ler (ör.
`StaffMenuController.bulkAssignBranches`'in `ALL_BRANCHES` dalı, ki zaten `context.activeBranchId()`'i
hiç çağırmıyor) sorunsuz çalışırken, gerçekten tek bir şubeye ihtiyaç duyan endpoint'ler bir
PLATFORM_ADMIN çok şubeli çağırdığında artık sessizce yanlış şubeyi kullanmak yerine AÇIKÇA
(`StaffPermissionDeniedException` → 403) reddediyor. Platform panelinin kendisi (`PlatformAdminBusinessController`/
`PlatformAdminStaffController`) zaten hiç `resolveStaffContextForActiveBranch`/`activeBranchId()`
kullanmıyor (her zaman path'ten explicit businessId/branchId alıyor), dolayısıyla hiç etkilenmedi.

**2) Overnight carryover testi - gerçek `Clock` enjeksiyonu ile tam determinizm:** Önceki oturumun
"opening'i 23:59:59'a çek" workaround'u kaldırıldı. `TenantService`'e bir `java.time.Clock` alanı
eklendi (constructor injection), `isWithinConfiguredBusinessHours` artık `LocalDate.now(zone)`/
`LocalTime.now(zone)` yerine `LocalDate.now(clock.withZone(zone))`/`LocalTime.now(clock.withZone(zone))`
kullanıyor - production'da yeni `TenantClockConfig`'in sağladığı `Clock.systemUTC()` bean'i ile
davranış birebir aynı (aynı gerçek an, sadece zone reinterpretasyonu - `Clock.withZone` zaten
`LocalDate/LocalTime.now(zone)`'un içeride yaptığı şeyin ta kendisi). Flaky testin kendisi
`BranchBusinessHoursFlowIntegrationTest`'ten çıkarılıp yeni `BranchOvernightCarryoverIntegrationTest`'e
taşındı - bu yeni sınıf `@TestConfiguration` + `@Import` + `@Primary` ile `Clock` bean'ini
`Clock.fixed(sabit-an, Europe/Istanbul)` ile değiştiriyor (ayrı bir Spring context, ama
`AbstractIntegrationTest`'in aynı statik Postgres container'ını paylaşıyor), fixture'ı da bu SABİT
ana göre kuruyor (artık gerçek `LocalTime.now()` hiç kullanılmıyor) - artık gerçek saatten tamamen
bağımsız, matematiksel olarak %100 deterministik. Diğer testler (`BranchBusinessHoursFlowIntegrationTest`'in
kalan 8'i) hâlâ gerçek `Clock.systemUTC()` bean'ini kullanıyor, hiç dokunulmadı - production davranışı
değişmedi, sadece TAM OLARAK bu bir testin zaman kaynağı değişti.

**Doğrulama:** Yeni `BranchOvernightCarryoverIntegrationTest` 6 kez ardışık koşturuldu (hepsi yeşil,
gerçek saat 23:xx'ten 00:xx'e geçmesine rağmen fark etmedi - beklenen, artık saatten bağımsız).
`BulkAssignBranchesFlowIntegrationTest`, `StaffAccessFlowIntegrationTest`,
`CrossTenantBranchAccessIntegrationTest`, `PlatformAdminFlowIntegrationTest`,
`PlatformAdminBranchManagementIntegrationTest` (concurrent race testi dahil, 3 kez daha koşturuldu),
`PlatformAdminCustomerAccessIntegrationTest` tekrar çalıştırılıp hepsi yeşil. Tüm proje test suite'i:
**43 test sınıfı, 200 test, 0 hata, 0 failure, 0 skip - tamamen yeşil.** Başka feature/refactor
yapılmadı. Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-24 Expense → Reports bug: kök neden ve düzeltme

**Kök neden araştırması:** Backend zinciri (`ExpenseRepository.sumManualAmount`/`sumRecurringAmount` →
`ExpenseService.expenseBreakdown` → `StaffReportingController#operatingResult`) gerçek datayla
(branch-manager, cashier, PLATFORM_ADMIN rolleriyle) uçtan uca defalarca test edildi - manuel ve
gerçekleşmiş recurring giderler her seferinde doğru toplandı, JPQL filtreleri (`cancelledAt IS NULL`,
`sourceTemplateId IS NULL/NOT NULL`, `incurredAt BETWEEN`) hatasız çalıştı. Asıl kök neden backend'de
değil, frontend'deydi: `frontend/staff-web/app/expenses/features/ExpenseForm.tsx` (yeni gider
`incurredAt` varsayılanı), `ExpenseList.tsx` (Gider Listesi'nin varsayılan tarih filtresi) ve
`RecurringTemplates.tsx` (şablon "sonraki tarih" hesaplaması), `lib/time.ts`'in kendi doc-comment'inin
açıkça yasakladığı `localIsoDate()` (cihazın yerel tarihi) kullanıyordu - hâlbuki Özet/Kasa/Raporlar
ekranları bu tam sınıf bug için daha önce zaten `branchIsoDate(activeBranchTimeZone)`'a geçirilmişti
(bkz. `lib/time.test.ts`'in "Özet/Kasa/Raporlar" regresyon testleri). Sonuç: personelin cihazı
şubenin saat dilimiyle (Europe/Istanbul) aynı değilse, "Gider Ekle" formu gideri yanlış takvim
gününe kaydediyor, Gider Listesi de kendi içinde tutarlı biçimde (yine cihaz-yerel) o günü
gösterdiği için gider ekranda görünüyor - ama şube saat dilimine göre doğru filtreleyen Raporlar/
"Yönetimsel Net Sonuç" o günü farklı hesapladığı için gideri dışarıda bırakıyordu.

**Düzeltme:** `ExpensesPage` artık zaten sahip olduğu `me()` sonucundan `activeBranchTimeZone`'u üç
alt bileşene (`ExpenseForm`, `ExpenseList`, `RecurringTemplates`) prop olarak geçiriyor; üçü de
`localIsoDate()` yerine `branchIsoDate(branchTimeZone)` kullanacak şekilde güncellendi (ExpenseList,
`me()` henüz dönmeden ilk render'da cihaz-yerel yer tutucuyla açılıp branchTimeZone geldiğinde bir kez
yeniden çapalanıyor - Raporlar sayfasındaki aynı desen). Backend'de değişiklik yapılmadı (zaten doğru
çalışıyordu).

**Regresyon testi:** Bu app'te component render test altyapısı yok (sadece `node --test` ile saf
fonksiyon testleri, bkz. `lib/time.test.ts`). Yeni `app/expenses/features/expenseTimeZone.test.ts`
üç dosyanın kaynağını okuyup `branchIsoDate` kullandığını ve `localIsoDate`'i hiç çağırmadığını
doğruluyor - `lib/time.ts`'in "expense dates" için `localIsoDate` yasağını doğrudan kod seviyesinde
uygulayan bir muhafız. `package.json`'daki `test` script'i bu yeni dosyayı da kapsayacak şekilde
genişletildi. `npm test`: **8/8 yeşil.** `tsc --noEmit`: hatasız. Canlı tarayıcıda doğrulandı
(branch-manager ile giriş, Giderler sayfası varsayılan tarih filtresi ve "Yeni Gider" diyaloğunun
tarih alanı artık cihaz saatinden bağımsız, şubenin (Europe/Istanbul) o anki takvim gününü
gösteriyor; konsol hatası yok). Reprodüksiyon sırasında oluşturulan iki test gideri (`TestVendor`,
`BizLevel`) iptal edilerek gerçek veri temizlendi. Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-24 PLATFORM_ADMIN scope daraltma

**Kök mekanizma:** `StaffRole.permissions()` (backend, `StaffRole.java`) PLATFORM_ADMIN için
`EnumSet.allOf(Permission.class)` döndürüyordu - yani her normal-staff `Permission`'a otomatik
sahipti. Bütün normal işletme operasyonu endpoint'leri (Kasa/sipariş, menü, masalar, giderler,
raporlar, refund, branch/business ayarları, business-scoped personel yönetimi) tek bir noktadan,
`StaffAuthService.requirePermission`/`resolveStaffContext(sessionId, Permission)` üzerinden
`Permission` kontrolüyle korunuyor - PLATFORM_ADMIN'in "her Permission'a sahip olması" bu
gate'lerin hepsinden sessizce geçmesi anlamına geliyordu (dolaylı erişim, kullanıcının
bahsettiği tam olarak bu). `/api/platform-admin/**` paneli (`PlatformAdminBusinessController`,
`PlatformAdminStaffController`) ise hiç `Permission` kontrolü yapmıyor - sadece
`context.role() == PLATFORM_ADMIN` diye rol bazlı kontrol ediyor; `/api/staff/auth/me`,
`/change-password`, `/logout` de permission gerektirmiyor (sadece oturum). Bu nedenle tek satırlık
değişiklik - PLATFORM_ADMIN için `EnumSet.noneOf(Permission.class)` - cerrahi ve eksiksiz: platform
paneli ve PLATFORM_ADMIN'in kendi hesabı (me/şifre/çıkış) hiç etkilenmeden, her normal işletme
operasyonu endpoint'i artık PLATFORM_ADMIN'i 403 ile reddediyor. `StaffContext.canAccessBranch`'in
PLATFORM_ADMIN kısayolu bilinçli olarak dokunulmadan bırakıldı - tüm çağrı noktaları zaten bir
Permission kontrolünün ardından geliyor (artık PLATFORM_ADMIN için hiç ulaşılamıyor), sadece
`/me`'nin kozmetik şube listesinde kullanılıyor.

**Etkilenen mevcut testler (PLATFORM_ADMIN'in artık YAPAMAYACAĞI şeyleri doğruluyorlardı, negatif
teste çevrildi):** `ChainComparisonFlowIntegrationTest` (PLATFORM_ADMIN artık `/api/staff/branches/
comparison`'da BUSINESS_ADMIN ile aynı şekilde 403 alıyor - artık hiçbir staff-web rolünün erişimi
yok), `ReportingFlowIntegrationTest` (`/api/staff/reports/chain` PLATFORM_ADMIN için de 403),
`BulkAssignBranchesFlowIntegrationTest` (PLATFORM_ADMIN'in eski ALL_BRANCHES menü ataması istisnası
artık erişilemez, 403). Yeni `PlatformAdminScopeIntegrationTest` eklendi: tek testte Kasa/sipariş,
refund, menü, masalar, giderler, raporlar (branch + chain + kitchen-summary + comparison), branch/
business ayarları, business-scoped personel yönetimi, audit - hepsi PLATFORM_ADMIN için 403; ayrı
bir testte `/me`, `/api/platform-admin/businesses`, `/api/platform-admin/businesses/{id}/staff-users`
hâlâ 200 döndüğü doğrulanıyor.

**Frontend (`staffNav.ts`/`AppShell.tsx`/`page.tsx`):** `NAV_GROUPS`'taki her item'dan
`"PLATFORM_ADMIN"` çıkarıldı - sadece "Platform" grubundaki "İşletmeler" linki kaldı (artık
kimsenin erişemediği "Zincir Raporları"/"Şube Karşılaştırma" item'ları da silindi). AppShell'in
sidebar footer'ı (e-posta, rol, şifre değiştir, çıkış) zaten role bakılmaksızın her zaman
gösteriliyordu - dokunulmadı. Login sonrası yönlendirme rol bazlı ayrıldı: normal roller hâlâ
`/dashboard`'a, PLATFORM_ADMIN artık doğrudan `/platform-admin/businesses`'e gidiyor (aksi halde
/dashboard'da art arda 403'lerle karşılaşırdı). Bu, projenin var olan felsefesiyle birebir uyumlu
(`staffNav.ts`'in kendi yorumu: "bu yalnızca 403'e gidecek bir linki gizleme niceliğidir, gerçek
yetkilendirme backend'de kalır") - dolayısıyla PLATFORM_ADMIN doğrudan URL'ye giderse (ör.
`/expenses`) sayfa çökmüyor, sadece "Bir şeyler ters gitti" hata durumunu gösteriyor.

**Doğrulama:** Backend - hedefli testler + tüm suite (`./mvnw test`) çalıştırıldı: **44 test sınıfı,
202 test, 0 hata, 0 failure, 0 skip - tamamen yeşil.** Frontend - `tsc --noEmit` hatasız, `npm test`
8/8 yeşil (Expense zaman dilimi testleri dahil, etkilenmedi). Backend + staff-web docker imajları
yeniden build edilip container'lar yeniden başlatılarak canlı doğrulandı: PLATFORM_ADMIN
(`arda@qrmenu.local`) ile giriş → doğrudan Platform Admin panosuna düşüyor, sidebar'da sadece
"İşletmeler" + hesap alanı var; `curl` ile `/api/staff/expenses` ve `/api/staff/reports` 403,
`/api/staff/auth/me` ve `/api/platform-admin/businesses` 200. `branch-manager@qrmenu.local` ile
giriş yapılıp normal rollerin nav'ının (Özet/Kasa/Siparişler/Raporlar/Giderler/İadeler) hiç
değişmediği doğrulandı - regresyon yok. Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-24 Dev verisini production-like kuruluma sıfırlama

**Amaç:** Dev DB'de birikmiş test/smoke verisini (birden fazla smoke-test işletmesi, inactive test
personeli, eski sipariş/ödeme/gider/rapor kayıtları) temizleyip gerçek kullanılacak hesaplarla
(`arda@qrmenu.local` PLATFORM_ADMIN, `branch-manager@qrmenu.local`, `cashier@qrmenu.local` - hepsi
Meydan Bistro/Arabica Bahçelievler altında) production'a yakın, gerçek UI/API akışlarından üretilmiş
bir veri seti kurmak. Kullanıcı onayı öncesi FK grafiği migration'lardan (`REFERENCES` taramasıyla)
çıkarıldı; silme/koruma kapsamı ve üretilecek veri seti onaylandıktan sonra uygulandı.

**Önce yedek:** `docker exec infra-postgres-1 pg_dump -Fc` ile tam DB dump'ı alınıp
scratchpad'e kopyalandı (geri dönüş için).

**Temizlik (tek transaction, `TRUNCATE` + hedefli `DELETE`):** Operasyon tabloları global olarak
boşaltıldı - `order_item_option, refund_item, payment_webhook_event, refund, payment, order_item,
customer_order, branch_daily_order_sequence, table_visit, anonymous_customer_session,
table_qr_token, restaurant_table, branch_product, product_option, product_allergen,
product_option_group, product, menu_category, expense, recurring_expense_template,
expense_category, owner_notification_log, daily_branch_close_report, audit_log_entry,
outbox_event, staff_session, business_contact, branch_business_hours`. Ardından üç eski
smoke/browser-test işletmesi (Browser Test Seed Business, Smoke Test İşletmesi, Yeni Test Şubeler
A.Ş.) `staff_user_branch` → `staff_user` → `branch` → `business` sırasıyla tamamen silindi (FK
bütünlüğü nedeniyle bu sıra zorunlu - önce operasyon verisi boşaltılmadan bu personel/business
satırları silinemezdi, çünkü expense/audit_log_entry gibi tablolar onlara referans veriyordu).
Transaction sonunda kalan `business`/`branch`/`staff_user` sayılarını doğrulayan bir `DO` bloğu
(1/1/3 bekleniyor) eklendi - beklenmedik bir sayı olsaydı `RAISE EXCEPTION` ile tüm transaction
otomatik geri alınacaktı. `backend/data/media` zaten boştu, ek dosya temizliği gerekmedi.

**Sonuç doğrulandı:** `business`=1 (Meydan Bistro), `branch`=1 (Arabica Bahçelievler),
`staff_user`=3 (`arda@qrmenu.local` PLATFORM_ADMIN, `branch-manager@qrmenu.local`,
`cashier@qrmenu.local`, üçü de `active=true`), tüm operasyon/rapor tabloları 0 satır.
Commit/push yapılmadı (kullanıcı talebi) - bu bir DB veri işlemi, kod değişikliği yok.

**Sıradaki adım:** `branch-manager@qrmenu.local` ile staff-web'e giriş yapıp gerçek UI/API
akışlarıyla kahve dükkânı menüsü, çalışma saatleri, masa/QR, giderler, siparişler ve
refund senaryolarını oluşturmak (DB'ye elle veri basılmayacak).

### Production-like veri seti kuruldu (gerçek REST API akışlarıyla, DB'ye elle yazım yok)

**Yöntem:** UI yerine backend REST API'ye doğrudan `curl` ile, gerçek staff-session/customer-session
akışları üzerinden gidildi (aynı endpoint'ler UI'nin kullandığı endpoint'ler - "gerçek API akışı"
kapsamında, DB seed değil). Ekran doğrulaması için sonda staff-web + customer-web tarayıcıda
kontrol edildi.

**Eksik rol bulgusu:** Menü/şube/QR/business-contact yönetimi `Permission.MENU_MANAGE` /
`BRANCH_MANAGE` / `QR_MANAGE` / `BUSINESS_SETTINGS_MANAGE` gerektiriyor - bunların hepsi yalnızca
`BUSINESS_ADMIN` rolünde var (`StaffRole.java`). Korunan 3 hesaptan hiçbiri (`PLATFORM_ADMIN` artık
sıfır permission, `BRANCH_MANAGER`/`CASHIER`'da bu izinler yok) bu işlemleri yapamıyordu. Bu yüzden
`/internal/businesses/{id}/staff-users` ile yeni bir `business-admin@qrmenu.local` (BUSINESS_ADMIN,
`Test1234!`, Arabica Bahçelievler'e bağlı) hesabı oluşturuldu - kod yorumundaki "PLATFORM_ADMIN'in
işletme işlemleri için ayrı bir BUSINESS_ADMIN/BRANCH_MANAGER hesabı kullanması" beklentisiyle
birebir uyumlu. Sipariş/refund/gider akışları için `branch-manager@qrmenu.local` kullanıldı.

**Kurulan veri:** Çalışma saatleri (veri girişi sırasında geçici olarak 7/24 açıldı, sonda gerçek
saatlere - Pzt-Per 08-22, Cum-Cts 08/09-23, Paz 09-21 - geri alındı, gerçek saat o an açık
saatlerin dışındaydı ve sipariş akışı `assertOrderingCurrentlyAllowed` ile buna bağlı olduğu için
gerekliydi); 1 business contact; 4 menü kategorisi, 13 ürün (görselli, alerjen etiketli,
Latte/Iced Latte'de opsiyon grupları - süt tipi/boy) hepsi şubede `AVAILABLE`; 5 masa + QR token;
5 gider kategorisi, 6 manuel gider + 2 recurring şablon (kira, doğalgaz) - şablonlar oluşturulduktan
kısa süre sonra arka plandaki `RecurringExpenseScheduler` gerçekten çalışıp iki realizasyonu kendisi
üretti, bu yüzden elle eklenmiş "gerçekleşmiş örnek" kira kaydı iptal edildi (`cancel` endpoint'i) -
gerçek mekanizma zaten kendi örneğini üretmiş oldu; 6 customer_order tam checkin→cart→payment
(mock-outcome + poll)→accept→ready→complete akışıyla farklı masalardan (#1-#4, #6 masa 1'in ikinci
ziyareti) oluşturuldu, #5 (masa 5) reddedilip `RefundService` otomatik tam refund'u tetikledi, #6
üzerinde manuel kısmi refund (1 adet Cappuccino) yapıldı.

**Doğrulama (branch-manager ile canlı tarayıcı):** Özet - brüt ₺1.725, refund ₺275, net ₺1.450,
6 sipariş, ortalama sepet ₺287,50 (hepsi DB ile birebir). Siparişler sekmeleri (Tamamlanan/
Reddedilen/İade) doğru dağılım gösteriyor. Kasa: günlük ciro ve tamamlanan sipariş sayısı (5)
doğru, kuyruklar boş (hepsi zaten sonuçlanmış). Giderler: manuel liste kasıtlı olarak
`sourceTemplateId IS NULL` filtreliyor (recurring-üretilenler ayrı tutuluyor - mevcut tasarım,
bug değil) ama Raporlar'daki "Yönetimsel Net Sonuç" ikisini de topluyor: Manuel ₺10.850,75 +
Tekrarlayan ₺46.800,00 = ₺57.650,75 toplam gider, net sonuç -₺56.200,75 (kira/kuruluş
maliyetleri satış hacmini aştığı için negatif - beklenen, tek aylık ilk kurulum verisiyle
gerçekçi). Kategori/ürün bazlı ciro dağılımı ve customer-web menüsü (görseller dahil) görsel
olarak doğrulandı. Commit/push yapılmadı (kullanıcı talebi).

### Business-scoped Personel ekranından PLATFORM_ADMIN'in tamamen gizlenmesi (bug fix)

**Bulgu:** Business Admin'in Personel ekranı (`/api/staff/staff-users` GET, `StaffUserController.list`)
`StaffAuthService.listStaffUsers(businessId, branchId)` çağırıyordu, bu da rol filtresi olmadan
şubeye atanmış her `StaffUser`'ı döndürüyordu. `createStaffUser` PLATFORM_ADMIN'i business'ın
*her* şubesine otomatik atadığı için (`StaffAuthService.java:158-160`), aynı business içindeki bir
PLATFORM_ADMIN hesabı bu listede normal personel gibi görünüyordu - frontend'de (`app/staff/page.tsx`)
`ROLE_LABELS`'ta karşılığı olmadığı için ham "PLATFORM_ADMIN" string'i olarak.

Daha ciddisi: `StaffAuthService.deactivateStaffUser(businessId, branchId, staffUserId)` hedefin
rolünü hiç kontrol etmiyordu - sadece `businessId` ve şube ataması kontrol ediliyordu, PLATFORM_ADMIN
her şubeye zaten atanmış olduğundan bu kontrolü geçiyordu. Yani ID'sini bilen bir BUSINESS_ADMIN,
kendi business'ındaki PLATFORM_ADMIN hesabını gerçekten devre dışı bırakabiliyordu. `resetPassword`
zaten PLATFORM_ADMIN hedefini reddediyordu (403); business-scoped tarafta rol değiştirme endpoint'i
zaten yok (rol değişimi sadece Platform Admin panelinde ve orada da `requireNonPlatformAdminTarget`
ile PLATFORM_ADMIN hedefi zaten engelleniyor) - o iki nokta zaten güvenliydi.

**Kapsam dışı bırakılan:** `StaffAuthService.listStaffUsers(businessId)` (tek parametreli, sadece
`PlatformAdminStaffController.list` kullanıyor) ve Platform Admin panelinin diğer tüm endpoint'leri
bilerek dokunulmadan bırakıldı - kullanıcı talebi açıkça "Platform Admin panelindeki mevcut
kullanıcı/işletme yönetimini bozma" dedi ve o panel zaten PLATFORM_ADMIN'i görüp yönetebilmeli.

**Çözüm:**
1. `listStaffUsers(businessId, branchId)` sonucuna `role != PLATFORM_ADMIN` filtresi eklendi -
   business-scoped liste artık PLATFORM_ADMIN'i asla döndürmüyor (frontend'de ekstra gizleme
   gerekmedi, veri zaten gelmiyor).
2. `deactivateStaffUser(businessId, branchId, staffUserId)`'a, `resetPassword`'daki mevcut
   desenle birebir aynı kontrol eklendi: hedef PLATFORM_ADMIN ise `StaffPermissionDeniedException`
   (403) - branch ataması kontrolünden önce.
3. Regression testleri `StaffAccessFlowIntegrationTest`'e eklendi: business-scoped listede
   PLATFORM_ADMIN'in hiç görünmediği + ID'si bilerek yapılan deactivate/reset-password
   denemelerinin 403 döndüğü + gerçek personelin (BUSINESS_ADMIN dahil) listede ve mutation'larda
   etkilenmediği doğrulandı.

**Ek doğrulama - Platform Admin panel tarafı (kod değişikliği yok, sadece test):** Kullanıcı
`/api/platform-admin/**`'te de PLATFORM_ADMIN hedefine mutation yapılamamasını (listede görünmesi
sorun değil, salt-okunur kalmalı) doğrulamamı istedi. `StaffAuthService.requireNonPlatformAdminTarget`
zaten deactivate/activate/role-change/reset-password'ün dördünün de tek ortak kontrol noktası -
kod değiştirilmedi. `PlatformAdminFlowIntegrationTest.platformAdminCannotManageAnotherPlatformAdminAccountThroughThePanel`
testi genişletildi: önceden sadece deactivate+reset-password'ü kapsıyordu, şimdi activate ve
role-change de eklendi (hepsi 403), listede PLATFORM_ADMIN'in hâlâ göründüğü doğrulandı (salt-okunur
olması bekleniyor, gizlenmesi değil), ve tüm reddedilen denemelerden sonra hedef hesabın orijinal
şifresiyle hâlâ login olabildiği (reset-password'ün gerçekten etkisiz kaldığı) teyit edildi.
6/6 test geçti, business-scoped düzeltmeye dokunulmadı.

Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-24 — Rol bazlı UI/permission smoke test + BRANCH_MANAGER ordering-toggle boşluğu

Kullanıcı BUSINESS_ADMIN/BRANCH_MANAGER/CASHIER ile canlı login yapıp (Chrome automation) her
rolün sidebar'ının backend permission'larıyla birebir tutarlı olduğunu, URL'yi elle yazarak
yetkisiz sayfalara gidilemeyeceğini ve kritik aksiyonların (personel, menü, masa/QR, şube
ayarları, giderler, raporlar, refund, Kasa) doğru korunduğunu doğrulamamı istedi.

**Sonuç - mevcut kod zaten sağlamdı:** 3 rolün sidebar'ı (`staffNav.ts`) `StaffRole.permissions()`
ile tam örtüşüyordu; sidebar'da gizlenen her sayfaya manuel URL ile gidildiğinde backend gerçekten
403 döndürüyordu (menü, personel, şube ayarları, masalar, denetim kaydı, işletme ayarları,
giderler, refund arama, platform-admin - hepsi network log'uyla teyit edildi). Refund arama ve
business-settings kaydetme gibi kritik aksiyonlar da 403 ile reddedildi, oturum bozulmadan.
İki kozmetik (güvenlik dışı) gözlem: `/refunds` sayfası 403'te `router.replace("/")` ile login
ekranına atıyor (session aslında hâlâ geçerli, `/dashboard`'a dönünce görülüyor) - diğer sayfalar
gibi satır içi hata banner'ı göstermek yerine; ve business-settings formundaki para birimi/saat
dilimi alanları programatik hızlı tıklamada değeri commit etmeyip 400 (403 değil) dönebiliyor -
her iki durum da yetkilendirme açığı değil, ayrı UX detayları.

**Bulunan gerçek tutarsızlık:** Backend `StaffRole.java` ve `StaffTenantController` Javadoc'u
açıkça BRANCH_MANAGER'ın kendi şubesi için `Permission.ORDERING_TOGGLE` ile sipariş alımını
açıp kapatabilmesi gerektiğini söylüyordu, ama frontend'de Şube Ayarları sadece BUSINESS_ADMIN'e
gösteriliyordu; sayfaya manuel gidilse bile ilk veri yüklemesi (`GET /branch`, `GET
/branch/business-hours`) `Permission.BRANCH_MANAGE` istediğinden (BRANCH_MANAGER'da yok) sayfa
hep "Bir şeyler ters gitti" ile patlıyordu - yani role verilmiş bir yetkinin kullanılabileceği
hiçbir UI yolu yoktu. Kullanıcıya soruldu, "şimdi düzelt" seçildi.

**Çözüm:**
1. `StaffAuthService`'e `Permission...` varargs alan iki "anyOf" overload eklendi:
   `resolveStaffContextForActiveBranch(sessionId, Permission... anyOf)` ve
   `resolveStaffContextForBranch(sessionId, branchId, Permission... anyOf)` (parametre sırası
   farklı olduğu için mevcut tek-Permission overload'larla çakışmıyor, Java en spesifik metodu
   seçiyor). `requireAnyPermission` helper'ı rolün listedeki permission'lardan en az birine sahip
   olup olmadığını kontrol ediyor.
2. `StaffTenantController.listBranches` ve `getBusinessHours`, `Permission.BRANCH_MANAGE`
   yerine `anyOf(BRANCH_MANAGE, ORDERING_TOGGLE)` kullanacak şekilde güncellendi - artık
   BRANCH_MANAGER da bu iki GET'i (yalnızca okuma) geçebiliyor. Tüm POST/PATCH endpoint'leri
   (adres, saat dilimi, kasa kabul süresi, teslimat modeli, çalışma saatleri) bilerek
   `BRANCH_MANAGE`'de bırakıldı - sadece `/branch/ordering-enabled` zaten `ORDERING_TOGGLE`
   kullanıyordu, değişmedi.
3. Frontend: `staffNav.ts`'te "Şube Ayarları" nav item'ına `BRANCH_MANAGER` eklendi.
   `app/branches/page.tsx` artık `me()` ile rolü de çekiyor; `canManageBranch = role ===
   "BUSINESS_ADMIN"` false olduğunda (yani BRANCH_MANAGER) sadece "Operasyon" kartındaki
   sipariş alımı toggle'ı gösteriliyor - "Teslimat modeli" alanı, "Operasyonu Kaydet",
   "Şube Bilgileri" ve "Çalışma Saatleri" bölümlerinin tamamı (hepsi BRANCH_MANAGE gerektirdiği
   için) gizleniyor.
4. Doğrulama: backend `mvn test` (ilgili 3 sınıf + tam suite) yeşil; canlı testte BRANCH_MANAGER
   artık sadece toggle'ı görüyor ve gerçekten çalışıyor ("Sipariş durumu güncellendi" toast +
   `openNow`/`orderingEnabled` state'i değişiyor); aynı oturumda adres değiştirme denemesi hâlâ
   403 ("Missing permission: BRANCH_MANAGE"); CASHIER hâlâ sayfayı hiç göremiyor/açamıyor (nav'da
   yok, manuel URL 403); BUSINESS_ADMIN'de sayfa öncekiyle birebir aynı (regresyon yok). Backend
   (`infra-backend-1`) ve staff-web (`infra-staff-web-1`, volume mount yok - Dockerfile'dan build
   ediliyor, kod değişikliği için `docker compose build` + `up -d` gerekiyor) image'ları yeniden
   build edilip yeniden başlatıldı.

Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-24 — Rol smoke testte kalan 2 UX düzeltmesi: 401/403 ayrımı + business-settings çift gönderim

Bir önceki smoke test girdisinde "kozmetik" diye not düşülen iki gerçek UX kusuru:

1. **403'te login'e yönlendirme:** `refunds`, `orders` ve `cashier` sayfalarındaki ilk yükleme/arama
   catch bloklarının hepsi `err.status === 401 || err.status === 403` durumunda aynı şekilde
   `router.replace("/")` çağırıyordu - yetkisiz bir role sahip kullanıcı (ör. CASHIER `/refunds`'a
   manuel giderse) geçerli oturumuyla login ekranına atılıyordu, session'ın hâlâ geçerli olduğu
   `/dashboard`'a dönünce görülüyordu.
2. **Business Settings çift gönderim → 400:** "Ayarları Kaydet" formunda `disabled={savingSettings}`
   var ama bu prop yalnızca React commit sonrası DOM'a yansıyor; repaint'ten önce ulaşan ikinci bir
   submit event'i (hızlı çift tık/Enter+tık) handler'ı `savingSettings` hâlâ `false` görerek tekrar
   çalıştırıyor, iki eşzamanlı `updateBusinessSettings` isteğinden biri backend'de 400 dönüyordu.

**Çözüm (küçük, ortak, sayfa mantığına dokunmadan):**
- `lib/api.ts`'e `isSessionExpired` (401) / `isAccessDenied` (403) yardımcıları eklendi - beş çağrı
  noktasındaki (`cashier` ×2, `orders` ×2, `refunds` ×1) `err instanceof ApiError && (401||403)`
  tekrarının yerine geçti.
- `AppShell`'e `accessDenied?: boolean` prop'u eklendi; `true` olduğunda sidebar/topbar aynı kalıp
  içerik alanında mevcut `ErrorState` ile "Bu sayfaya erişim yetkiniz yok." gösteriyor (yeni
  component yok, var olanı kullandı). Üç sayfa da 401'de hâlâ `router.replace("/")`, 403'te ise
  `setAccessDenied(true)` + bu prop'u `AppShell`'e geçiriyor.
- `business-settings/page.tsx`: `handleSaveSettings` içine `savingSettingsRef` (useRef) eklendi -
  handler'ın en başında senkron kontrol edilip set ediliyor, `finally`'de sıfırlanıyor. State'e
  bağlı `disabled` prop'un kapatamadığı render-timing açığını, event-loop içinde senkron olarak
  kapatıyor. Yalnızca bu form değiştirildi - aynı state/disabled desenini kullanan diğer ~97 submit
  handler'ına dokunulmadı (geniş refactor istenmedi); kişi ekleme formu da rapor edilen kapsamın
  dışında bırakıldı.

**Regresyon testleri:** staff-web'de daha önce React component testi yoktu (yalnızca `node --test`
ile saf mantık testleri) - `customer-web`'deki kurulumla birebir aynı `vitest` + `@testing-library/react`
+ `jsdom` eklendi (`vitest.config.mts`, `vitest.setup.ts`; `package.json`'da `test:unit` (mevcut
`node --test`, değişmedi) + `test:components` (yeni `vitest run`) + `test` ikisini sırayla çalıştırıyor).
Eklenen testler: `lib/api.test.ts` (isSessionExpired/isAccessDenied, node:test), `components/layout/
AppShell.test.tsx` (accessDenied=true'da içerik yerine mesaj + `router.replace` çağrılmıyor;
accessDenied=false'ta normal içerik; `/me` gerçekten 401 verirse hâlâ login'e yönlendiriyor),
`app/business-settings/page.test.tsx` (aynı formun `fireEvent.submit` ile art arda iki kez, ilk istek
hâlâ pending'ken tetiklenmesi `updateBusinessSettings`'i yalnızca bir kez çağırıyor; ilk istek
bitince tetiklenen üçüncü submit normal şekilde ikinci isteği yolluyor). `npm test` (10 node:test +
4 vitest testi) ve `npx tsc --noEmit` yeşil.

Commit/push yapılmadı (kullanıcı talebi).

## 2026-08-24 — Staff-web genel UI: arama ikonu, Kasa Yenile, dark mode, sidebar collapse

Dört ayrı UI kusuru rapor edildi, hepsi shared component seviyesinde çözülecek (geniş refactor yok):

**Tasarım (uygulama öncesi):**
1. **Arama ikonu çakışması:** `refunds`/`orders` sayfalarındaki `searchInputWrap` deseni (ikon
   absolute + ayrı `page.module.css`'te `padding-left: 40px`) iki farklı CSS module dosyasına
   bölünmüş - cascade sırası bundler'ın import graph'ına bağlı, garanti değil. Kasa'daki arama ise
   shared `Input` component'ini hiç kullanmıyor, kendi hardcoded-renkli `.searchBar`'ı var (dark
   mode'da da kırık). Çözüm: `components/ui/Input.tsx`'e opsiyonel `icon` prop'u eklenecek - ikon
   pozisyonu ve `padding-left` aynı `Input.module.css` dosyasında, tek kaynaktan, cascade sırası
   riski olmadan tanımlanacak. Üç sayfa da (`cashier`, `refunds`, `orders`) kendi
   `searchInputWrap`/`searchBar` kopyalarını silip bu prop'u kullanacak.
2. **Kasa Yenile no-op görünümü:** `reloadAll()` gerçekten sipariş listelerini + KPI'ları (`refreshMetrics`)
   yeniden çekiyor, ama `loading` yalnızca ilk yüklemede true oluyor - manuel tıklamada buton hiç
   disabled/loading durumuna girmiyor, kullanıcıya "hiçbir şey olmadı" izlenimi veriyor. Çözüm: ayrı
   bir `refreshing` state - tıklanınca true, ikon spin + buton disabled, istek bitince false.
3. **Dark mode:** `app/globals.css`'teki token seti (`:root[data-theme="dark"]`) zaten doğru ve eksiksiz;
   sorun `AppShell.module.css` (sidebar/topbar), `app/cashier/[branchId]/page.module.css`,
   `app/tables/page.module.css`, `app/branches/page.module.css`'in token yerine hardcoded hex/rgba
   renk kullanması - bu yüzden o bölgeler dark mode'da hâlâ açık renkte kalıyor. Çözüm: bu dosyalardaki
   hardcoded renkleri (`--color-*` token ailesi + gerekiyorsa dark override'da yeni bir eşleniği)
   tokenlara taşımak; ikonik turuncu accent (`--color-primary`/`--kasa-accent` vb.) iki temada da aynı
   kalıyor zaten (globals.css'te böyle tanımlı), değiştirilmeyecek.
4. **Sidebar collapse:** Topbar'daki `.hamburger` (yalnızca <1024px'te görünen, mobil drawer'ı açan
   IconButton) kaldırılacak; yerine sidebar'ın üst kısmına (`brand` satırına, logo yanına) yeni bir
   toggle IconButton eklenecek. Bu tek buton hem `drawerOpen` (mobil) hem yeni bir `collapsed` state'ini
   (masaüstü) aynı anda toggle'layacak - hangisinin görsel etkisi olacağını mevcut `@media (max-width:
   1023px)` breakpoint'i belirliyor, ayrı bir JS matchMedia kontrolüne gerek yok. `collapsed` state'i
   sayfa geçişlerinde AppShell yeniden mount olduğu için component state'te tutulamıyor (17 sayfa da
   kendi `<AppShell>`'ini kuruyor) - `lib/theme.ts`'teki `useSyncExternalStore` + `localStorage` deseni
   kopyalanarak yeni bir `lib/sidebarCollapse.ts` eklenecek. Collapsed genişlikte nav label'ları,
   grup başlıkları, marka wordmark'ı, kullanıcı e-postası/rolü ve destek kartı metni gizlenecek
   (yeni `linkLabel` vb. span'larla sarmalanıp `.collapsed` altında `display:none`); ikonlar kalacak.
   Mobil davranış (drawer + backdrop + link tıklayınca otomatik kapanma) değişmeyecek.

**Uygulama ve doğrulama (bilgisayar reset'i sonrası devam):** Bilgisayarda reset olmuş, Docker
daemon ve tüm container'lar durmuştu; oturuma devam ederken Docker Desktop yeniden başlatıldı,
`infra-postgres-1`/`infra-mailhog-1` manuel `docker compose up -d` ile ayağa kaldırıldı (backend
bu ikisine bağımlı olduğu için restart-loop'taydı). Kod tarafında yukarıdaki 4 madde çalışma
dizininde zaten tam uygulanmış haldeydi (reset koddan önce, sadece bu log girdisinin
tamamlanmasından ve canlı doğrulamadan önce olmuş) - `npx tsc --noEmit` ve `npm test` (10
node:test + 4 vitest) reset sonrası da yeşil, backend `mvn -o compile` de temiz. staff-web image'ı
(volume mount yok) `docker compose build staff-web` + `up -d` ile yeniden build edilip
doğrulandı: sidebar collapse toggle (PLATFORM_ADMIN ile, `/platform-admin/businesses`) genişlik
animasyonu ve label gizleme dahil sorunsuz; Kasa'daki arama kutusu ikonu artık `Input` component'i
üzerinden hizalı; "Yenile" butonu tıklanınca spin/disabled oluyor; `/orders` sayfasındaki arama
kutusu da aynı ikon deseniyle tutarlı; tüm bu ekranlar dark mode'da (varsayılan tema) hardcoded
renk kalmadan doğru görünüyor. Konsol hatası yok.

**Yan not - iki hesap login olamıyor:** Doğrulama sırasında `business-admin@qrmenu.local` ve
`branch-manager@qrmenu.local` (`Test1234!`) ikisi de 401 "Invalid email or password" döndü;
`arda@qrmenu.local` (PLATFORM_ADMIN) ve `cashier@qrmenu.local` (CASHIER) sorunsuz login oldu.
Önceki bir memory/log girdisi `business-admin@qrmenu.local`'ın 2026-08-24 reset'inde oluşturulduğunu
söylüyordu - ya reset sonrası bir volume/veri kaybı ya da o hesap hiç kalıcı olmamış, araştırılmadı
(bu oturumun kapsamı dışında, kullanıcıya ayrıca bildirildi).

Commit/push yapılmadı.
