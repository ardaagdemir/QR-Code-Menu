-- Gap-analysis #4: haftanın her günü için ayrı çalışma saati (product-requirements.md
-- Section 12.2) - tek opening_time/closing_time çifti yetersizdi.
CREATE TABLE branch_business_hours (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    day_of_week VARCHAR(10) NOT NULL
        CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    opening_time TIME,
    closing_time TIME,
    closed BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_branch_business_hours_branch_day UNIQUE (branch_id, day_of_week)
);
CREATE INDEX idx_branch_business_hours_branch_id ON branch_business_hours (branch_id);

-- Mevcut şubelerin tek çift saatini (varsa) haftanın 7 gününe kopyala - eski
-- davranışı (her gün aynı saatler) birebir koruyoruz, veri kaybı olmadan.
INSERT INTO branch_business_hours (id, business_id, branch_id, day_of_week, opening_time, closing_time, closed)
SELECT gen_random_uuid(), b.business_id, b.id, day.name, b.opening_time, b.closing_time, false
FROM branch b
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS day(name)
WHERE b.opening_time IS NOT NULL AND b.closing_time IS NOT NULL;

ALTER TABLE branch DROP COLUMN opening_time;
ALTER TABLE branch DROP COLUMN closing_time;

-- Section 12.2: opsiyonel adres alanı.
ALTER TABLE branch ADD COLUMN address VARCHAR(500);
