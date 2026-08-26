-- Aylık rapor gönderimi: owner_notification_log günlük gönderimle aynı denetim izi
-- tablosunu paylaşır (ayrı bir paralel tablo yerine genelleştirildi). report_type
-- mevcut tüm satırlarda DEFAULT 'DAILY' ile geriye dönük dolduruluyor - Postgres bu
-- ALTER TABLE ... ADD COLUMN ... DEFAULT işlemini tabloyu yeniden yazmadan yapar, ve
-- aşağıdaki CHECK constraint'i (mevcut satırlar dahil) doğrulanırken zaten backfill'in
-- doğru olduğunu (daily_close_report_id dolu + report_period NULL) kanıtlar.
ALTER TABLE owner_notification_log
    ADD COLUMN report_type VARCHAR(10) NOT NULL DEFAULT 'DAILY' CHECK (report_type IN ('DAILY', 'MONTHLY')),
    ADD COLUMN report_period DATE,
    ALTER COLUMN daily_close_report_id DROP NOT NULL;

ALTER TABLE owner_notification_log
    ADD CONSTRAINT owner_notification_log_type_consistency CHECK (
        (report_type = 'DAILY' AND daily_close_report_id IS NOT NULL AND report_period IS NULL)
        OR
        (report_type = 'MONTHLY' AND daily_close_report_id IS NULL AND report_period IS NOT NULL)
    );

-- Concurrency-safe idempotency: uygulama katmanındaki existsBy/backoff kontrolü artı
-- pg_try_advisory_xact_lock (bkz. MonthlyReportContactDispatcher) çift instance'ı
-- normalde zaten birbirinden ayırır; bu partial unique index, aynı (branch, ay, kişi)
-- için asla iki SENT+AUTO satırı oluşamayacağını DB seviyesinde garanti eden son
-- güvence katmanı - V38'deki case-insensitive email index'iyle aynı "app-check + DB
-- backstop" deseni. status='FAILED' satırları bu index'in dışında kalır, böylece
-- retry/backoff mantığı aynı (branch, ay, kişi) için birden fazla FAILED satırı
-- üretebilir.
CREATE UNIQUE INDEX idx_owner_notification_log_monthly_auto_sent
    ON owner_notification_log (branch_id, report_period, business_contact_id)
    WHERE report_type = 'MONTHLY' AND triggered_by = 'AUTO' AND status = 'SENT';

-- Backoff kontrolü (son deneme SENT mi, FAILED ise ne zaman) için en güncel satırı
-- hızlı bulmaya yarayan index.
CREATE INDEX idx_owner_notification_log_monthly_lookup
    ON owner_notification_log (report_type, branch_id, report_period, business_contact_id, attempted_at DESC);
