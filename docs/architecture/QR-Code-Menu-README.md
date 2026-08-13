# QR Menü, Sipariş ve Restoran Operasyon Platformu

QR kod üzerinden sipariş ve ödeme akışını; kasa, mutfak, menü, şube, raporlama ve temel gider yönetimiyle birleştiren restoran operasyon platformu.

Tek şubeli işletmelerin yanı sıra çok şubeli zincir işletmeleri de destekleyecek şekilde tasarlanmıştır.

## Neler Yapabilir?

### Müşteri
- Masaya özel QR kod ile menüye erişim
- Ürün, opsiyon ve adet seçerek sepet oluşturma
- Online ödeme akışı
- İşletmenin sipariş kabul/red durumunu canlı takip etme
- Mutfak hazırlık ve hazır durumunu takip etme
- Sipariş geçmişi / takip bağlantısı
- Opsiyonel misafir sayısı girişi

### Kasa / Mutfak
- Ödemesi doğrulanmış siparişleri anlık görüntüleme
- Siparişi kabul veya reddetme
- Reddedilen sipariş için iade akışı
- KDS üzerinden hazırlık sürecini yönetme
- Geciken kasa onaylarında uyarı
- Günlük satış ve operasyon verilerini görüntüleme

### İşletme Yönetimi
- İşletme, şube, masa ve QR yönetimi
- Merkezi menü ve şube bazlı ürün/fiyat/bulunabilirlik yönetimi
- Personel, rol ve yetki yönetimi
- Satış, ürün ve şube bazlı raporlama
- Excel dışa aktarma ve gün sonu raporları
- Gider ve tekrarlayan gider yönetimi
- Çok şubeli işletmeler için karşılaştırmalı raporlar
- Audit log ve temel operasyon kayıtları

## Teknoloji

- **Backend:** Java 21, Spring Boot, Maven
- **Database:** PostgreSQL, Flyway
- **Frontend:** Next.js, React, TypeScript
- **Architecture:** Modular Monolith
- **Realtime:** Server-Sent Events (SSE)
- **Local Infrastructure:** Docker Compose
- **Tests:** JUnit, Testcontainers

## Projeyi Ayağa Kaldırma

En kolay yöntem Docker Compose kullanmaktır.

### Gereksinimler

- Docker
- Docker Compose

### 1. Repository'yi klonlayın

```bash
git clone <repository-url>
cd QR-Code-Menu
```

### 2. Environment dosyasını oluşturun

```bash
cd infra
cp .env.example .env
```

`.env` içindeki local geliştirme değerlerini ihtiyacınıza göre düzenleyebilirsiniz.

### 3. Uygulamayı başlatın

```bash
docker compose up --build
```

Servisler ayağa kalktıktan sonra:

- **Müşteri uygulaması:** http://localhost:3000
- **Personel/Admin uygulaması:** http://localhost:3002
- **Backend:** http://localhost:8080
- **Backend health:** http://localhost:8080/actuator/health
- **MailHog:** http://localhost:8025
- **PostgreSQL:** localhost:5433

Uygulamayı durdurmak için:

```bash
docker compose down
```

Veritabanı volume'unu da silerek temiz başlangıç yapmak için:

```bash
docker compose down -v
```

## Docker Kullanmadan Geliştirme

### Backend

Java 21 gereklidir.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Testler:

```bash
./mvnw test
```

### Customer Web

Node.js 20 kullanılması önerilir.

```bash
cd frontend/customer-web
npm install
npm run dev
```

### Staff Web

```bash
cd frontend/staff-web
npm install
npm run dev
```

Staff uygulaması varsayılan olarak `http://localhost:3002` üzerinde çalışır.

## Önemli Notlar

- Gerçek ödeme sağlayıcısı henüz seçilmemiştir. Geliştirme ortamında gerçek webhook davranışını taklit eden mock payment provider kullanılmaktadır.
- Kart bilgileri uygulama tarafından saklanmaz.
- Ürün görselleri local geliştirmede media storage adapter üzerinden yönetilir.
- Gider fişleri public olarak servis edilmez; yetki ve tenant kontrolü ile erişilir.
- `infra/.env` gerçek secret içerebilir ve Git repository'sine eklenmemelidir.

## Dokümantasyon

Detaylı ürün davranışları ve roadmap:

```text
docs/product-requirements.md
```

Gerçekleşen geliştirmeler:

```text
docs/development-progress.md
```

---

> Proje aktif olarak geliştirilmektedir. Production deployment ve gerçek ödeme sağlayıcısı entegrasyonu ayrı aşamalarda tamamlanacaktır.
