-- Branches created before MenuService.assignActiveCatalogToBranch existed never got a
-- default BranchProduct row for their business's existing active catalog products
-- (Section 5's opt-in rule with no auto-provisioning) - their customer menu stayed
-- empty even though the staff menu screen listed the (business-level) products.
-- One-time, idempotent backfill: insert an AVAILABLE, no-price-override row for every
-- (branch, active product) pair within the same business that's still missing one -
-- same NOT EXISTS pattern as V13's branch_business_hours backfill. Never touches a
-- pair that already has a row, so any branch already correctly provisioned, or a
-- product a staff member deliberately opted out of, keeps exactly what it had.
INSERT INTO branch_product (id, business_id, branch_id, product_id, availability, created_at, updated_at)
SELECT gen_random_uuid(), b.business_id, b.id, p.id, 'AVAILABLE', now(), now()
FROM branch b
JOIN product p ON p.business_id = b.business_id
WHERE p.active = true
  AND NOT EXISTS (
    SELECT 1 FROM branch_product bp WHERE bp.branch_id = b.id AND bp.product_id = p.id
  );
