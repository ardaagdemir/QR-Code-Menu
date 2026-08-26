-- Rapor Alıcıları: aktif/pasif (Devre Dışı Bırak) modeli kaldırılıyor, yerine hard delete
-- geliyor - bir kayıt varsa aktiftir, gerekmiyorsa silinir. owner_notification_log geçmişi
-- (recipient_email zaten satırda snapshot olarak duruyor) korunmalı, bu yüzden FK'yi nullable
-- yapıp ON DELETE SET NULL'a çeviriyoruz - hem gelecekteki hard delete'ler hem de aşağıdaki
-- legacy temizlik için.
ALTER TABLE owner_notification_log
    ALTER COLUMN business_contact_id DROP NOT NULL,
    DROP CONSTRAINT owner_notification_log_business_contact_id_fkey,
    ADD CONSTRAINT owner_notification_log_business_contact_id_fkey
        FOREIGN KEY (business_contact_id) REFERENCES business_contact (id) ON DELETE SET NULL;

-- Devre Dışı Bırak ile pasife alınmış legacy kayıtları temizle - bildirim geçmişleri
-- yukarıdaki ON DELETE SET NULL sayesinde korunur.
DELETE FROM business_contact WHERE active = false;

ALTER TABLE business_contact DROP COLUMN active;
