# QR Menü & Sipariş Platformu

QR kod ile açılan mobil web menüsü üzerinden sipariş ve online ödeme alan bir restoran platformu.

Ürün gereksinimleri, mimari kararlar ve milestone planı için: [`docs/product-requirements.md`](docs/product-requirements.md).

## Monorepo yapısı

```
qr-menu/
├── backend/               # Java 21 + Spring Boot (Maven), modular monolith
├── frontend/
│   ├── customer-web/      # Next.js — müşterinin QR ile açtığı menü/sipariş uygulaması
│   └── staff-web/         # Next.js — restoran personelinin kullandığı kasa/yönetim paneli
├── infra/                 # Docker Compose, Caddy (TLS), altyapı tanımları
└── docs/                  # Ürün/mimari dokümantasyonu, ADR'ler
```

## Projeyi ayağa kaldırma (adım adım)

Bu bölüm, bilgisayarında daha önce hiç bu proje üzerinde çalışmamış birinin projeyi sıfırdan
çalışır hale getirmesi için yazıldı. En kolay ve önerilen yol Docker Compose'dur: tek komutla
backend, iki frontend uygulaması ve veritabanı otomatik ayağa kalkar — bilgisayarına ayrıca
Java veya Node.js kurman gerekmez.

### 1. Docker Desktop'ı kur ve aç

- macOS/Windows: https://www.docker.com/products/docker-desktop/ adresinden indir, kur ve uygulamayı aç
  (Docker Desktop simgesi "running" durumuna geçmeli — ilk açılış biraz sürebilir).
- Linux: dağıtımına göre [Docker Engine + Compose plugin](https://docs.docker.com/engine/install/) kur.
- Terminalde doğrula — ikisi de bir sürüm numarası döndürmeli:

  ```bash
  docker --version
  docker compose version
  ```

### 2. Projeyi bilgisayarına indir

```bash
git clone https://github.com/ardaagdemir/QR-Code-Menu.git
cd QR-Code-Menu
```

(Projeyi zaten indirdiysen bu adımı atla, sadece proje klasörüne `cd` ile gir.)

### 3. Ortam değişkenleri dosyasını oluştur

```bash
cd infra
cp .env.example .env
```

`.env` dosyası veritabanı şifresi gibi yerel geliştirme ayarlarını içerir; varsayılan değerleriyle
olduğu gibi bırakabilirsin. Bu dosya yalnızca yerel geliştirme içindir, gerçek bir sunucuya bu
haliyle **asla** konulmamalı (zaten `.gitignore` ile commit'lenmesi engellenir).

### 4. Her şeyi tek komutla ayağa kaldır

Hâlâ `infra/` klasöründeyken:

```bash
docker compose up --build
```

İlk çalıştırmada imajlar derlendiği için birkaç dakika sürebilir; sonraki çalıştırmalar çok daha
hızlı olur. Terminalde loglar akmaya devam eder — her şey hazır olduğunda durdurmak için `Ctrl+C`,
sonraki seferler için arka planda çalıştırmak istersen `docker compose up --build -d` kullanabilirsin.

Her şey ayağa kalktığında şurada açılır:

| Ne | Adres | Ne işe yarar |
|----|-------|---------------|
| Müşteri menüsü | http://localhost:3000 | QR kod ile açılan müşteri sipariş ekranı |
| Personel paneli | http://localhost:3002 | Kasa/yönetim ekranları |
| Backend health | http://localhost:8080/actuator/health | Backend ayakta mı kontrolü — `{"status":"UP"}` dönmeli |
| Test e-posta kutusu (Mailhog) | http://localhost:8025 | Sistemin gönderdiği bildirim e-postalarını gösterir |
| PostgreSQL | `localhost:5433` | Veritabanına doğrudan bağlanmak istersen (kullanıcı/şifre `infra/.env` içinde) |

Bu adımdan sonra `http://localhost:3002`'ye girsen bile giriş yapabileceğin bir hesap,
`http://localhost:3000`'e girsen bile açacağın bir müşteri menüsü **yoktur** — veritabanı tamamen
boş gelir. Devam etmeden önce aşağıdaki "İlk işletmeyi kurma" adımını uygulaman gerekir.

### 5. İlk işletmeyi kurma (bootstrap)

Personel paneline giriş yapabilmek için önce bir işletme ve bir personel hesabı olması gerekir, ama
henüz giriş yapmış kimse olmadığı için normal (oturum/cookie gerektiren) API'ler kullanılamaz — bu
"tavuk-yumurta" sorununu çözmek için backend'de ayrı, paylaşılan bir tokenla korunan `/internal/**`
adında bir "bootstrap" API'si var. Bu API sadece bu ilk kurulum için kullanılır; sonrasında şube,
masa ve QR yönetimi personel panelinin kendi ekranlarından yapılabilir.

Kullanacağın token, `infra/.env` dosyasındaki `INTERNAL_ADMIN_TOKEN` değeridir (3. adımda
`.env.example`'dan kopyaladıysan varsayılan değer `dev-internal-admin-token-change-me`'dir). Her
istekte bu değeri `X-Internal-Admin-Token` header'ında göndereceksin.

Terminalde, sırayla, her adımın çıktısındaki `id` değerini bir sonraki adımda kullanarak:

**5.1. İşletme (business) oluştur**

```bash
curl -X POST http://localhost:8080/internal/businesses \
  -H "X-Internal-Admin-Token: dev-internal-admin-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Restoran"}'
```

Cevaptaki `"id"` alanını not al — bir sonraki her adımda `{businessId}` yerine bunu kullanacaksın.

**5.2. İlk personel hesabını oluştur**

Bu, personel panelinde (`localhost:3002`) giriş yapacağın e-posta/şifredir. `role` olarak
`BUSINESS_ADMIN` ver — işletmenin tüm yönetim yetkilerine (şube/masa/menü/personel/rapor) sahip tek
roldür:

```bash
curl -X POST http://localhost:8080/internal/businesses/{businessId}/staff-users \
  -H "X-Internal-Admin-Token: dev-internal-admin-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@qrmenu.local","password":"changeme123","role":"BUSINESS_ADMIN"}'
```

Şifre en az 8 karakter olmalı. `{businessId}` yerine 5.1'de aldığın `id`'yi yaz.

**5.3. Şube oluştur**

```bash
curl -X POST http://localhost:8080/internal/businesses/{businessId}/branches \
  -H "X-Internal-Admin-Token: dev-internal-admin-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{"name":"Merkez Şube"}'
```

Cevaptaki `"id"` alanını not al — bu `{branchId}`.

**5.4. Masa oluştur**

```bash
curl -X POST http://localhost:8080/internal/businesses/{businessId}/branches/{branchId}/tables \
  -H "X-Internal-Admin-Token: dev-internal-admin-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{"label":"Masa 1"}'
```

Cevaptaki `"id"` alanını not al — bu `{tableId}`.

**5.5. Masa için QR token üret**

```bash
curl -X POST http://localhost:8080/internal/businesses/{businessId}/tables/{tableId}/qr-tokens \
  -H "X-Internal-Admin-Token: dev-internal-admin-token-change-me"
```

Cevaptaki `"token"` alanı, müşteri menüsünü açan bağlantının son parçasıdır.

**Artık her iki tarafı da açabilirsin:**

- **Personel / işletme sahibi** → http://localhost:3002 adresine git, 5.2'de oluşturduğun
  e-posta/şifre ile giriş yap → kasa/yönetim ekranını görürsün. Buradan ikinci bir şube, masa
  veya QR token eklemek istersen artık `/internal/**`'e gerek yok — panelin kendi ekranlarından
  (Şubeler / Masalar) yapılır.
- **Müşteri** → http://localhost:3000/t/{5.5'te aldığın token} adresine git → menü açılır, sipariş
  verilebilir. Gerçek bir restoranda bu adres bir QR koda gömülür ve müşteri sadece QR'ı okutur;
  yerelde test ederken token'ı adrese elle eklemek yeterlidir.

### 6. Durdurma / temizleme

```bash
docker compose down        # servisleri durdurur, veritabanı verileri kalır
docker compose down -v     # servisleri durdurur ve veritabanı verilerini de siler (bootstrap'ı da siler, tekrar 5. adımı yapman gerekir)
```

## Docker olmadan, her parçayı kendi bilgisayarında çalıştırma

Backend veya frontend kodu üzerinde hızlı iterasyon yapıyorsan (her değişiklikte Docker imajını
yeniden derlemek yavaş kalır), her parçayı doğrudan kendi bilgisayarında çalıştırabilirsin.

### Gereksinimler

- Java 21 — doğrula: `java -version`
- Node.js 20+ ve npm — doğrula: `node -v` ve `npm -v`
- Bir PostgreSQL. En kolayı: yalnızca veritabanını ve mailhog'u Docker'da bırakmak, backend/frontend'i
  yerelde çalıştırmak:

  ```bash
  cd infra
  docker compose up postgres mailhog
  ```

### Backend'i çalıştırma

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

(`./mvnw` Maven'i kendi indirir, ayrıca Maven kurmana gerek yok.) `local` profili Postgres'e
Docker Compose dışından, `localhost:5433` üzerinden bağlanır. Backend ayaktaysa
`http://localhost:8080/actuator/health` adresi `{"status":"UP"}` döner.

### Testler

```bash
cd backend
./mvnw test      # unit + Testcontainers ile integration test (gerçek PostgreSQL, Docker gerektirir)
./mvnw verify
```

### Müşteri web uygulamasını çalıştırma (customer-web)

```bash
cd frontend/customer-web
npm install
npm run dev
```

http://localhost:3000 adresinde açılır. Backend'in `http://localhost:8080`'de çalışıyor olması
gerekir — bu varsayılan ayardır, ek yapılandırma gerekmez.

### Personel panelini çalıştırma (staff-web)

```bash
cd frontend/staff-web
npm install
npm run dev
```

http://localhost:3002 adresinde açılır.

## Sık karşılaşılan sorunlar

- **`docker compose up` "Cannot connect to the Docker daemon" hatası veriyor** → Docker Desktop
  uygulamasının açık ve çalışır durumda olduğundan emin ol.
- **3000/3002/8080/5433 portlarından biri "already in use" hatası veriyor** → o portu kullanan
  başka bir uygulamayı kapat, ya da `infra/docker-compose.yml` içinde ilgili port eşlemesini değiştir.
- **Backend ayağa kalkmıyor / `INTERNAL_ADMIN_TOKEN` ile ilgili bir hata görüyorsun** → 3. adımdaki
  `infra/.env` dosyasını oluşturduğundan emin ol.
- **Yerelde (Docker olmadan) backend Postgres'e bağlanamıyor** → `-Dspring-boot.run.profiles=local`
  bayrağını unutmadığından ve `docker compose up postgres mailhog` komutunun ayrı bir terminalde hâlâ
  çalıştığından emin ol.

## Production'a çalıştırma

```bash
cd infra
cp .env.prod.example .env.prod   # gerçek değerleri girin, commit etmeyin
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

TLS termination Caddy ile yapılır (otomatik Let's Encrypt) - `infra/Caddyfile`, `infra/docker-compose.prod.yml`.
Gerekli tüm domain/secret/URL değişkenleri için `infra/.env.prod.example` dosyasına bakın; biri eksikse
`docker compose` build'e girmeden hata verir.

## Backup / restore

`docker compose up` ile birlikte iki ayrı sidecar servis de otomatik ayağa kalkar - ikisi de `backend`
container'ından bağımsız çalışır ve eski yedekleri otomatik siler (varsayılan: 7 gün):

- `postgres-backup` (`infra/docker/postgres-backup/`) - veritabanını periyodik olarak (varsayılan:
  günde bir) yedekleyip `infra/backups/` klasörüne gzip'li bir `.sql.gz` dosyası olarak yazar.
- `media-backup` (`infra/docker/media-backup/`) - `media_data` volume'ünü (ürün görselleri + private
  gider fişleri) aynı sıklıkla `infra/backups/media/` klasörüne gzip'li bir `.tar.gz` arşivi olarak
  yazar; volume'e sadece salt-okunur (`:ro`) bağlı, hiçbir zaman yazamaz.

İkisi de host'ta (Docker volume değil, bind-mount) saklanır. Sıklık/saklama süresi ikisi için ortak
`infra/.env`(.prod)`'daki `BACKUP_INTERVAL_SECONDS`/`BACKUP_RETENTION_DAYS` ile ayarlanabilir (bkz.
`.env.example`).

Manuel/anlık bir yedek almak veya bir yedeği geri yüklemek için (`infra/` klasöründeyken):

```bash
./scripts/backup.sh                                              # dev  (infra/.env)  - DB + media
./scripts/backup.sh prod                                         # prod (infra/.env.prod) - DB + media

./scripts/restore.sh backups/qrmenu_manual_20260814T120000Z.sql.gz        # dev  - DB
./scripts/restore.sh backups/qrmenu_manual_20260814T120000Z.sql.gz prod   # prod - DB

./scripts/restore-media.sh backups/media/media_manual_20260814T120000Z.tar.gz        # dev  - media
./scripts/restore-media.sh backups/media/media_manual_20260814T120000Z.tar.gz prod   # prod - media
```

`restore.sh` ve `restore-media.sh` **yıkıcıdır** - hedef veritabanını/`media_data` volume'ünü silip
yeniden oluşturur, bu yüzden ikisi de elle bir onay metni yazılmasını ister (`restore.sh`: veritabanı
adı, `restore-media.sh`: `RESTORE MEDIA` sabit metni). S3/bulut depolama entegrasyonu yok (bilinçli
olarak kapsam dışı) - `infra/backups/` klasörünü (DB + media) başka bir yere kopyalamak (`rsync`/`scp`)
operatörün sorumluluğunda.

## Katkı / geliştirme sırası

Geliştirme, [`docs/product-requirements.md`](docs/product-requirements.md) Bölüm 9'daki milestone sırasını (M1—M13)
takip eder; ilerleme detayları [`docs/development-progress.md`](docs/development-progress.md) içinde loglanır. Yeni
bir milestone'a geçmeden önce dokümandaki ilgili bölüm gözden geçirilmelidir.
