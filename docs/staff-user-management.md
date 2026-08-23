# Personel (Staff) Kullanıcı Yönetimi

Herhangi bir rolden (`BUSINESS_ADMIN`, `BRANCH_MANAGER`, `CASHIER`, `PLATFORM_ADMIN`) staff
kullanıcısının en baştan nasıl eklenip silineceğini anlatan referans. Kod değişikliği
içermez, sadece mevcut API'lerin ve DB şemasının kullanım kılavuzu.

İlgili kaynak dosyalar:
- `backend/src/main/java/com/qrmenu/staffaccess/StaffAuthService.java`
- `backend/src/main/java/com/qrmenu/staffaccess/StaffRole.java`
- `backend/src/main/java/com/qrmenu/staffaccess/web/StaffUserController.java` (session'lı, `/api/staff/staff-users`)
- `backend/src/main/java/com/qrmenu/staffaccess/web/InternalStaffController.java` (token'lı, `/internal/**`)

## 1. Roller ve izinler

`StaffRole.java` — 4 rol var: `PLATFORM_ADMIN`, `BUSINESS_ADMIN`, `BRANCH_MANAGER`, `CASHIER`.

| Rol | Kapsam | Kullanıcı yönetimi yapabilir mi? (`STAFF_MANAGE`) |
|---|---|---|
| `PLATFORM_ADMIN` | Tüm sistem, tüm işletmeler | Evet (tüm izinler `EnumSet.allOf`) |
| `BUSINESS_ADMIN` | Tek işletme, tek şube | **Evet** — tek rol bu izne sahip business-scoped olarak |
| `BRANCH_MANAGER` | Tek şube | Hayır |
| `CASHIER` | Tek şube, sipariş/kasa | Hayır |

Önemli: `PLATFORM_ADMIN` hariç **her rol tam olarak 1 şubeye (`branchIds`) bağlı olmak
zorunda** — `StaffAuthService.createStaffUser`:

```java
if (role != StaffRole.PLATFORM_ADMIN && effectiveBranchIds.size() != 1) {
    throw new IllegalArgumentException("User-facing staff users must be assigned to exactly one branch");
}
```

`PLATFORM_ADMIN` için `branchIds` **her zaman otomatik olarak işletmenin o anki tüm
şubeleriyle değiştirilir** — istekte ne gönderilirse gönderilsin (`request.branchIds()`
görmezden gelinir). Yani `/internal` ile bir `PLATFORM_ADMIN` oluştururken `branchIds`
alanını boş bırakabilirsin, sistem bunu kendisi dolduruyor. Bunun sebebi: PLATFORM_ADMIN
zaten `canAccessBranch()` üzerinden her şubeye erişebiliyor, ama "Kasa"/"Siparişler" gibi
**tek bir aktif şube** gerektiren ekranların çalışabilmesi için `StaffContext.activeBranchId()`
kendi `staff_user_branch` kaydına bakıyor — kayıt olmazsa bu ekranlar 403 verip kullanıcıyı
login'e geri atıyordu (yaşanan gerçek bir bug, düzeltildi).

⚠️ **Bilinen sınır:** Bu otomatik atama sadece **oluşturma anındaki** şube listesini
donduruyor, sonradan eklenen şubeleri otomatik yakalamıyor. Ayrıca işletmenin **birden
fazla şubesi varsa**, o PLATFORM_ADMIN'in `branchIds`'i de birden fazla olur —
`activeBranchId()` (tam 1 şube bekleyen metod) yine 403 fırlatır, çünkü "Kasa" gibi
ekranların hangi şubeyi aktif alacağını otomatik seçecek bir mantık henüz yok (gerçek
çoklu-şube desteği bir şube-seçici UI/API gerektirir, henüz yapılmadı). Tek-şubeli
işletmelerde bu sorun oluşmaz.

Bu, `BUSINESS_ADMIN` için de geçerli — "işletme admini" olması onu tüm şubelere değil,
`staff_user_branch` tablosunda tek bir şubeye bağlıyor (DB'de
`enforce_user_facing_staff_single_branch` trigger'ı da bunu zorunlu kılıyor).

## 2. Şifreleme

Ham şifre asla DB'ye yazılmıyor. `StaffAuthService` içinde tek bir
`BCryptPasswordEncoder` instance'ı var; `createStaffUser`, `changePassword`,
`resetPassword` hepsi `passwordEncoder.encode(...)` çağırıyor. DB'deki
`staff_user.password_hash` kolonu `$2a$...` formatında BCrypt digest — tek yönlü, geri
çözülemez, sadece `matches()` ile karşılaştırılabilir.

## 3. Kullanıcı ekleme

İki farklı yol var, hangisini kullanacağın **DB'de zaten `STAFF_MANAGE` iznine sahip,
giriş yapabilen bir kullanıcı olup olmamasına** bağlı.

### 3a. Yol 1 — `/internal` bootstrap endpoint'i (session gerekmez)

Ne zaman kullanılır:
- İşletmenin **ilk** admin'ini oluştururken (henüz giriş yapabilecek kimse yok — "tavuk-yumurta" problemi).
- DB'de hiç `BUSINESS_ADMIN`/`PLATFORM_ADMIN` kalmadıysa (örn. hepsi yanlışlıkla silindiyse).

Koruma: session/cookie değil, `X-Internal-Admin-Token` header'ı (`InternalAdminAuthFilter`).
Token değeri `infra/.env` → `INTERNAL_ADMIN_TOKEN`.

> `<INTERNAL_ADMIN_TOKEN>` aşağıdaki örneklerde bir placeholder — gerçek değeri
> `infra/.env` dosyasından al ve curl'e onu yapıştır. Gerçek token değerini bu dosyaya
> (git'e commit edilen bir dosya) yazmıyoruz; `.env` gitignore'lu ama `docs/` değil.

```bash
curl -X POST http://localhost:8080/internal/businesses/{businessId}/staff-users \
  -H "X-Internal-Admin-Token: <INTERNAL_ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "kullanici@qrmenu.local",
    "password": "GucluBirSifre123!",
    "role": "BUSINESS_ADMIN",
    "branchIds": ["<branchId>"]
  }'
```

Notlar:
- `role` alanı burada **hiçbir kısıtlama olmadan** herhangi bir değer olabilir —
  `PLATFORM_ADMIN` bile bu endpoint'ten oluşturulabilir (session'lı endpoint'in aksine).
- `auditService.record(...)` çağrılmıyor — bu yoldan eklenen kullanıcılar audit log'a düşmez.
- `businessId` ve `branchId` için mevcut kayıtları görmek istersen:
  ```sql
  SELECT id, name FROM business;
  SELECT id, business_id, name FROM branch;
  ```

### 3b. Yol 2 — normal, session'lı endpoint (önerilen, ilk admin'den sonrası için)

Ne zaman kullanılır: DB'de zaten giriş yapabilen bir `BUSINESS_ADMIN`/`PLATFORM_ADMIN`
varsa — yani normal, üretimdeki gerçek akış budur.

```bash
# 1) Admin ile giriş yap, session cookie'yi sakla
curl -X POST http://localhost:8080/api/staff/auth/login \
  -c cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@qrmenu.local", "password": "GucluBirSifre123!"}'

# 2) O session ile yeni kullanıcı ekle
curl -X POST http://localhost:8080/api/staff/staff-users \
  -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{
    "email": "yenikullanici@qrmenu.local",
    "password": "BaskaGucluSifre456!",
    "role": "CASHIER"
  }'
```

Farkları (`StaffUserController.create`):
- `branchIds` gövdede **verilmez** — otomatik olarak çağıran admin'in
  `context.activeBranchId()`'sine atanır. Yani admin hangi şubede aktifse, yeni personel
  o şubeye eklenir.
- `role: "PLATFORM_ADMIN"` **reddedilir** (`StaffPermissionDeniedException`) — bu rolü
  sadece Yol 1 (`/internal`) ile, token'la oluşturabilirsin.
- Çağıran kullanıcıda `STAFF_MANAGE` izni (yalnızca `BUSINESS_ADMIN`/`PLATFORM_ADMIN`) yoksa 403 döner.
- `auditService.record(...)` çağrılır — normal audit trail'e düşer.

## 4. Kullanıcı listeleme

```bash
curl -b cookies.txt http://localhost:8080/api/staff/staff-users
```

Sadece çağıran admin'in kendi işletmesindeki + aktif şubesindeki kullanıcıları döner
(`listStaffUsers(businessId, branchId)`).

## 5. Kullanıcı silme

### 5a. Soft delete (önerilen, uygulama içi)

Gerçek "silme" API'si yok — sadece deaktive etme var:

```bash
curl -X POST http://localhost:8080/api/staff/staff-users/{staffUserId}/deactivate \
  -b cookies.txt
```

- `staff_user.active = false` yapar, kullanıcı bir daha giriş yapamaz.
- Kayıt DB'de kalır — geçmiş siparişler/audit log/gider kayıtları bozulmaz.
- Kendi hesabını bu yoldan deaktive edemezsin, sadece kendi aktif şubendeki kullanıcıları
  deaktive edebilirsin.
- Neden hard-delete yok: `staff_user` satırı `audit_log_entry`, `expense`,
  `owner_notification_log`, `staff_session`, `staff_user_branch` tablolarından
  referans alıyor — silmek bu geçmiş kayıtların "kim yaptı" bilgisini kırar.

### 5b. Hard delete (manuel SQL — geri dönüşü yok, dikkatli kullan)

Sadece test verisi temizliği gibi durumlar için. `COMMIT`'ten önce mutlaka `SELECT` ile
kimlerin etkileneceğini kontrol et.

**Tek bir kullanıcıyı (email ile) silmek — en güvenli, önerilen:**

```sql
BEGIN;

SELECT id, email, role, business_id FROM staff_user WHERE email = 'silinecek@qrmenu.local';

UPDATE audit_log_entry SET actor_staff_user_id = NULL
  WHERE actor_staff_user_id = (SELECT id FROM staff_user WHERE email = 'silinecek@qrmenu.local');

UPDATE expense SET created_by_staff_user_id = NULL
  WHERE created_by_staff_user_id = (SELECT id FROM staff_user WHERE email = 'silinecek@qrmenu.local');

UPDATE owner_notification_log SET triggered_by_staff_user_id = NULL
  WHERE triggered_by_staff_user_id = (SELECT id FROM staff_user WHERE email = 'silinecek@qrmenu.local');

DELETE FROM staff_session
  WHERE staff_user_id = (SELECT id FROM staff_user WHERE email = 'silinecek@qrmenu.local');

DELETE FROM staff_user_branch
  WHERE staff_user_id = (SELECT id FROM staff_user WHERE email = 'silinecek@qrmenu.local');

DELETE FROM staff_user WHERE email = 'silinecek@qrmenu.local';

COMMIT;
```

**Bir role ait tüm kullanıcıları silmek (örn. tüm `CASHIER`'lar) — dikkat, geniş etki:**

```sql
BEGIN;

SELECT id, email, business_id FROM staff_user WHERE role = 'CASHIER';

UPDATE audit_log_entry SET actor_staff_user_id = NULL
  WHERE actor_staff_user_id IN (SELECT id FROM staff_user WHERE role = 'CASHIER');

UPDATE expense SET created_by_staff_user_id = NULL
  WHERE created_by_staff_user_id IN (SELECT id FROM staff_user WHERE role = 'CASHIER');

UPDATE owner_notification_log SET triggered_by_staff_user_id = NULL
  WHERE triggered_by_staff_user_id IN (SELECT id FROM staff_user WHERE role = 'CASHIER');

DELETE FROM staff_session
  WHERE staff_user_id IN (SELECT id FROM staff_user WHERE role = 'CASHIER');

DELETE FROM staff_user_branch
  WHERE staff_user_id IN (SELECT id FROM staff_user WHERE role = 'CASHIER');

DELETE FROM staff_user WHERE role = 'CASHIER';

COMMIT;
```

**⚠️ `BUSINESS_ADMIN` rolündeki tüm kullanıcıları toplu silme riski:** `STAFF_MANAGE`
iznine sahip tek business-scoped rol `BUSINESS_ADMIN` olduğu için, o işletmedeki tüm
`BUSINESS_ADMIN`'leri silersen (ve `BRANCH_MANAGER`/`CASHIER` de kalmamışsa) o işletmede
**kimse giriş yapıp yeni personel ekleyemez** — tek kurtarma yolu Bölüm 3a'daki
`/internal` bootstrap endpoint'i ile yeniden bir `BUSINESS_ADMIN` oluşturmaktır.

## 6. Şifre değiştirme / sıfırlama

- **Kendi şifreni değiştirme** (`POST /api/staff/auth/change-password`): mevcut şifreyi
  bilmen ve giriş yapmış olman gerekir. Başarılı olursa mevcut session hariç tüm diğer
  session'ların silinir.
- **Admin, başka bir kullanıcının şifresini sıfırlama**
  (`POST /api/staff/staff-users/{staffUserId}/reset-password`): admin yeni şifreyi
  kendisi belirler (sistem otomatik üretmez), hedef kullanıcının tüm session'ları
  silinir. Kendi hesabın için kullanılamaz, `PLATFORM_ADMIN` hedefine karşı kullanılamaz.
- Sistemde e-posta/SMS tabanlı "şifremi unuttum" self-servis akışı **yok** — şifresini
  unutan ve giriş yapamayan bir çalışan için tek yol, yetkili bir admin'in yukarıdaki
  reset-password ile onun yerine yeni bir geçici şifre belirlemesidir.

## 7. Özet karar tablosu

| Durum | Ne yap |
|---|---|
| İlk kez bir işletme kuruyorum, hiç admin yok | Bölüm 3a (`/internal`) ile ilk `BUSINESS_ADMIN`'i oluştur |
| Zaten giriş yapabilen bir admin var, yeni personel ekleyeceğim | Bölüm 3b (normal login + `/api/staff/staff-users`) |
| Bir çalışanı işten çıkardım, geçmişi korunsun istiyorum | Bölüm 5a (deactivate) |
| Test verisini temizliyorum, geçmiş önemli değil | Bölüm 5b (manuel SQL hard delete) |
| Tüm `BUSINESS_ADMIN`'leri yanlışlıkla sildim | Bölüm 3a ile yeniden bootstrap et |
| Çalışan şifresini unuttu, giriş yapamıyor | Admin, Bölüm 6'daki reset-password ile yeni geçici şifre versin |
