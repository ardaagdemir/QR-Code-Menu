-- Menü yaşam döngüsü (kategori/ürün/option group/option rename+reorder+silme):
-- Product hard-delete edildiğinde canlı katalog zincirindeki "belongs-to" çocukları
-- (kendi branch_product opt-in satırları, kendi option group/option'ları) da otomatik
-- silinmeli - bunlar üründen bağımsız bir anlam taşımaz. order_item/order_item_option
-- bu zincire hiç dahil değil (V5'te bilinçli olarak product_id/option_id FK'siz
-- bırakılmış, isim/fiyat SNAPSHOT'ı taşıyorlar) - bu yüzden hard delete sipariş
-- geçmişini asla etkilemez.
--
-- menu_category -> product FK'si (product.category_id) kasıtlı olarak RESTRICT kalıyor:
-- kategori silme, içinde ürün varsa application katmanında (MenuService.deleteCategory)
-- engelleniyor - "tek tıkla bütün ürün hattını sil" riskini DB seviyesinde de yedekli
-- tutuyoruz, sessiz cascade yok.

ALTER TABLE branch_product
    DROP CONSTRAINT branch_product_product_id_fkey,
    ADD CONSTRAINT branch_product_product_id_fkey
        FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE;

ALTER TABLE product_option_group
    DROP CONSTRAINT product_option_group_product_id_fkey,
    ADD CONSTRAINT product_option_group_product_id_fkey
        FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE;

ALTER TABLE product_option
    DROP CONSTRAINT product_option_option_group_id_fkey,
    ADD CONSTRAINT product_option_option_group_id_fkey
        FOREIGN KEY (option_group_id) REFERENCES product_option_group (id) ON DELETE CASCADE;
