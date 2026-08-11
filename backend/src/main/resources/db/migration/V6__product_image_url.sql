-- Frontend UX/UI Quality Baseline (docs/product-requirements.md Bölüm 14): product cards need
-- an optional image. No media-storage/CDN infrastructure - just a plain, nullable URL column.
ALTER TABLE product ADD COLUMN image_url VARCHAR(2048);
