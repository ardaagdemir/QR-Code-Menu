-- KDV alanı ürün ekranından tamamen kaldırıldı (staff-web menu screen) - Product.taxRatePercent
-- artık hiçbir yerde okunmuyor/yazılmıyor, kolon da kaldırılıyor.
ALTER TABLE product DROP COLUMN tax_rate_percent;
