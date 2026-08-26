-- Masa yaşam döngüsü: hiç TableVisit geçmişi olmayan masa hard-delete edilebilir,
-- TableVisit geçmişi olan masa ise archive/deactivate edilir (V32__branch_active.sql ile
-- aynı desen). table_qr_token'ın masadan bağımsız bir anlamı yok - Product hard-delete
-- CASCADE deseniyle aynı mantık (V36__menu_lifecycle_cascades.sql): masa hard-delete
-- edildiğinde kendi QR token'ları da otomatik silinir. table_visit FK'si RESTRICT
-- kalıyor - geçmişi olan masa asla sessizce cascade silinmez, application katmanında
-- (TenantService.deleteTable) engellenir.

ALTER TABLE restaurant_table ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE table_qr_token
    DROP CONSTRAINT table_qr_token_table_id_fkey,
    ADD CONSTRAINT table_qr_token_table_id_fkey
        FOREIGN KEY (table_id) REFERENCES restaurant_table (id) ON DELETE CASCADE;
