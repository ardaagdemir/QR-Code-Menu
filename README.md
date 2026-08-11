# QR Menü & Sipariş Platformu

QR kod ile açılan mobil web menüsü üzerinden sipariş ve online ödeme alan bir restoran platformu.

Ürün gereksinimleri, mimari kararlar ve milestone planı için: [`docs/product-requirements.md`](docs/product-requirements.md).

> **Durum:** Milestone 1 — Foundation. Yalnızca proje iskeleti (backend health/DB/Flyway bağlantısı, customer-web
> placeholder, Docker Compose) mevcuttur. Domain modelleri (Business, Branch, Menu, Order, ...) henüz yoktur.

## Monorepo yapısı

```
qr-menu/
├── backend/          # Java 21 + Spring Boot (Maven), modular monolith
├── frontend/
│   └── customer-web/ # Next.js (TypeScript, App Router) — müşteri web uygulaması
├── infra/            # Docker Compose, altyapı tanımları
└── docs/             # Ürün/mimari dokümantasyonu, ADR'ler
```

## Gereksinimler

- Java 21
- Maven (veya yalnızca `./mvnw` — Maven kurulu olmasa da çalışır)
- Node.js 20+ ve npm
- Docker + Docker Compose

## Backend'i çalıştırma

```bash
cd backend
./mvnw spring-boot:run
```

Varsayılan olarak `postgres` host adını (Docker Compose servis adı) kullanır. Docker dışında, bir Postgres'e
`localhost`'tan bağlanmak için `local` profiliyle çalıştırın:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Health endpoint: `GET http://localhost:8080/actuator/health`

### Testler

```bash
cd backend
./mvnw test      # unit + Testcontainers ile integration test (gerçek PostgreSQL, Docker gerektirir)
./mvnw verify
```

## Frontend'i çalıştırma

```bash
cd frontend/customer-web
npm install
npm run dev
```

`http://localhost:3000` adresinde açılır.

## Hepsini Docker Compose ile çalıştırma

```bash
cd infra
cp .env.example .env   # gerekirse değerleri düzenleyin
docker compose up --build
```

- customer-web: http://localhost:3000
- backend health: http://localhost:8080/actuator/health
- PostgreSQL: `localhost:5433` (konteyner içi: `postgres:5432`; host'ta 5433 kullanılır çünkü 5432 yerel bir
  PostgreSQL kurulumuyla çakışabilir)

## Katkı / geliştirme sırası

Geliştirme, [`docs/product-requirements.md`](docs/product-requirements.md) Bölüm 9'daki milestone sırasını takip
eder. Şu an yalnızca **Milestone 1 — Foundation** tamamlanmıştır; sonraki milestone'lara geçmeden önce dokümandaki
ilgili bölüm gözden geçirilmelidir.
