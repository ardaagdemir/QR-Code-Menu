-- Platform admin panel: branch-level activate/deactivate (never a hard delete),
-- separate from the pre-existing ordering_enabled temporary-closed toggle.
ALTER TABLE branch ADD COLUMN active BOOLEAN NOT NULL DEFAULT true;
