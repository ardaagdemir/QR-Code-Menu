-- Gap-analysis "Product alanları" (Bölüm 3.2): allergens/estimatedPreparationMinutes/
-- active-passive eklendi. Mevcut ürünler bozulmasın diye active=true varsayılan.
ALTER TABLE product ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE product ADD COLUMN estimated_preparation_minutes INTEGER;

-- Bölüm 3.2: "alerjenler mümkün olduğunca yapılandırılmış enum/reference listesi olarak
-- tutulur" - serbest metin değil, product_allergen bir değer koleksiyonu (Product'ın
-- kendi entity/repository'si olmayan ElementCollection'ı), tenant-scope her zaman
-- sahip Product satırı üzerinden geliyor.
CREATE TABLE product_allergen (
    product_id UUID NOT NULL REFERENCES product(id),
    allergen VARCHAR(30) NOT NULL,
    PRIMARY KEY (product_id, allergen)
);
