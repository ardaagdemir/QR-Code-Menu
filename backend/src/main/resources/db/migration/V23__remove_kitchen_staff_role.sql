-- Product decision: no separate Mutfak/KDS screen or role anymore - sipariş
-- operasyonunun tamamı (kabul/red + PREPARING/READY/COMPLETED akışı) Kasa ekranından
-- BUSINESS_ADMIN/BRANCH_MANAGER/CASHIER tarafından yürütülür (docs/product-requirements.md
-- Bölüm 6/8/11). Var olabilecek KITCHEN_STAFF satırlarını CASHIER'a taşı - CASHIER artık
-- aynı işi (Permission.ORDER_PREPARE dahil) yapabiliyor, mevcut branch ataması
-- (staff_user_branch) da CASHIER için zaten geçerli bir model.
UPDATE staff_user SET role = 'CASHIER' WHERE role = 'KITCHEN_STAFF';

ALTER TABLE staff_user DROP CONSTRAINT staff_user_role_check;
ALTER TABLE staff_user ADD CONSTRAINT staff_user_role_check
    CHECK (role IN ('PLATFORM_ADMIN', 'BUSINESS_ADMIN', 'BRANCH_MANAGER', 'CASHIER'));
