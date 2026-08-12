# QR Menü, Sipariş ve Restoran Operasyon Platformu — Ürün Gereksinimleri ve Roadmap

> **Durum:** Yaşayan ürün şartnamesi. Uygulama geliştirme sürecindedir; gerçekleşen işler `docs/development-progress.md` dosyasında takip edilir.
> **Amaç:** Tek şubeli veya zincir işletmelerde QR ile sipariş/ödeme akışını; kasa, mutfak, menü, şube, raporlama ve temel gider yönetimiyle tek platformda birleştirmek.
> **Tek otorite:** Ürün davranışları ve roadmap için bu dosya; gerçekleşen kod durumu için `development-progress.md` kullanılır.

## Statü Etiketleri

| Etiket | Anlamı |
|---|---|
| ✅ **CONFIRMED** | Kullanıcı tarafından açıkça istenmiş/kararlaştırılmış davranış |
| 💡 **RECOMMENDED** | Teknik/ürün önerisi; kullanıcı isteğini güvenli ve sürdürülebilir biçimde gerçekleştirmek için önerilen yaklaşım |
| ❓ **OPEN** | Daha sonra kesinleştirilecek sağlayıcı/ürün kararı |

---

# 1. Ürün Vizyonu

✅ **CONFIRMED:** Platform yalnızca QR menü değildir. Aşağıdaki dört kullanıcı grubunun aynı operasyon üzerinde çalışmasını sağlayan restoran işletim platformudur:

1. **Müşteri:** QR → menü → sepet → online ödeme → canlı sipariş takibi.
2. **Kasa / şube operasyonu:** Ödenmiş siparişi görür, kabul eder veya tamamını reddeder/iptal eder.
3. **Mutfak:** Kabul edilmiş siparişi anlık görür, hazırlar, durumunu günceller.
4. **İşletme sahibi / yönetici:** Menü, şube, masa, QR, çalışan, satış, rapor, gider ve zincir görünümünü yönetir.

✅ Tek şubeli işletmeler ve çok şubeli zincir işletmeler aynı ürün modelini kullanır.

---

# 2. Çekirdek Müşteri Akışı

## 2.1 QR ve masa bağlantısı

✅ Her fiziksel masanın kendine ait QR kodu vardır.

✅ QR okutulduğunda müşteri ilgili `Business + Branch + Table` bağlamına girer ve menü doğrudan açılır.

✅ QR yalnızca masa ziyareti (`TableVisit`) başlatır; başka müşterinin sepetine veya siparişine erişim sağlamaz.

✅ Müşteriye işletme, şube ve masa bilgisi açık biçimde gösterilir.

## 2.2 Session modeli

✅ Müşteri oturumu hesap/login gerektirmez.

💡 **RECOMMENDED teknik yorum:** Web uygulaması bir telefonun donanımsal kimliğini güvenilir/gizlilik-dostu biçimde tanımlayamaz. Bu nedenle “telefon bazlı session”, **cihazdaki tarayıcıya ait güvenli anonim cookie/session** olarak uygulanır.

- `AnonymousCustomerSession`: tarayıcı/cihaz seviyesinde anonim kimlik.
- `TableVisit`: o session'ın belirli Business/Branch/Table ziyareti.
- Aynı telefon/tarayıcı aynı aktif ziyareti sürdürebilir.
- Başka bir telefon kendi session'ını oluşturur.
- Müşteri ödeme yaptıktan sonra session ve TableVisit sipariş takibi için devam eder.
- TableVisit sonsuza kadar açık kalmaz; configurable TTL / işletme kapanışı / uzun inaktivite ile sona erer.

💡 Müşteri fiziksel olarak aynı telefonu başka bir kişiye verirse sistemi bunu otomatik ayırt etmeye zorlayan device fingerprinting yapılmaz. Gerekirse kullanıcıya “yeni masa ziyareti başlat” davranışı sağlanabilir.

---

# 3. Menü ve Ürün Deneyimi

## 3.1 Menü modeli

✅ Ürün kataloğu Business seviyesindedir:

`Business → MenuCategory → Product → ProductOptionGroup → ProductOption`

✅ Şubede satışa açılma/fiyat/bulunabilirlik `BranchProduct` ile yönetilir.

✅ Varsayılan davranış **opt-in** olmaya devam eder:

- `BranchProduct` yok → ürün o şubede satılmaz/görünmez.
- `AVAILABLE` → görünür ve sipariş verilebilir.
- `UNAVAILABLE` → görünür, “Tükendi” gösterilir ve sepete eklenemez.

## 3.2 Product alanları

✅ Admin ürün oluştururken en az şunları yönetebilmelidir:

- name
- description
- imageUrl / image reference
- base price
- tax rate
- estimatedPreparationMinutes
- allergens
- category
- displayOrder
- active/passive
- option groups/options

💡 Alerjenler mümkün olduğunca yapılandırılmış enum/reference listesi olarak tutulur; yalnızca serbest metin olmamalıdır.

💡 Ürün görseli için domain doğrudan dosya binary'si tutmaz; bir media/storage adapter'ın döndürdüğü URL/key saklanır. Local geliştirmede basit dosya yaklaşımı kullanılabilir.

## 3.3 Zincir menü davranışı

✅ Business katalogundaki ürün adı, açıklaması, fotoğrafı, alerjenleri vb. merkezi alanlarda yapılan değişiklik, ürünü kullanan şubelere otomatik yansır.

✅ Şube yalnızca kendisine ait `BranchProduct` üzerinden availability ve izin verilen lokal override'ları değiştirir.

✅ Zincir işletme yöneticisi:

- ürünü seçili şubelere topluca atayabilir,
- tüm şubelere atayabilir,
- seçili şubelerde availability/fiyat yönetebilir,
- merkezi menü değişikliğinden etkilenen şubelere iç bildirim/duyuru gönderebilir.

💡 Yeni bir ürünün tüm şubelere otomatik satılabilir hale gelmesi domain varsayılanı yapılmaz. Bunun yerine admin tarafında **“Tüm şubelere ata”** toplu işlemi sağlanır; böylece mevcut opt-in güvenliği korunur.

---

# 4. Sepet ve Sipariş Oluşturma

✅ Sepet ayrı aggregate/modül değildir; `Order.status = DRAFT` sepettir.

✅ Müşteri:

- ürün ekleyebilir,
- seçenek seçebilir,
- miktar değiştirebilir,
- kalem çıkarabilir,
- toplamı görebilir.

✅ Frontend fiyatına güvenilmez. Backend sepete ekleme ve ödeme öncesinde Product + BranchProduct + ProductOption verilerini yeniden doğrular.

✅ OrderItem/OrderItemOption sipariş anındaki isim, fiyat, opsiyon ve gerekli raporlama alanlarının immutable snapshot'ını tutar.

✅ Floating point para hesabı kullanılmaz; tutarlar minor unit (kuruş) olarak tutulur.

---

# 5. Ödeme Akışı

## 5.1 Sağlayıcı bağımsızlığı

✅ `PaymentProviderPort` kullanılmaya devam edilir.

❓ Gerçek sağlayıcı adı daha sonra seçilecektir (iyzico/PayTR/Stripe vb.).

✅ Mock provider gerçek sağlayıcı davranışını taklit eder:

`CREATED → PROCESSING → ayrı webhook → SUCCEEDED / FAILED`

✅ Frontend callback'i siparişi doğrudan ödenmiş yapamaz. Ödeme yalnızca doğrulanmış server-to-server webhook sonucunda kesinleşir.

✅ Webhook `(provider, eventId)` bazında idempotent olmalıdır.

✅ Bir Order için birden fazla payment attempt olabilir fakat en fazla bir `SUCCEEDED` payment olabilir.

---

# 6. Kasa Kabul / Red Kapısı — Yeni Çekirdek Akış

✅ **CONFIRMED:** Başarılı ödeme siparişi doğrudan mutfağa göndermez.

Yeni ana akış:

```text
DRAFT
  ↓
AWAITING_PAYMENT
  ↓ verified payment webhook
AWAITING_STORE_ACCEPTANCE
  ├─ ACCEPT → IN_KITCHEN
  └─ REJECT → REJECTED_BY_STORE → FULL REFUND
```

✅ Ödeme başarılı olduğunda kasa/staff paneline sipariş anlık olarak düşer.

✅ Yetkili kasa kullanıcısı siparişin tamamını:

- **Kabul edebilir** → sipariş mutfağa gider.
- **Reddedebilir/iptal edebilir** → sipariş mutfağa gitmez ve tam iade süreci başlar.

✅ Red nedeni tutulmalıdır (`reasonCode` + optional note).

✅ Ödeme alınmış bir sipariş “silinmez”; red, refund ve audit kayıtları korunur.

✅ Refund başarısız olursa müşteri ve kasa bunu ayrı durum olarak görür; sipariş sessizce “iptal edildi” sayılmaz.

💡 Kasa kabul süresi uzarsa müşteriye “İşletme onayı bekleniyor” gösterilir. Daha sonra timeout/otomatik aksiyon politikası configurable olabilir.

---

# 7. Order / Payment / Refund State Modeli

## 7.1 Order

```text
DRAFT
  → AWAITING_PAYMENT
  → PAYMENT_FAILED → AWAITING_PAYMENT
  → AWAITING_STORE_ACCEPTANCE
      → IN_KITCHEN
      → REJECTED_BY_STORE
  → READY
  → COMPLETED

DRAFT → CANCELLED (TTL/terk)
```

✅ Payment state Order state'ten ayrıdır.

✅ Refund state Order state'ten ayrıdır.

## 7.2 Payment

```text
CREATED → PROCESSING → SUCCEEDED
                     → FAILED
                     → EXPIRED
CREATED → CANCELLED
```

## 7.3 Refund

```text
REQUESTED → PROCESSING → COMPLETED
                       → FAILED → PROCESSING
```

✅ Kasa tarafından ödenmiş sipariş reddi tam refund üretir.

✅ Mutfak kaynaklı kısmi red desteklenirse `RefundItem` üzerinden kısmi refund üretilebilir.

---

# 8. Mutfak Akışı

✅ Mutfak yalnızca **kasa tarafından kabul edilmiş** siparişleri görür.

✅ KDS anlık çalışır.

✅ Mutfak siparişi hazırlayıp durumunu günceller.

✅ Sipariş hazır olduğunda Order `READY` olur ve müşteriye bildirim olayı üretilir.

✅ Teslim/alım sonrası `COMPLETED` olur.

✅ Önceden kararlaştırılan item/adet bazlı kabul-red modeli korunabilir:

- orderedQuantity
- acceptedQuantity
- rejectedQuantity

💡 Bu mekanizma kasa kabulünün yerine geçmez; kasa siparişin tamamını kabul/red eder, mutfak ise kabul edilmiş sipariş içindeki operasyonel istisnaları yönetebilir.

---

# 9. Müşteri Bildirimleri ve Sipariş Takibi

✅ Siparişin her kritik aşamasında müşteri durumu güncellenir:

- ödeme bekleniyor
- ödeme işleniyor
- ödeme başarılı
- işletme onayı bekleniyor
- işletme siparişi kabul etti
- işletme siparişi reddetti
- refund başlatıldı / tamamlandı / başarısız
- mutfakta hazırlanıyor
- sipariş hazır
- sipariş tamamlandı

✅ Birincil kanal v1'de persistent order status + SSE'dir.

✅ Sayfa kapatılıp yeniden açıldığında session/order tracking üzerinden mevcut gerçek durum yüklenir.

✅ `orderTrackingToken` cookie kaybı/farklı cihaz için read-only yedek erişim olmaya devam eder.

💡 **Web Push/PWA:** Kullanıcı tarayıcı izni verirse sayfa kapalıyken READY/REJECTED gibi kritik durumlar için Web Push eklenebilir. Tarayıcı izni yokken “mutlaka cihaz bildirimi” garanti edilemez; backend durum kaydı her zaman otoritedir.

---

# 10. Kasa / Staff Web Paneli

✅ `staff-web` aşağıdaki operasyonları barındırır.

## 10.1 Kasa dashboard

- Yeni ödenmiş/onay bekleyen siparişler
- Accept / Reject
- Red nedeni
- Hazırlanan/hazır siparişler
- Refund durumu
- Günlük sipariş sayısı
- Yetkisi varsa günlük satış özeti

## 10.2 Admin / şube yönetimi

- Business bilgileri
- Branch oluşturma/düzenleme
- Business hours / kapanış saatleri
- Masa yönetimi
- QR oluşturma/revoke/yenileme
- Menü/category/product/options yönetimi
- Product image/allergen/prep time yönetimi
- BranchProduct fiyat/availability yönetimi
- Menü toplu şubeye atama
- Çalışan ve rol yönetimi
- İşletme sahibi/rapor alıcısı iletişim bilgileri
- Raporlama
- Gider yönetimi

---

# 11. Roller ve Permission Modeli

✅ Kritik işlemler role-name yerine permission ile korunur.

Önerilen roller:

- `PLATFORM_ADMIN`
- `BUSINESS_ADMIN` — işletme sahibi / merkez yönetim
- `BRANCH_MANAGER`
- `CASHIER`
- `KITCHEN_STAFF`

Örnek permission'lar:

- `ORDER_ACCEPT`
- `ORDER_REJECT`
- `ORDER_VIEW`
- `REFUND_ISSUE`
- `KITCHEN_VIEW`
- `KITCHEN_UPDATE`
- `MENU_MANAGE`
- `BRANCH_MANAGE`
- `TABLE_QR_MANAGE`
- `STAFF_MANAGE`
- `REPORT_VIEW`
- `REPORT_EXPORT`
- `REPORT_FINANCIAL_SUMMARY_VIEW`
- `EXPENSE_CREATE`
- `EXPENSE_APPROVE`
- `EXPENSE_VIEW`
- `BUSINESS_SETTINGS_MANAGE`

💡 Mutfak ekranında ciro/finansal veri gösterimi role sabitlenmez; `REPORT_FINANCIAL_SUMMARY_VIEW` permission'ı olan kullanıcıya gösterilir. Böylece işletme isterse mutfakta görünür, istemezse gizler.

---

# 12. İşletme ve Şube Ayarları

## 12.1 Business

✅ Business tenant sınırıdır.

Ek alan/ilişkiler:

- name
- legal/display name gerekirse ayrı
- defaultCurrency
- defaultTimeZone (fallback)
- BusinessContact / report recipients
- active

## 12.2 Branch

✅ Şube bazında en az:

- name
- timezone
- orderingEnabled
- address (opsiyonel)
- weekly business hours
- temporary closed/open override
- deliveryMode (`CUSTOMER_PICKUP` / `WAITER_DELIVERY`)

bulunmalıdır.

💡 Tek `openingTime/closingTime` alanı haftanın farklı günlerini temsil etmekte yetersizdir. `BranchBusinessHours(dayOfWeek, openingTime, closingTime, closed)` modeli tercih edilir.

## 12.3 Business contacts / owners

✅ Bir işletmenin birden fazla sahibi/rapor alıcısı olabilir.

`BusinessContact` en az:

- name
- phone
- email
- whatsappEnabled
- dailyReportRecipient
- monthlyReportRecipient
- active

alanlarını taşıyabilir.

---

# 13. Satış Raporlama ve Analytics — v1 Kapsamına Alındı

✅ Raporlama artık v1 dışı değildir; işletme ürününün çekirdek yönetim özelliğidir.

## 13.1 Temel metrikler

Kasa/yönetim panelinde:

- bugün brüt satış
- net satış (refund düşülmüş)
- sipariş sayısı
- ortalama sepet
- kabul/red oranı
- refund toplamı
- ürün bazında satılan adet
- ürün bazında ciro
- kategori bazında ciro
- saatlik satış dağılımı
- şube bazında satış
- masa/TableVisit bazında kullanım
- seçilebilir tarih aralığı

## 13.2 Zincir görünümü

✅ BUSINESS_ADMIN tüm şubeler için:

- toplam ciro
- şube bazında ciro
- sipariş sayısı
- ürün/adet kırılımı
- ortalama sepet
- refund
- karşılaştırmalı şube performansı

verilerini görebilir.

## 13.3 “Kaç kişi geldi?” metriği

⚠️ QR sistemi gerçek fiziksel müşteri sayısını tek başına bilemez; bir kişi bütün masa için sipariş verebilir.

Bu nedenle sistem varsayılan olarak:

- `TableVisit count`
- unique anonymous session count
- order count

raporlar.

💡 Gerçek kişi sayısı isteniyorsa `TableVisit.guestCount` opsiyonel alanı eklenir ve kasa/personel tarafından veya düşük-friction bir müşteri adımıyla girilebilir. “Kişi sayısı” yalnızca bu alan doldurulduğunda gerçek footfall metriği olarak gösterilir.

## 13.4 Rapor veri kaynağı

✅ Geçmiş raporlar canlı Product fiyatından hesaplanmaz.

- satış tutarı: Payment + immutable Order/OrderItem snapshots
- refund: tamamlanmış Refund
- ürün adedi: kabul edilmiş/satılmış OrderItem snapshot'ları

kullanılır.

Böylece sonradan ürün adı/fiyatı değişse bile geçmiş rapor değişmez.

---

# 14. Gün Sonu Kapanış Raporu ve Excel

## 14.1 Daily close snapshot

✅ Her Branch için iş günü kapanışı kaydedilebilir olmalıdır.

`DailyBranchCloseReport` önerilen alanlar:

- businessId
- branchId
- businessDate
- periodStart / periodEnd
- grossSales
- completedRefunds
- netSales
- orderCount
- acceptedOrderCount
- rejectedOrderCount
- averageOrderValue
- tableVisitCount
- guestCount (varsa)
- generatedAt
- status (`PREVIEW` / `FINAL`)

## 14.2 Zamanlama

Kullanıcı isteği: kapanıştan 10 dakika önce rapor hazırlığı.

💡 **RECOMMENDED düzeltme:** Kapanıştan 10 dakika önce alınan toplam, son 10 dakikadaki satışları kaçırabileceği için “gün sonu final” olamaz.

Bu nedenle:

1. `closingTime - 10 min` → **PREVIEW** hesaplanabilir / kapanış hatırlatması üretilebilir.
2. `closingTime` veya configurable birkaç dakika sonrası → **FINAL** snapshot alınır.
3. Sahibine FINAL değer gönderilir.

## 14.3 Excel export

✅ Admin istediği tarih/şube/Business aralığını `.xlsx` olarak indirebilir.

✅ Günlük kapanış raporları Excel'e aktarılabilir.

💡 Excel **source of truth değildir**. Asıl günlük snapshot DB'de tutulur.

Kullanıcının “aynı Excel her gün güncellensin” isteğini karşılamak için:

- Business/Branch başına aylık workbook üretilebilir,
- her FINAL kapanış yeni satır/sheet olarak append edilir,
- dosya object/file storage'da saklanır,
- gerektiğinde yeniden DB'den üretilebilir.

Bu yaklaşım bozuk Excel dosyasının finansal veri kaybına yol açmasını engeller.

---

# 15. Mekan Sahibine Otomatik Gün Sonu Bildirimi

✅ BusinessContact üzerinden bir veya daha fazla rapor alıcısı tanımlanabilir.

💡 Bildirim altyapısı provider bağımsız olmalıdır:

`OwnerNotificationPort`

Olası adapter'lar:

- email
- WhatsApp Business / yetkili provider
- ileride başka kanal

✅ FINAL daily close report özetinde en az:

- şube
- tarih
- brüt satış
- net satış
- refund
- sipariş sayısı
- en çok satan ürünler

bulunabilir.

❓ WhatsApp sağlayıcısı ve resmi hesap/şablon süreçleri daha sonra seçilecektir; WhatsApp entegrasyonu çekirdek raporlama için blocker değildir.

---

# 16. Gider Yönetimi

✅ Çalışanların/yöneticilerin şube gideri girebileceği bölüm roadmap'e alınır.

## 16.1 Expense

Önerilen alanlar:

- businessId
- branchId (opsiyonel: Business-level gider olabilir)
- categoryId
- amountMinorUnits
- incurredAt
- vendor
- description
- receiptImageUrl / file key
- createdByStaffUserId
- status (`DRAFT` / `SUBMITTED` / `APPROVED` / `REJECTED`)
- approvedBy
- approvedAt

✅ Gider fişi fotoğrafı/dokümanı eklenebilir.

✅ Gider kategorileri manuel yönetilebilir.

## 16.2 Sabit / recurring giderler

✅ Tekrarlayan gider şablonu oluşturulabilir:

`RecurringExpenseTemplate`

- amount
- category
- branch/business scope
- recurrence (`MONTHLY` ilk ihtiyaç)
- dayOfMonth
- startDate
- endDate optional
- active

✅ Sistem her dönem ilgili gider taslağını üretir; admin onaylayabilir/düzenleyebilir.

---

# 17. Gelir / Gider ve Yönetimsel Kârlılık

✅ Aylık dashboard şunları gösterebilir:

- gross sales
- refunds
- net sales
- approved expenses
- net operating result = net sales - approved expenses

⚠️ Bu değer **yasal/muhasebesel net kâr** olarak sunulmamalıdır. Vergi, stok maliyeti, personel tahakkuku, amortisman vb. tüm muhasebe kalemleri sistemde yoksa “kâr” iddiası yanıltıcı olur.

💡 UI terimi ilk sürümde **“Yönetimsel Net Sonuç”** veya **“Gelir - Kayıtlı Gider”** olmalıdır.

💡 Ürün bazında gerçek brüt kâr/marj istenirse ileride BranchProduct seviyesinde `unitCostMinorUnits` veya ayrı maliyet modeli eklenebilir.

---

# 18. Zincir İşletme Yönetimi

✅ Business birden çok Branch barındırabilir.

✅ BUSINESS_ADMIN merkez panelde:

- tüm şubeleri görür,
- merkezi menüyü yönetir,
- ürünü tüm/seçili şubelere dağıtır,
- şube fiyat/availability override'larını görür,
- tüm şubelerin satışlarını karşılaştırır,
- tüm şubelerin TableVisit/guestCount metriklerini karşılaştırır,
- şubelere duyuru gönderebilir.

## 18.1 Şube duyuruları

💡 `StaffAnnouncement`:

- businessId
- title/message
- target: ALL_BRANCHES / SELECTED_BRANCHES
- branchIds
- createdBy
- createdAt
- expiresAt optional

staff-web üzerinde gösterilebilir.

Bu özellik merkezi menü güncellemesi sonrası “Yeni menü/fiyat yayında” gibi mesajlar için kullanılabilir.

---

# 19. Frontend UX/UI Productization Baseline

Bu bölüm yalnızca görsel stil önerisi değil, **ürün gereksinimidir**. Mevcut çalışan business logic/API akışları korunur; frontend bu kurallara göre ürünleşmiş, tutarlı ve kullanıcı-dostu hale getirilir.

## 19.1 Ortak tasarım ilkeleri

✅ `customer-web` ve `staff-web` aynı temel design-token ailesini kullanır; ancak aynı ekran yoğunluğunu kullanmak zorunda değildir.

✅ Tasarım dili:
- modern, sade, güven veren
- güçlü görsel hiyerarşi
- tek bir tutarlı ikon seti
- tutarlı spacing/radius/shadow/type scale
- primary / secondary / destructive action ayrımı
- native `alert/confirm` yerine ürün içi dialog/toast pattern'i
- remote CDN'e bağımlı olmayan modern sans-serif typography

✅ Otomatik OS dark-mode, ilk ürünleşme sürümünde zorunlu değildir. Customer ve staff arayüzlerinde öncelik **kontrollü ve tutarlı light theme** olmalıdır. Dark mode daha sonra bilinçli bir ürün özelliği olarak eklenebilir.

✅ Temel reusable UI parçaları mümkün olduğunca ortak pattern'lerle oluşturulur:
- Button
- IconButton
- Badge / StatusBadge
- Card
- Input / Select / Textarea / FormField
- Modal / Dialog / Drawer / BottomSheet
- Tabs / segmented controls
- EmptyState / ErrorState / Skeleton
- Toast/feedback
- ConfirmDialog

✅ Tek sayfada birden fazla bağımsız sorumluluk büyümeye başladığında page component parçalanır. Büyük CRUD sayfaları tek `page.tsx` içinde yüzlerce satır state/form/list mantığı taşımamalıdır.

✅ Accessibility:
- görünür `:focus-visible`
- semantic HTML
- yeterli contrast
- minimum 44px touch target
- form label/error ilişkileri
- klavye ile kullanılabilir dialog/drawer
- yalnız renkle anlam taşıyan durumlar kullanılmaz

## 19.2 Customer Web — ürün deneyimi

**Hedef:** QR okutulduğunda açılan ekran teknik bir web formu değil, modern bir restoranın mobil sipariş deneyimi gibi hissettirmelidir. Mobil kullanım birincildir.

### App shell ve menü

- 360–430px telefonlar birincil viewport'tur; 768px+ ekranlarda içerik gereksiz dar bir telefon kolonuna sıkıştırılmaz.
- business / branch / table bilgisi üstte kompakt ve güven veren bir header'da gösterilir.
- kategori navigasyonu sticky ve yatay kaydırılabilir chip/tab yapısında olur.
- kategori başlıkları ve ürün grupları görsel olarak net ayrılır.
- sepet boş değilse müşterinin bir sonraki aksiyonu kolay görülür; mobilde sticky cart CTA kullanılabilir.

### Ürün kartları

- ürün görseli menünün ana görsel öğelerinden biridir; mevcut küçük thumbnail yaklaşımı yerine mobilde yaklaşık 104–120px seviyesinde güçlü görsel alan kullanılır.
- desktop/tablet'te kartlar gerekirse 2 kolonlu grid'e dönüşebilir.
- ürün adı, kısa description ve fiyat ilk bakışta okunur.
- prep time ve allergen bilgisi ikincil ama erişilebilir metadata olarak gösterilir.
- `UNAVAILABLE` ürün açık biçimde “Tükendi” görünür ve seçilemez.
- görsel yokken emoji yerine tasarım diliyle uyumlu nötr placeholder kullanılır.

### Ürün detay / opsiyon

- mobilde bottom sheet tercih edilir; geniş ekranda dialog/modal olabilir.
- varsa büyük ürün görseli üst bölümde yer alır.
- required/optional option grupları açıkça ayrılır.
- validation hatası ilgili option grubunun yanında gösterilir.
- quantity stepper kolay dokunulur olmalıdır.
- alt bölümde sticky “Sepete Ekle · Toplam” CTA bulunur.

### Sepet / ödeme / takip

- sepet bottom sheet/drawer olarak çalışabilir; item, option, quantity ve ara/toplam tutarlar net ayrılır.
- checkout aksiyonu görsel olarak birincil CTA'dır.
- ödeme ve sipariş takibi salt status metni olmamalı; anlamlı durum kartı/timeline/progress pattern'i kullanılmalıdır.
- müşteri aşağıdaki durumları teknik enum görmeden açık Türkçe mesajlarla anlamalıdır:
  - ödeme işleniyor
  - ödeme alındı / işletme onayı bekleniyor
  - işletme kabul etti
  - işletme reddetti
  - iade işleniyor / tamamlandı / başarısız
  - hazırlanıyor
  - hazır
  - tamamlandı

### Durum ekranları

Customer web'in her kritik ekranında tasarlanmış:
- loading/skeleton
- empty
- API/network error + retry
- invalid/revoked QR
- expired session/cart
- payment failure
- unavailable product

durumları bulunmalıdır.

## 19.3 Staff Web — operasyon paneli

**Hedef:** staff-web ham CRUD sayfaları toplamı değil, restoran operasyonlarının hızlı yönetildiği modern bir dashboard olmalıdır.

### Application shell

Desktop'ta üstte çok sayıda linkin wrap olduğu navigation kullanılmaz.

✅ Ana shell:
- desktop: kalıcı/collapsible sol sidebar + top bar
- küçük ekran: drawer navigation
- top bar: aktif şube/işletme bağlamı, kullanıcı/rol ve gerekli global aksiyonlar
- aktif route açıkça görünür
- navigation permission bazlı filtrelenir

Önerilen bilgi mimarisi:

**Operasyon**
- Dashboard
- Kasa
- Mutfak
- Pickup / Siparişler

**Yönetim**
- Menü
- Şubeler / Masalar / QR
- Personel

**Finans**
- Satış Raporları
- Giderler
- İadeler

**Sistem**
- Duyurular
- Denetim Kaydı
- İşletme Ayarları

Rol/permission erişimi olmayan linkler gösterilmez; backend authorization her durumda otorite olmaya devam eder.

### Dashboard

Login sonrası rolün kullanım amacına uygun landing page gösterilmelidir. BUSINESS_ADMIN/BRANCH_MANAGER için dashboard en az:
- bugünkü brüt/net satış
- sipariş sayısı
- ortalama sepet
- refund özeti
- en çok satan ürünler
- aktif/bekleyen operasyon bilgileri
- varsa şube karşılaştırması

gibi özetleri güçlü KPI kartlarıyla sunmalıdır.

### Kasa

Kasa ekranında bir sipariş kartının ilk bakışta şu bilgileri vermesi gerekir:
- masa
- sipariş numarası
- ödeme doğrulanmış bilgisi
- siparişin ne kadar süredir onay beklediği
- toplam tutar
- ürün/adet özeti

`Kabul Et` birincil aksiyondur. `Reddet` destructive/secondary aksiyon olarak ayrılır ve reason seçimi/not ile kontrollü confirm flow kullanır. Bekleme süresi uzayan siparişler görsel olarak fark edilir olmalıdır.

### Kitchen Display System

KDS normal admin CRUD ekranı gibi tasarlanmaz. Büyük/dokunmatik ekranlarda uzaktan okunabilir olmalıdır:
- büyük order/table numarası
- sipariş yaşı / geçen süre
- yüksek okunabilirlikte ürün/adet
- opsiyonların ana üründen görsel olarak ayrılması
- büyük touch actions
- NEW/PREPARING/READY gibi operasyonel ayrım veya eşdeğer net grouping
- realtime bağlantı durumu dikkat dağıtmadan görünür

KDS'de finansal bilgi yalnız gereksinim varsa ikincil gösterilir; mutfak aksiyonlarını gölgelememelidir.

### Admin / CRUD ekranları

- sayfa başlığında title + açıklama + primary action pattern'i kullanılır.
- oluşturma/düzenleme için gereksiz inline uzun form + liste yığını yerine drawer/modal veya iyi bölünmüş form pattern'i tercih edilir.
- liste ekranları uygun yerde searchable/filterable table/list kullanır.
- destructive işlemler confirm dialog ister.
- success/error feedback toast veya inline feedback ile tutarlı verilir.
- büyük `menu`, `expenses` gibi sayfalar feature/component parçalarına ayrılır.

### Raporlama

Rapor ekranı yalnız tablo değildir. Veri mevcut olduğunda:
- KPI cards
- hızlı tarih presetleri: Bugün / Dün / Bu Hafta / Bu Ay / Özel
- gelir trendi
- ürün/kategori ranking
- branch comparison
- refund etkisi
- Excel export

aynı bilgi hiyerarşisinde sunulur. Grafik için ağır bir framework eklenmesi zorunlu değildir; küçük ve sürdürülebilir bir çözüm tercih edilir.

## 19.4 Responsive davranış

✅ Customer: 360, 390, 430, 768 ve desktop viewport'larda doğrulanır.

✅ Staff:
- >=1024px: sidebar + desktop layout
- 768–1023px: compact/collapsible navigation
- <768px: drawer navigation ve tek kolon kullanılabilir yönetim ekranları
- KDS ayrıca büyük ekran/kiosk viewport'unda test edilir

Hiçbir ana flow yatay overflow, üst üste binen sticky alan veya erişilemeyen CTA üretmemelidir.

## 19.5 UI/UX Productization Gate — kabul kriterleri

Bu gate sırasında **backend business logic yeniden yazılmaz**. API kontratında yalnız UI için gerçekten gerekli additive değişiklik varsa yapılabilir.

Tamamlanmış sayılmak için:

1. Customer menu → product → option → cart → payment → tracking akışı tek bir tutarlı ürün dili kullanır.
2. Staff login sonrası top-link navigation yerine gerçek application shell/sidebar kullanır.
3. Kasa ve KDS operasyonel kullanım için özel ekran hiyerarşisine sahiptir.
4. Admin sayfalarında ortak PageHeader/Form/Table/Dialog/Feedback pattern'leri vardır.
5. Menu/Expenses gibi büyük sayfalar anlamlı feature/component'lere ayrılmıştır.
6. Rapor ekranı dashboard seviyesinde bilgi hiyerarşisine sahiptir.
7. Loading/empty/error/success/confirm pattern'leri tutarlıdır.
8. Customer ve staff frontend lint/build geçer.
9. Gerçek Chrome'da customer için 390x844, staff için desktop ve KDS için büyük ekran viewport'unda kritik flow'lar manuel/E2E doğrulanır.
10. Mevcut payment/order/refund/session/permission davranışları bozulmaz.

💡 Hafif bir icon library (örn. Lucide) kullanılabilir; ağır bir UI framework yalnız ciddi gerekçe varsa eklenir.


---

# 20. Backend Modülleri

Önerilen modular monolith feature paketleri:

| Modül | Sorumluluk |
|---|---|
| `tenant` | Business, Branch, BusinessHours, Table, QR, BusinessContact |
| `menu` | Category, Product, allergens, options, BranchProduct, bulk branch assignment |
| `customersession` | AnonymousCustomerSession, TableVisit |
| `ordering` | Order/DRAFT cart, OrderItem snapshots, state machine, tracking token |
| `payment` | PaymentProviderPort, attempts, webhook, idempotency |
| `ordercontrol` | Kasa accept/reject gate ve reject reason |
| `kitchen` | KDS, preparation/item decisions |
| `refund` | Full/partial refund |
| `notification` | Customer SSE/push abstractions + staff notifications |
| `reporting` | Sales queries, daily close snapshots, Excel export |
| `expense` | Expense, categories, recurring templates |
| `staffaccess` | StaffUser, Role, Permission |
| `audit` | Kritik aksiyon audit trail |
| `shared` | Money, DomainEvent, Outbox teknik altyapısı |

💡 `ordercontrol` ayrı package yerine ordering altında alt-feature da olabilir; gereksiz modül parçalanması yapılmamalıdır. ArchUnit sınırı gerçek coupling'e göre belirlenir.

---

# 21. Teknik Mimari Prensipleri

✅ Java 21 + Spring Boot + Maven.

✅ PostgreSQL + Flyway.

✅ Modular monolith; mikroservis yok.

✅ package-by-feature + ArchUnit.

✅ Tenant sınırı `Business`.

✅ RLS erken aşamada yok; açık business_id sorguları + servis ownership kontrolü + integration test.

✅ Transactional Outbox ödeme/order kritik event'lerinde kullanılır.

✅ Kafka v1 için gerekli değildir.

✅ Customer realtime için SSE yeterlidir; çok instance olursa Redis/pubsub daha sonra değerlendirilebilir.

✅ Gerçek ödeme kart verisi uygulama tarafından saklanmaz.

✅ API DTO'ları JPA entity'lerini doğrudan dışarı açmaz.

✅ Ödeme, refund, order acceptance, expense approval, menu update ve QR revoke gibi kritik işlemler audit edilir.

---

# 22. Güvenlik ve Veri Bütünlüğü

- QR token yüksek entropili ve revoke edilebilir.
- Session cookie: HttpOnly, Secure, uygun SameSite.
- orderTrackingToken yüksek entropili, DB'de hash.
- internal/public/admin endpointleri ayrıştırılır.
- staff auth Spring Security + permission checks.
- tenant cross-access negatif integration testleri zorunlu.
- payment webhook signature + raw body doğrulama.
- webhook idempotency DB unique constraint.
- payment başına tek SUCCEEDED garantisi DB partial unique index.
- refund toplamı başarılı payment tutarını aşamaz.
- raporlama yalnızca yetkili business/branch kapsamından veri görür.
- export endpointleri de tenant/permission kontrolüne tabidir.
- receipt/expense upload'larında content type, size ve zararlı dosya kontrolleri uygulanır.
- rate limiting QR/session/tracking ve hassas public endpointlerde değerlendirilir.

---

# 23. MVP / Ürün Fazları

## 23.1 Core Ordering v1

- QR/TableVisit/session
- menu
- cart
- online payment
- kasa accept/reject
- full refund on store reject
- kitchen flow
- customer live tracking
- READY notification state
- staff authentication/permissions
- admin menu/branch/table/QR
- sales dashboard
- Excel export
- daily close snapshot

## 23.2 Management v1.1

- multi-branch owner comparison dashboard
- bulk menu distribution
- staff announcements
- expense entry/approval
- recurring expenses
- monthly income/expense management view
- owner automatic report notification

## 23.3 Later / Optional

- WhatsApp adapter production integration
- Web Push/PWA production hardening
- legal e-Archive/ÖKC invoice integrations
- inventory/stock quantity
- procurement/vendor management
- true accounting profit/loss
- loyalty/coupon
- multilingual menu
- platform subscription billing
- white-label/subdomains
- Redis SSE fanout / horizontal scale

---

# 24. Güncellenmiş Geliştirme Roadmap'i

> `development-progress.md` gerçekleşen işi belirtir. Claude mevcut kodu bu roadmap ile karşılaştırmalı; tamamlanmış çalışan özellikleri tekrar yazmamalı, yalnızca gap'leri tamamlamalıdır.

## M1 — Foundation

- Spring Boot/Maven
- PostgreSQL/Flyway/Testcontainers
- Docker Compose
- customer-web foundation

## M2 — Business / Branch / Table / QR / Session

- Business/Branch/Table/TableQrToken
- AnonymousCustomerSession/TableVisit
- tenant isolation foundation

## M3 — Business Catalog + BranchProduct + Customer Menu

- category/product/options
- BranchProduct opt-in
- public menu
- customer menu UX

## M4 — DRAFT Order / Cart

- cart/order DRAFT
- backend price/availability validation
- order tracking token creation
- draft cleanup

## M5 — Payment + Webhook + Idempotency + Outbox

- PaymentProviderPort
- realistic mock
- payment attempt model
- verified webhook
- idempotency
- outbox
- `DRAFT → AWAITING_PAYMENT → AWAITING_STORE_ACCEPTANCE`

## M6 — Kasa Order Control + Store Reject Refund + Customer Status

- cashier/staff incoming paid orders
- accept/reject
- reject reason
- ACCEPT → kitchen
- REJECT → automatic full refund flow
- customer acceptance/rejection/refund statuses
- SSE events

## M7 — Kitchen + Ready Flow + Partial Operational Rejection

- KDS
- item/quantity accept/reject if retained
- preparing/ready/completed
- customer live tracking
- readable order number
- `/order/track/{token}`
- partial refund integration for rejected quantities

## M8 — Staff Authentication + Admin Operations + Audit

- StaffUser
- roles/permissions including CASHIER
- login
- Business/Branch/Table/QR admin
- menu admin
- product image/allergens/prep time
- audit

## M9 — Reporting + Excel + Daily Close

- reporting module
- branch/day/date-range metrics
- product/category sales
- gross/net/refund
- owner/branch dashboard
- DailyBranchCloseReport
- preview at close-10m + FINAL at close
- `.xlsx` export
- finance summary permissions

## M10 — Chain Management

- multi-branch owner dashboard
- branch comparisons
- bulk product assignment to branches
- central menu propagation verification
- selected/all branch staff announcements
- TableVisit/session metrics
- optional guestCount

## M11 — Expense Management + Recurring Expenses

- expense categories
- expense entry
- receipt upload
- approval flow
- recurring monthly expense templates
- monthly net sales vs approved expense dashboard
- explicitly non-statutory management result

## M12 — Owner Notifications + Operational Hardening

- BusinessContact/report recipients
- OwnerNotificationPort
- email adapter
- WhatsApp adapter if provider/account ready
- pickup/delivery mode finalization
- browser/web push optional
- reconciliation/timeouts
- edge cases/E2E

## M13 — Security Hardening + Production Readiness

- complete security review
- RLS reassessment
- rate limiting
- token/log redaction
- upload security
- export authorization tests
- backup/restore expectations
- observability
- deployment hardening

### Cross-cutting — UI/UX Productization Gate

Bu bir milestone numarası değildir ve mevcut roadmap'i yeniden numaralandırmaz. Mevcut kritik domain/gap işi stabil hale geldikten sonra, yeni frontend-heavy geliştirmeler büyümeden önce Bölüm 19 uygulanır.

Önerilen uygulama sırası:
1. shared frontend token/component pattern'leri
2. customer-web productization
3. staff-web application shell/sidebar
4. cashier + KDS operational UX
5. admin CRUD component refactor
6. reporting/dashboard visualization
7. responsive/accessibility/browser E2E pass

---

# 25. Gap Analysis Kuralları — Claude İçin

Yeni roadmap mevcut çalışan koddan sonra uygulanırken:

1. Önce `product-requirements.md` + `development-progress.md` + repository kodu okunur.
2. Zaten çalışan milestone/feature yeniden yazılmaz.
3. Her yeni roadmap maddesi `IMPLEMENTED / PARTIAL / MISSING / CONFLICTING` olarak zihinsel olarak sınıflandırılır.
4. Önce `CONFLICTING` davranışlar düzeltilir (özellikle payment-success → auto-kitchen eski davranışı varsa).
5. Sonra M5'ten itibaren sırayla eksikler tamamlanır.
6. Yeni rapor markdown dosyaları üretilmez; yalnızca `development-progress.md` kısa tutulur.
7. Her milestone sonunda backend test/verify, frontend lint/build ve kritik gerçek-browser E2E doğrulaması yapılır.
8. Git commit/push kullanıcı istemedikçe yapılmaz.

---

# 26. Özellikle Kaçınılacak Gereksiz Karmaşıklıklar

- ayrı Cart modülü yok
- mikroservis yok
- Kafka v1 yok
- payment kart saklama yok
- generic attribute engine yok
- Excel finansal source-of-truth değil
- “unique session = gerçek kişi” diye raporlama yok
- kayıtlı giderlerden hesaplanan sonucu “yasal net kâr” diye sunmak yok
- device fingerprinting yok
- WhatsApp entegrasyonu çekirdek sipariş/raporlama akışını bloke etmez
- her merkezi menü güncellemesinde fiziksel duplicate product kayıtları oluşturmak yok

---

# 27. Açık Kararlar

❓ Gerçek ödeme sağlayıcısı.

❓ WhatsApp/owner notification sağlayıcısı ve üretim hesabı.

❓ Product/expense görsellerinin production storage sağlayıcısı.

❓ Gerçek guest count'ın personel tarafından mı müşteri tarafından mı girileceği (özellik açılırsa).

❓ İşletme bazında kasa accept timeout politikası.

---

# 28. Bu Revizyondaki Ana Değişiklikler

1. Ödeme sonrası otomatik mutfak akışı kaldırıldı; **kasa kabul/red kapısı** eklendi.
2. Kasa reddi sonrası **tam refund zorunlu akış** haline geldi.
3. Customer session’ın ödeme sonrası takip boyunca sürmesi netleştirildi.
4. “Telefon bazlı” session device/browser cookie olarak güvenli şekilde yorumlandı; fingerprinting reddedildi.
5. Customer notification state'leri genişletildi.
6. Product alanlarına fotoğraf, açıklama, alerjen, hazırlık süresi ve sıralama eklendi.
7. Business-level merkezi katalog zincir işletme senaryosu için resmileştirildi; bulk branch assignment eklendi.
8. Kasa dashboard ve `CASHIER` rol/permission alanı eklendi.
9. **Raporlama v1 kapsamına alındı.**
10. Daily closing snapshot + Excel export eklendi.
11. Kapanıştan -10 dakika raporu PREVIEW, kapanış anı FINAL olarak ayrıştırıldı.
12. WhatsApp owner report provider-bağımsız opsiyonel entegrasyon olarak roadmap'e alındı.
13. BusinessContact / owner-report-recipient modeli eklendi.
14. Zincir şube karşılaştırma dashboard'u eklendi.
15. Gerçek “kaç kişi geldi” metriğinin QR session sayısından türetilemeyeceği netleştirildi; optional guestCount önerildi.
16. Expense + recurring expense + receipt upload roadmap'e eklendi.
17. “Ciro/kâr/zarar” raporu muhasebesel kâr yerine yönetimsel net sonuç olarak doğru isimlendirildi.
18. Roadmap 13 milestone'a genişletildi ve mevcut uygulamanın gap analysis ile devam etmesi tanımlandı.
