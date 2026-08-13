-- product-requirements.md Section 6 / 27: "İşletme bazında kasa accept timeout politikası"
-- resolved as a configurable-but-not-enforced branch setting - the cashier UI marks an
-- order gecikmiş/kritik once it has waited this long, but no automatic reject/refund
-- happens (Section 6: "Refund başarısız olursa ... sipariş sessizce iptal edildi
-- sayılmaz" - the same "no silent automatic outcome" spirit applies to acceptance).
ALTER TABLE branch
    ADD COLUMN store_acceptance_timeout_seconds INTEGER NOT NULL DEFAULT 300;
