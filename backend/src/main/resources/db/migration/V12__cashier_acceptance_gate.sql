-- Gap-analysis #1: "kasa kabul/red kapısı" - ödeme başarılı olduğunda sipariş artık
-- otomatik mutfağa düşmüyor (PAID kaldırıldı), AWAITING_STORE_ACCEPTANCE'a düşüyor;
-- mutfağa gitmek için kasanın ACCEPT'i, ya da REJECT + tam iade akışı gerekiyor
-- (docs/product-requirements.md Bölüm 6/7).
-- 'AWAITING_STORE_ACCEPTANCE' (25 karakter) mevcut VARCHAR(20) sınırını aşıyor.
ALTER TABLE customer_order ALTER COLUMN status TYPE VARCHAR(30);

-- Eski (V8) constraint'i önce düşür - aksi halde aşağıdaki UPDATE, henüz eski listede
-- olmayan 'AWAITING_STORE_ACCEPTANCE' değerini yazmaya çalışırken ona takılır.
ALTER TABLE customer_order DROP CONSTRAINT customer_order_status_check;

-- Eski koddan (bu değişiklikten önce) kalmış olası 'PAID' satırlarını yeni modeldeki
-- doğrudan karşılığına taşı - PAID, kasa kapısından önce "ödeme bitti, mutfağa geçiş
-- sırası" anlamına geliyordu, tam olarak AWAITING_STORE_ACCEPTANCE'ın karşılığı.
UPDATE customer_order SET status = 'AWAITING_STORE_ACCEPTANCE' WHERE status = 'PAID';

ALTER TABLE customer_order ADD CONSTRAINT customer_order_status_check
    CHECK (status IN (
        'DRAFT', 'CANCELLED', 'AWAITING_PAYMENT', 'PAYMENT_FAILED',
        'AWAITING_STORE_ACCEPTANCE', 'REJECTED_BY_STORE', 'IN_KITCHEN', 'READY', 'COMPLETED'
    ));

-- Red nedeni (Bölüm 6: "Red nedeni tutulmalıdır (reasonCode + optional note)").
ALTER TABLE customer_order ADD COLUMN rejection_reason_code VARCHAR(50);
ALTER TABLE customer_order ADD COLUMN rejection_note TEXT;

-- Bölüm 11: yeni CASHIER rolü.
ALTER TABLE staff_user DROP CONSTRAINT staff_user_role_check;
ALTER TABLE staff_user ADD CONSTRAINT staff_user_role_check
    CHECK (role IN ('PLATFORM_ADMIN', 'BUSINESS_ADMIN', 'BRANCH_MANAGER', 'CASHIER', 'KITCHEN_STAFF'));
