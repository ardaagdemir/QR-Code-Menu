# Gap Analizi — Yeni `product-requirements.md` vs Mevcut Kod

> Bu dosya, güncellenen `docs/product-requirements.md` (kasa onay kapısı, raporlama, gün sonu/Excel, gider
> yönetimi, zincir yönetimi vb. eklenen revizyon) ile mevcut kod tabanının karşılaştırmasıdır. Kod/migration
> değişikliği içermez, yalnızca durum tespitidir.
>
> **Not (2026-08-12):** Aşağıdaki bölüm 1/2 tabloları dosyanın ilk yazıldığı andaki durumu (tespit anı)
> yansıtır ve bilinçli olarak güncellenmemiştir — güncel durum için bölüm 3'teki ✅ işaretlerine ve
> `development-progress.md`'ye bakın. Bölüm 3'teki 1-13 arası maddelerin tamamı artık ✅ COMPLETED.

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
| **Kasa onayı (`ordercontrol`)** | ❌ MISSING | Modül, statüler, ACCEPT/REJECT uç noktaları, `reasonCode`+not, kasa dashboard ekranı — hiçbiri yok. |
| **Roller/Permission** | ❌ MISSING | `CASHIER` rolü yok. `ORDER_ACCEPT/ORDER_REJECT/ORDER_VIEW/REPORT_*/EXPENSE_*/BUSINESS_SETTINGS_MANAGE` permission'larının hiçbiri yok. Mevcut `QR_MANAGE` yeni dokümanda `TABLE_QR_MANAGE` (kozmetik). |
| **Menü yönetimi** | 🟡 PARTIAL | Category/Product/Option/BranchProduct/opt-in çalışıyor. `allergens`, `estimatedPreparationMinutes`, `active/passive` alanları **yok**. Toplu şubeye atama ("tüm şubelere ata" / "seçili şubelere ata") **yok**. |
| **Şube/İşletme ayarları** | 🟡 PARTIAL | `Business`: yalnızca name+active var; `defaultCurrency`, `defaultTimeZone`, `BusinessContact` yok. `Branch`: `timezone`, `address`, haftalık saatler, geçici kapatma override'ı yok. |
| **Zincir yönetimi** | 🟡 PARTIAL | Business→Branch çoklu şube yapısı zaten var (M2'den beri). Şube karşılaştırma dashboard'u, toplu ürün atama, `StaffAnnouncement` **yok**. |
| **Raporlama/Analytics** | ❌ MISSING | Modül yok. Ciro/refund/sipariş sayısı/ürün-kategori kırılımı/saatlik dağılım/şube karşılaştırma — hiçbiri yok. |
| **Gün sonu + Excel** | ❌ MISSING | `DailyBranchCloseReport`, PREVIEW/FINAL akışı, `.xlsx` export — hiçbiri yok. |
| **Sahibine bildirim** | ❌ MISSING | `OwnerNotificationPort`, email/WhatsApp adapter — yok (spec bunu blocker saymıyor, opsiyonel). |
| **Gider yönetimi** | ❌ MISSING | `expense` modülü, kategori, recurring template, onay akışı — hiçbiri yok. |
| **Session/TableVisit TTL** | ✅ (2026-08-12) | ~~`AnonymousCustomerSession`/`TableVisit` var, `lastActivityAt` tutuluyor ama hiçbir scheduled job TableVisit'i süresi dolunca kapatmıyor (yalnızca DRAFT order 2 saatte cancel oluyor).~~ `TableVisitCleanupScheduler` eklendi (Gap-Analysis #13). |
| **Frontend — customer-web** | 🟡 PARTIAL | Mobil-first temel, sepet, ödeme, tracking, receipt çalışıyor. Eksik: allergen/prep-time gösterimi (veri yok), **"işletme onayı bekleniyor" durumu**, refund durumu ekranı, kabul/red mesajları. |
| **Frontend — staff-web** | 🟡 PARTIAL | Login, KDS, refund, branch/table/QR, menü, personel, audit, pickup board var. Eksik: kasa dashboard, raporlama ekranı, Excel indirme, gider ekranı, business hours/contact yönetimi, staff announcement. |

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

Bu sıralama, dokümanın kendi M6→M13 planıyla ve Bölüm 25'teki "önce CONFLICTING düzelt, sonra sırayla eksikleri
tamamla" kuralıyla birebir uyumlu.
