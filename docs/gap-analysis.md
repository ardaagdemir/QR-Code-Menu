# Gap Analizi — Yeni `product-requirements.md` vs Mevcut Kod

> Bu dosya, güncellenen `docs/product-requirements.md` (kasa onay kapısı, raporlama, gün sonu/Excel, gider
> yönetimi, zincir yönetimi vb. eklenen revizyon) ile mevcut kod tabanının karşılaştırmasıdır. Kod/migration
> değişikliği içermez, yalnızca durum tespitidir.
>
> **Not (2026-08-12):** Bölüm 1'deki tablo dosyanın ilk yazıldığı andaki durumu (tespit anı) yansıtır ve
> bilinçli olarak güncellenmemiştir. Bölüm 2'deki tablo ise 2026-08-12'de kod tabanıyla tek tek karşılaştırılıp
> güncel duruma çekildi — o tarih itibarıyla açık kalan tek gerçek eksik "Session/TableVisit TTL" idi
> (bkz. Gap-Analysis #13), o da bu güncellemeyle kapatıldı. Aynı gün ayrıca `product-requirements.md`'nin
> kendisi de bölüm bölüm kodla karşılaştırıldı (bu dosyanın ✅/❌/🟡 taraması
> daha kaba bir modül-var-mı seviyesindeydi); tek gerçek ek eksik Section 11'in mutfak ekranı ciro özeti 💡
> notuydu, bkz. Gap-Analysis #14. Bölüm 3'teki 1-14 arası maddelerin tamamı ✅ COMPLETED; ayrıntı için
> `development-progress.md`'ye bakın.

## 1. En Kritik CONFLICTING / PARTIAL Noktalar

| # | Konu | Durum | Neden kritik |
|---|---|---|---|
| 1 | **Ödeme sonrası otomatik mutfağa düşme** | 🔴 **CONFLICTING** | `OrderingService.markOrderPaid()` şu an `order.markPaid()` çağırdıktan hemen sonra `order.markInKitchen()` çağırıyor — webhook geldiği an sipariş otomatik mutfağa gidiyor. Yeni Bölüm 6/7: ödeme sonrası `AWAITING_STORE_ACCEPTANCE`'a düşmeli, mutfağa gitmek için **kasa ACCEPT**'i beklemeli. Bu, roadmap'in "önce düzeltilmesi gereken" tek maddesi (Bölüm 25.4). |
| 2 | **Order state machine** | 🔴 **CONFLICTING** | `OrderStatus` enum'da `PAID` var ama yeni modelde bu adım `AWAITING_STORE_ACCEPTANCE`/`REJECTED_BY_STORE` ile değişiyor. `PAID` durumu kalkmalı veya `AWAITING_STORE_ACCEPTANCE`'a dönüşmeli. |
| 3 | **Kasa reddi → otomatik tam refund** | 🟡 **PARTIAL/CONFLICTING** | Refund akışı yalnızca personelin elle kalem/adet seçtiği manuel bir akış (M7'de bilinçli olarak "otomatik refund yok" kararı verilmişti — `ordering→refund→payment→ordering` döngüsü riski nedeniyle). Yeni spec bunu **zorunlu** kılıyor. Çözüm: orkestrasyonu modül içine değil, controller/uygulama katmanına koymak (aşağıda öneri sırasında var). |
| 4 | **Branch çalışma saatleri modeli** | 🟡 **PARTIAL/CONFLICTING** | `Branch` şu an tek bir `openingTime`/`closingTime` alanı taşıyor. Yeni spec haftanın her günü için ayrı `BranchBusinessHours(dayOfWeek, openingTime, closingTime, closed)` istiyor — mevcut alan yetersiz, şema değişikliği gerekiyor. |
| 5 | **Mutfak kuyruğu kaynağı** | 🟡 **PARTIAL** | KDS şu an "ödenmiş her sipariş" görüyor (çünkü otomatik `IN_KITCHEN`'a geçiyor). Kasa kapısı eklenince KDS sorgusu "yalnızca kasa tarafından kabul edilmiş" siparişlere daralmalı — küçük ama kritik bir değişiklik. |
| 6 | **Müşteri bildirim durumları** | 🟡 **PARTIAL** | SSE altyapısı ve `orderTrackingToken` çalışıyor, ama tracking response yalnızca `IN_KITCHEN/READY/COMPLETED` dönüyor. Ödeme durumu ayrı bir uç noktada; "işletme onayı bekleniyor/kabul etti/reddetti", "refund başlatıldı/başarısız" gibi ara durumlar tracking view'da hiç yok. |

## 2. Eksik Özellikler (kategori bazında)

| Kategori | Durum | Not |
|---|---|---|
| **Kasa onayı (`ordercontrol`)** | ✅ | `OrderControlController` (ACCEPT/REJECT), `AWAITING_STORE_ACCEPTANCE`/`REJECTED_BY_STORE` + `rejectionReasonCode`/`rejectionNote`, `staff-web/app/cashier` dashboard — tamamı var (Gap-Analysis #1). |
| **Roller/Permission** | ✅ | `StaffRole.CASHIER` + `Permission.ORDER_ACCEPT/ORDER_REJECT/ORDER_VIEW` (#1), `REPORT_*` (#8), `EXPENSE_*` (#10), `BUSINESS_SETTINGS_MANAGE` (#6) — hepsi eklendi. `QR_MANAGE` adı `TABLE_QR_MANAGE`'e yeniden adlandırılmadı — bilinçli olarak atlandı, dokümanın kendi notuyla ("kozmetik") uyumlu, fonksiyonel etkisi yok. |
| **Menü yönetimi** | ✅ | `allergens`/`estimatedPreparationMinutes`/`active` alanları + customer-web `ProductCard`'da gösterimi var; toplu şubeye atama `BulkAssignBranchesFlowIntegrationTest` ile kapsanıyor (Gap-Analysis #7, "Product Alanları"). |
| **Şube/İşletme ayarları** | ✅ | `Business.defaultCurrency/defaultTimeZone`, `BusinessContact`, `Branch.timezone/address`, `BranchBusinessHours` (haftalık) — `staff-web/app/business-settings` ekranıyla birlikte var (Gap-Analysis #6). |
| **Zincir yönetimi** | ✅ | Şube karşılaştırma dashboard'u `staff-web/app/chain-comparison`, toplu ürün atama, `StaffAnnouncement` (+ `staff-web/app/announcements`) — hepsi var (Gap-Analysis #7). |
| **Raporlama/Analytics** | ✅ | `staff-web/app/reports` (+ `[branchId]`) — ciro/refund/sipariş sayısı/ürün-kategori/saatlik/şube karşılaştırma metrikleri var (Gap-Analysis #8). |
| **Gün sonu + Excel** | ✅ | `DailyBranchCloseReport`, PREVIEW/FINAL akışı, `.xlsx` export (yetkilendirme testleriyle) — var (Gap-Analysis #9, #12). |
| **Sahibine bildirim** | ✅ (email) | `OwnerNotificationPort` + email adapter + `owner_notification_log` — var (Gap-Analysis #11). WhatsApp adapter spec'in kendisinde blocker sayılmadığı için bilinçli olarak ertelendi, eklenmedi. |
| **Gider yönetimi** | ✅ | `expense` modülü, kategori, recurring template (`RecurringExpenseScheduler`), onay akışı — `staff-web/app/expenses` ile birlikte var (Gap-Analysis #10). |
| **Session/TableVisit TTL** | ✅ (2026-08-12) | `TableVisitCleanupScheduler` eklendi; `getOwnedTableVisit` artık kapalı bir visit'i 404 sayıyor (Gap-Analysis #13). |
| **Frontend — customer-web** | ✅ | Sepet/ödeme/tracking/receipt + allergen/prep-time gösterimi (`ProductCard`), "işletme onayı bekleniyor"/red mesajları ve refund durumu (`latestRefundStatus`) tracking sayfasında var (Gap-Analysis #1, #4/#6). |
| **Frontend — staff-web** | ✅ | Kasa dashboard (`/cashier`), raporlama (`/reports`), Excel indirme, gider ekranı (`/expenses`), business settings (`/business-settings`), staff announcement (`/announcements`), zincir karşılaştırma (`/chain-comparison`) — hepsi var. |
| **Görsel/Receipt storage** | ✅ (2026-08-13) | `MediaStoragePort` + `LocalFileMediaStorageAdapter`, staff-web'de dosya upload UI (`FileUploadField`) — `Product.imageUrl`/`Expense.receiptImageUrl` artık dosya seçilerek doldurulabiliyor, manuel URL girişi yok (Gap-Analysis #15). |

**IMPLEMENTED olarak doğrulananlar** (yeniden yazılmamalı): QR→TableVisit→session, Business-level katalog +
BranchProduct opt-in, DRAFT sepet + backend revalidasyon, mock ödeme+webhook+idempotency+outbox, KDS item-bazlı
kabul/red, tam/kısmi refund (manuel), okunabilir sipariş no + SSE tracking, StaffUser auth + mevcut 4 rol, audit
log, pickup board, `DeliveryModel`, rate limiting, payment timeout scheduler.

## 3. Önerilen Geliştirme Sırası (mevcut kodu koruyarak)

1. ✅ **Kasa kabul/red kapısı (çekirdek, önce bu)** — `OrderStatus`'a `AWAITING_STORE_ACCEPTANCE`/`REJECTED_BY_STORE`
   ekle; `markOrderPaid()`'den `markInKitchen()` çağrısını çıkar. Yeni `Permission.ORDER_ACCEPT/ORDER_REJECT/
   ORDER_VIEW` + `StaffRole.CASHIER`. Accept/Reject uç noktaları (muhtemelen `ordering` altında ince bir
   alt-paket, ayrı modül değil — dokümanın kendi önerisiyle uyumlu). Reject → refund orkestrasyonu
   **controller/application katmanında** yapılmalı (ordering'in refund'a bağımlı olmaması için), döngü riski
   böylece çözülür. (Bkz. development-progress.md, Gap-Analysis #1.)
2. ✅ **Kasa dashboard (staff-web)** — yeni ekran, mevcut KDS/refund sayfa desenini tekrar kullanarak. (Bkz.
   development-progress.md, "Gap-Analysis — Kasa Dashboard".)
3. ✅ **Mutfak kuyruğu filtresini güncelle** — yalnızca kasa-kabullü siparişleri göster. (`getKitchenQueue`
   değişmeden otomatik sağlandı — IN_KITCHEN'a artık yalnızca kasa ACCEPT'i üzerinden ulaşılabiliyor; bkz.
   Gap-Analysis #1 notu.)
4. ✅ **Müşteri bildirim durumlarını genişlet** — tracking response'a ara durumları ekle (mevcut SSE altyapısı
   korunur, yalnızca state/response genişler). (Bkz. development-progress.md, Gap-Analysis #4 ve #6.)
5. ✅ **Product alanları** — `allergens`, `estimatedPreparationMinutes`, `active` (yeni migration + DTO'lar, mevcut
   alanlara ek). (Bkz. development-progress.md, "Gap-Analysis — Product Alanları".)
6. ✅ **Branch/Business ayarları** — `BranchBusinessHours`, `address`, `timezone`, geçici kapatma;
   `Business.defaultCurrency/defaultTimeZone`, `BusinessContact`. (Bkz. development-progress.md,
   Gap-Analysis #6.)
7. ✅ **Toplu menü atama + Zincir karşılaştırma + StaffAnnouncement** (finansal-olmayan kapsam; bkz.
   development-progress.md, Gap-Analysis #7).
8. ✅ **Raporlama modülü** — temel metrikler önce, sonra şube karşılaştırma. (Bkz. development-progress.md,
   Gap-Analysis #8.)
9. ✅ **Gün sonu snapshot + Excel export**. (Bkz. development-progress.md, Gap-Analysis #9.)
10. ✅ **Gider yönetimi** (expense + recurring). (Bkz. development-progress.md, Gap-Analysis #10.)
11. ✅ **Sahibine otomatik bildirim** (email adapter — WhatsApp blocker olmadığı için ertelendi). (Bkz.
    development-progress.md, Gap-Analysis #11.)
12. ✅ **Security hardening / RLS yeniden değerlendirme** — kapanışta. (Bkz. development-progress.md,
    Gap-Analysis #12.)
13. ✅ **Session/TableVisit TTL** — bölüm 2'de PARTIAL olarak tespit edilip önceliklendirme sırasına hiç
    girmemişti (asıl eksik kalan madde buydu). `TableVisitCleanupScheduler` eklendi: `last_activity_at`,
    `CustomerSessionService.VISIT_TTL`'i (6 saat) aşan her açık `TableVisit`'i `closed_at` ile kapatıyor;
    `getOwnedTableVisit` artık kapalı bir visit'i 404 olarak davranıyor (aynı ownership-mismatch deseniyle) —
    eski bir session cookie'siyle süresi dolmuş bir visit üzerinden sepete/sipariş akışına süresiz erişim
    engellendi. (Bkz. development-progress.md, Gap-Analysis #13.)
14. ✅ **Mutfak ekranında permission'a bağlı ciro özeti** — `product-requirements.md` Section 11'in 💡
    notu ("mutfak ekranında ciro gösterimi role sabitlenmez, `REPORT_FINANCIAL_SUMMARY_VIEW` olan görür")
    hiç uygulanmamıştı; bu doküman taraması sırasında fark edildi. Yeni `Permission.
    REPORT_FINANCIAL_SUMMARY_VIEW` (yalnızca BUSINESS_ADMIN/BRANCH_MANAGER'a verildi, KITCHEN_STAFF/CASHIER
    almıyor — CASHIER'ın zaten sahip olduğu düz `REPORT_VIEW`'dan kasıtlı olarak ayrı), yeni
    `GET /api/staff/branches/{branchId}/reports/kitchen-summary` uç noktası, `staff-web/app/kitchen`'a
    küçük bir brüt/net satış + sipariş sayısı bloğu (yalnızca izinli role'lerde `me()` ile kontrol edilip
    çağrılıyor). (Bkz. development-progress.md, Gap-Analysis #14.)
15. ✅ **Görsel/receipt storage (`MediaStoragePort`)** — Bölüm 3.2/16.1'in "media/storage adapter'ın
    döndürdüğü URL/key saklanır" gereksinimi hiç uygulanmamıştı; `imageUrl`/`receiptImageUrl` yalnızca düz
    string alanlardı, staff-web'de bu alanları dolduran hiçbir UI yoktu. Provider-bağımsız
    `MediaStoragePort` + tek adapter (`LocalFileMediaStorageAdapter` — content-type sniffing + boyut
    limiti + `/media/**` static serving), yeni `POST /api/staff/media/product-images` ve `/receipts`
    (permission-gated), staff-web'de yeni shared `FileUploadField` — `ProductsSection`/`ProductRow`/
    `ExpenseForm` artık dosya seçtiriyor, manuel URL girişi yok. `Product.imageUrl`/`Expense.
    receiptImageUrl` şeması değişmedi. Canlı Docker Compose + Chrome doğrulaması sırasında bulunan gerçek
    hata (non-root container + yeni named volume sahiplik çakışması) düzeltildi. (Bkz.
    development-progress.md, Gap-Analysis #15.)

Bu sıralama, dokümanın kendi M6→M13 planıyla ve Bölüm 25'teki "önce CONFLICTING düzelt, sonra sırayla eksikleri
tamamla" kuralıyla birebir uyumlu.
