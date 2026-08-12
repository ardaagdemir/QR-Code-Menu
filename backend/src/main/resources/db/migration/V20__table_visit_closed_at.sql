-- Gap-analysis #13 (docs/gap-analysis.md Section 2 "Session/TableVisit TTL"): no scheduled
-- job ever closed a TableVisit once its VISIT_TTL (6h, CustomerSessionService) elapsed, so a
-- caller holding a stale session cookie could keep acting on an old visit indefinitely.
-- closed_at is NULL while the visit is open; a scheduler sets it once last_activity_at is
-- older than VISIT_TTL, and getOwnedTableVisit then treats the visit as gone (404).
ALTER TABLE table_visit ADD COLUMN closed_at TIMESTAMPTZ;
CREATE INDEX idx_table_visit_open_stale ON table_visit (last_activity_at) WHERE closed_at IS NULL;
