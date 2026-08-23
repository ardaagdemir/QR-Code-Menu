# Production Go-Live Checklist

Repo'daki mevcut prod iskeletine (`infra/docker-compose.prod.yml`, `infra/Caddyfile`,
`infra/.env.prod.example`, `infra/scripts/backup.sh` / `restore.sh`) göre doğrulanmış,
sıralı ve uygulanabilir bir go-live checklist'i. Kod değişikliği içermez.

## 1. DNS

- [ ] `API_DOMAIN`, `CUSTOMER_WEB_DOMAIN`, `STAFF_WEB_DOMAIN` için A/AAAA kayıtlarını
      sunucu IP'sine yönlendir (3 ayrı subdomain gerekiyor — `infra/Caddyfile:10-19`).
- [ ] DNS propagasyonunu doğrula (`dig +short <domain>` üçü için de sunucu IP'sini
      dönmeli).
- [ ] 80 ve 443 portlarının internetten erişilebilir olduğunu doğrula — Caddy Let's
      Encrypt HTTP-01 challenge için buna ihtiyaç duyuyor
      (`infra/docker-compose.prod.yml:145-147`).

## 2. `infra/.env.prod` dosyasını doldur

`cp infra/.env.prod.example infra/.env.prod` sonrası **hepsi zorunlu** (`docker compose`
eksik olursa fail-fast, `:?` guard'ları var):

| Değişken | Not |
|---|---|
| `API_DOMAIN`, `CUSTOMER_WEB_DOMAIN`, `STAFF_WEB_DOMAIN` | gerçek domainler |
| `ACME_EMAIL` | Let's Encrypt bildirim maili |
| `NEXT_PUBLIC_API_BASE_URL` | `https://api.<domain>` — **build-time**'da JS bundle'a gömülüyor, sonradan değiştirilemez, yanlışsa frontend'i yeniden build etmen gerekir |
| `CORS_ALLOWED_ORIGINS` | `https://order.<domain>,https://staff.<domain>` |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | güçlü parola |
| `INTERNAL_ADMIN_TOKEN` | güçlü rastgele token — bootstrap API'nin tek koruması |
| `PAYMENT_MOCK_WEBHOOK_SECRET` | boşsa mock adapter her webhook imzasını reddeder (fail-closed by design) |
| `MEDIA_STORAGE_PUBLIC_BASE_URL` | `https://api.<domain>` — ürün görseli URL'lerine gömülüyor |
| `SMTP_HOST/PORT/USERNAME/PASSWORD/AUTH/STARTTLS`, `OWNER_NOTIFICATION_FROM_EMAIL` | aşağıda ayrı madde |

- [ ] `.env.prod` gitignore'da doğrulandı (`.gitignore:34`) — commit riski yok, yine de
      `git status` ile teyit et.

## 3. SMTP (owner bildirimleri)

- [ ] Gerçek bir SMTP sağlayıcısı seç (dev'de `mailhog` kullanılıyor, prod'da yok —
      `application.yml:26-27` default'ları prod'da geçersiz çünkü compose `:?` ile
      zorunlu kılıyor).
- [ ] `SMTP_HOST/PORT/USERNAME/PASSWORD/AUTH=true/STARTTLS=true` ve
      `OWNER_NOTIFICATION_FROM_EMAIL` gir.
- [ ] Not: mail sağlık kontrolü kasıtlı olarak `/actuator/health`'i etkilemiyor
      (`application.yml:83-88`) — SMTP çökse bile backend "healthy" görünmeye devam eder,
      bunu ayrı izlemen gerekir.

## 4. Mock ödeme — kritik, karar gerektiren madde

- `PAYMENT_PROVIDER` prod compose'da **hiç set edilmiyor**, backend'de
  `payment.provider=mock` default (`application.yml:56`) ve
  `MockPaymentSimulationController` `matchIfMissing = true` ile devrede
  (`MockPaymentSimulationController.java:27`).
- **Sonuç: bugün prod'a bu haliyle çıkarsan gerçek ödeme entegrasyonu yok, mock ödeme
  akışı canlı ortamda da aktif olacak.**
- [ ] Bilinçli bir karar olarak kaydet: gerçek para akışı olmadan/gerçek ödeme
      sağlayıcısı entegre edilmeden go-live yapılacaksa bunu paydaşlara açıkça bildir
      (demo/soft-launch modu).
- [ ] `PAYMENT_MOCK_WEBHOOK_SECRET`'ı yine de güçlü/rastgele tut — mock olsa da webhook
      imza doğrulaması gerçek.
- [ ] Gerçek ödeme sağlayıcısı entegre edilene kadar bunu README/ops dokümanına "known
      limitation" olarak not düş.

## 5. PostgreSQL prod DB + ilk Flyway migration

- [ ] `docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build`
      çalıştır — `postgres` servisi `POSTGRES_DB/USER/PASSWORD` ile ilk açılışta DB'yi
      otomatik oluşturur (image'ın init davranışı).
- [ ] Backend healthcheck'i bekle (`depends_on: condition: service_healthy`,
      `infra/docker-compose.prod.yml:64-72`) — Flyway migration'ları (`V1__init.sql` →
      `V31__expense_cancellation.sql`, 31 dosya) backend başlarken otomatik uygulanır
      (`spring.flyway.locations`, `application.yml:8-9`).
- [ ] `docker compose logs backend | grep -i flyway` ile 31 migration'ın hatasız
      uygulandığını doğrula.

## 6. Media storage

- [ ] `media_data` named volume (`infra/docker-compose.prod.yml:90,153-159`) — backend
      container'ı `appuser`'a chown'lu `/data/media` ile başlıyor
      (`backend/Dockerfile:20-23`), volume mount'un bu izinleri bozmadığını ilk deploy'da
      doğrula (upload testiyle).
- [ ] Public erişim: sadece `product-images/` `/media/product-images/**` altında
      auth'suz servis ediliyor (`MediaResourceConfig.java`) — menüde görünen ürün
      fotoğrafları için tasarım gereği.
- [ ] Private erişim: `receipts/` (gider fişleri) hiçbir static path'te expose
      edilmiyor, sadece `StaffExpenseController#getReceipt` üzerinden session+permission
      kontrolüyle okunuyor — bunun prod'da da böyle kaldığını (yeni bir public route
      eklenmediğini) teyit et.
- [x] `media_data` volume artık `media-backup` sidecar ile Postgres'le aynı ritimde
      (ortak `BACKUP_INTERVAL_SECONDS`/`BACKUP_RETENTION_DAYS`) `infra/backups/media/`
      altına gzip'li `.tar.gz` olarak yedekleniyor, `:ro` mount sayesinde volume'e asla
      yazamıyor (`infra/docker/media-backup/`). Manuel yedek: `./scripts/backup.sh`;
      geri yükleme: `./scripts/restore-media.sh` (yıkıcı, `RESTORE MEDIA` onayı ister).
      `infra/backups/` klasörünü (DB + media) host dışına kopyalama sorumluluğu hâlâ
      operatörde (madde 9).

## 7. İlk BUSINESS_ADMIN / business / branch bootstrap

Sıra (tümü `X-Internal-Admin-Token: <INTERNAL_ADMIN_TOKEN>` header'ıyla,
`InternalAdminAuthFilter.java`):

1. `POST /internal/businesses` → business oluştur (`InternalTenantController.java:41-45`)
2. `POST /internal/businesses/{businessId}/branches` → branch oluştur (satır 47-53)
3. `POST /internal/businesses/{businessId}/staff-users` → ilk `BUSINESS_ADMIN`
   kullanıcısı (email, ≥8 karakter parola — `PasswordPolicy.MIN_LENGTH=8`,
   role=`BUSINESS_ADMIN`) (`InternalStaffController.java`)

- [ ] Bu adımdan sonra `POST /api/staff/auth/login` ile staff-web üzerinden giriş
      yapılabildiğini doğrula.
- [ ] `INTERNAL_ADMIN_TOKEN`'ı bootstrap sonrası güvenli bir yerde sakla (rotasyon
      gerekirse `.env.prod` güncelleyip stack'i yeniden başlat).

## 8. İlk masa + QR oluşturma

1. `POST /internal/businesses/{businessId}/branches/{branchId}/tables` → masa oluştur
2. `POST /internal/businesses/{businessId}/tables/{tableId}/qr-tokens` → aktif QR token
   üret
3. `GET /internal/businesses/{businessId}/tables/{tableId}/qr-tokens/active` → token'ı
   doğrula, `https://order.<domain>/...?token=<token>` formatında QR'ı üret/bas.

- [ ] Alternatif/gerçek yol: staff-web'de BUSINESS_ADMIN girişiyle masa/QR yönetim
      ekranı varsa (Staff-Auth login sonrası) onu kullan — `/internal/**` sadece "UI
      yokken" bootstrap içindir.
- [ ] Üretilen QR ile customer-web'i gerçekten aç, menü yüklendiğini doğrula.

## 9. Backup/restore — prod değerleriyle doğrulama

- [ ] `postgres-backup` ve `media-backup` sidecar'larının ilk yedeği aldığını doğrula:
      `ls infra/backups/` ve `ls infra/backups/media/` (varsayılan: günde 1, 7 gün
      saklama — `BACKUP_INTERVAL_SECONDS`/`BACKUP_RETENTION_DAYS`, ikisi için ortak).
- [ ] `./scripts/backup.sh prod` ile manuel DB + media yedeği al, iki dosyanın da
      oluştuğunu doğrula.
- [ ] **Staging/ayrı bir ortamda** `./scripts/restore.sh <dosya> prod` ve
      `./scripts/restore-media.sh <dosya> prod` komutlarını gerçekten çalıştırıp geri
      yüklemenin çalıştığını doğrula — prod'un kendisinde deneme yapma (ikisi de yıkıcı:
      DB'yi/`media_data`'yı silip yeniden oluşturuyor).
- [ ] `infra/backups/` klasörünü (DB + `media/` alt klasörü) host dışına (rsync/scp)
      düzenli kopyalayan bir cron/job kur — script'lerin kendisi bunu yapmıyor, bilinçli
      olarak kapsam dışı bırakılmış.

## 10. Container restart / reboot sonrası otomatik ayağa kalkma

- [ ] Tüm servislerde `restart: unless-stopped` var (`infra/docker-compose.prod.yml` —
      postgres, postgres-backup, backend, customer-web, staff-web, caddy) → Docker
      daemon'ın kendisi (`systemctl enable docker`/`dockerd` boot'ta başlıyor mu) sunucu
      seviyesinde ayrıca doğrulanmalı, host'u reboot edip test et.
- [ ] `docker compose ps` ile reboot sonrası 6 servisin de `healthy`/`running` olduğunu
      doğrula.

## 11. Go-live sonrası smoke test

- [ ] `curl -f https://api.<domain>/actuator/health` → `{"status":"UP"}`
- [ ] `curl -f https://order.<domain>/api/health` ve
      `curl -f https://staff.<domain>/api/health`
- [ ] TLS sertifikalarının 3 domain için de geçerli (Let's Encrypt, tarayıcıda kilit
      ikonu) olduğunu doğrula.
- [ ] Staff-web'de gerçek `BUSINESS_ADMIN` girişi yap (`POST /api/staff/auth/login`).
- [ ] QR ile customer-web'i aç → menüyü gör → sepete ürün ekle → mock ödeme akışını
      uçtan uca tamamla (bilinçli olarak mock — madde 4).
- [ ] Kasa/staff-web'de siparişin göründüğünü, kabul edilip
      PREPARING→READY→COMPLETED akışının çalıştığını doğrula.
- [ ] Bir ürün görseli / gider fişi yükleyip görüntüleyerek media storage'ın (madde 6)
      uçtan uca çalıştığını doğrula.
- [ ] `docker compose logs -f --tail=100` ile ilk birkaç dakika hata/exception
      olmadığını izle.

---

**En kritik karar noktası:** (4) mock ödeme prod'da bilinçli olarak mı aktif
bırakılıyor — bu kod değişikliği değil, ürün/ops kararı gerektiriyor. (6) media volume
yedeği artık otomatik (`media-backup` sidecar); kalan tek operatör sorumluluğu
`infra/backups/`'ı host dışına düzenli kopyalamak (madde 9).
