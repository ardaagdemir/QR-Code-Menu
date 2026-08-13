-- Gap-analysis #17 (product-requirements.md Section 13.3): QR/TableVisit volume alone can't
-- tell real footfall (one person can order for the whole table). guest_count is optional -
-- NULL until a customer (or staff) actually enters it; unknown is never treated as 1.
ALTER TABLE table_visit ADD COLUMN guest_count INTEGER;
