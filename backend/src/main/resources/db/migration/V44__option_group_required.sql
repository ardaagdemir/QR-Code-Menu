-- Zorunluluk artık tekli/çoklu seçim tipinden bağımsız açık bir alan. Şimdiye kadar SINGLE
-- gruplar zaten zorunluydu (tam 1 seçim) - mevcut davranış korunsun diye onlar TRUE olur.
ALTER TABLE product_option_group ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE product_option_group SET required = TRUE WHERE selection_type = 'SINGLE';
