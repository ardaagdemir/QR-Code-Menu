-- Gap-analysis #11 (product-requirements.md Section 15): mekan sahibine otomatik gün sonu
-- bildirimi. owner_notification_log: her gönderim denemesi (otomatik veya manuel) ayrı bir
-- satır - denetim izi, üzerine yazılmıyor. AUTO denemeler için idempotency
-- (daily_close_report_id, business_contact_id) çiftinin uygulama katmanında (repository
-- existsBy... sorgusuyla) kontrol edilmesiyle sağlanıyor - DB seviyesinde unique constraint
-- yok çünkü MANUAL yeniden gönderme bilinçli olarak aynı çift için yeni satırlar üretebilmeli.
CREATE TABLE owner_notification_log (
    id UUID PRIMARY KEY,
    daily_close_report_id UUID NOT NULL REFERENCES daily_branch_close_report (id),
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    business_contact_id UUID NOT NULL REFERENCES business_contact (id),
    recipient_email VARCHAR(255) NOT NULL,
    channel VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL')),
    status VARCHAR(10) NOT NULL CHECK (status IN ('SENT', 'FAILED')),
    error_message VARCHAR(2000),
    triggered_by VARCHAR(10) NOT NULL CHECK (triggered_by IN ('AUTO', 'MANUAL')),
    triggered_by_staff_user_id UUID REFERENCES staff_user (id),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_owner_notification_log_report_id ON owner_notification_log (daily_close_report_id);
CREATE INDEX idx_owner_notification_log_business_id ON owner_notification_log (business_id);
