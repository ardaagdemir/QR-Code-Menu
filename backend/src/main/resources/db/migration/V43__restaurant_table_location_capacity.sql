-- Masa konumu (İç Mekan / Dış Mekan) ve opsiyonel kapasite. Mevcut masalar veri
-- kaybetmeden INDOOR varsayılanına düşer (DEFAULT kalıcı olarak korunuyor, çünkü
-- internal API hâlâ location belirtmeden masa oluşturabiliyor - TenantService.createTable).

ALTER TABLE restaurant_table ADD COLUMN location VARCHAR(16) NOT NULL DEFAULT 'INDOOR';
ALTER TABLE restaurant_table ADD COLUMN capacity INTEGER NULL;
ALTER TABLE restaurant_table ADD CONSTRAINT chk_restaurant_table_capacity_positive CHECK (capacity IS NULL OR capacity > 0);
