-- Gap-analysis #16: every user-facing staff role has one trusted active branch.
-- Existing ambiguous data must be resolved deliberately before this migration runs;
-- never guess an active branch or delete assignments here.
DO $$
DECLARE
    ambiguous_user_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO ambiguous_user_count
    FROM (
        SELECT staff.id
        FROM staff_user staff
        LEFT JOIN staff_user_branch assignment ON assignment.staff_user_id = staff.id
        LEFT JOIN branch assigned_branch ON assigned_branch.id = assignment.branch_id
        WHERE staff.role <> 'PLATFORM_ADMIN'
        GROUP BY staff.id
        HAVING COUNT(assignment.id) <> 1
            OR COUNT(assignment.id) FILTER (
                WHERE assigned_branch.business_id = staff.business_id
            ) <> 1
    ) ambiguous_users;

    IF ambiguous_user_count > 0 THEN
        RAISE EXCEPTION
            'V25 cannot enforce single active branch: % user-facing staff user(s) have zero, multiple, or cross-business branch assignments. Resolve them explicitly before retrying.',
            ambiguous_user_count;
    END IF;
END
$$;

-- PostgreSQL partial indexes cannot predicate on a joined staff_user.role. Use a
-- constraint trigger so PLATFORM_ADMIN remains genuinely outside this invariant.
CREATE OR REPLACE FUNCTION enforce_user_facing_staff_single_branch()
RETURNS TRIGGER AS $$
BEGIN
    -- Serialize competing assignments for the same staff user so the joined-role
    -- check remains concurrency-safe like a unique index would be.
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.staff_user_id::text, 0));
    IF EXISTS (
        SELECT 1
        FROM staff_user staff
        JOIN branch assigned_branch ON assigned_branch.id = NEW.branch_id
        WHERE staff.id = NEW.staff_user_id
          AND staff.role <> 'PLATFORM_ADMIN'
          AND assigned_branch.business_id <> staff.business_id
    ) THEN
        RAISE EXCEPTION
            'User-facing staff user % cannot be assigned to a branch from another business',
            NEW.staff_user_id;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM staff_user staff
        WHERE staff.id = NEW.staff_user_id
          AND staff.role <> 'PLATFORM_ADMIN'
    ) AND (
        SELECT COUNT(*)
        FROM staff_user_branch assignment
        WHERE assignment.staff_user_id = NEW.staff_user_id
    ) > 1 THEN
        RAISE EXCEPTION
            'User-facing staff user % cannot have multiple branch assignments',
            NEW.staff_user_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER staff_user_single_branch
AFTER INSERT OR UPDATE OF staff_user_id, branch_id ON staff_user_branch
DEFERRABLE INITIALLY IMMEDIATE
FOR EACH ROW
EXECUTE FUNCTION enforce_user_facing_staff_single_branch();

-- Audit visibility is branch-scoped as well. Historical rows remain null because a
-- current assignment cannot safely prove which branch an earlier action belonged to.
ALTER TABLE audit_log_entry ADD COLUMN branch_id UUID REFERENCES branch (id);

CREATE INDEX idx_audit_log_entry_business_branch_created_at
    ON audit_log_entry (business_id, branch_id, created_at DESC);

CREATE OR REPLACE FUNCTION assign_audit_branch_from_staff()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.actor_staff_user_id IS NOT NULL THEN
        SELECT assignment.branch_id
        INTO NEW.branch_id
        FROM staff_user_branch assignment
        JOIN staff_user staff ON staff.id = assignment.staff_user_id
        WHERE assignment.staff_user_id = NEW.actor_staff_user_id
          AND staff.role <> 'PLATFORM_ADMIN';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_entry_assign_branch
BEFORE INSERT ON audit_log_entry
FOR EACH ROW
EXECUTE FUNCTION assign_audit_branch_from_staff();
