-- Milestone 8: StaffUser gerçek authentication + roller + permission katmanı + audit.

CREATE TABLE staff_user (
    id UUID PRIMARY KEY,
    -- Null only for PLATFORM_ADMIN (Section 5: "PLATFORM_ADMIN: business_id = null, tek istisna").
    business_id UUID REFERENCES business (id),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('PLATFORM_ADMIN', 'BUSINESS_ADMIN', 'BRANCH_MANAGER', 'KITCHEN_STAFF')),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_user_email UNIQUE (email)
);
CREATE INDEX idx_staff_user_business_id ON staff_user (business_id);

-- Section 5: "StaffUser *-* Branch (yalnızca BRANCH_MANAGER/KITCHEN_STAFF için scoping)".
-- BUSINESS_ADMIN needs no rows here - their scope is implicitly every branch in their
-- business, checked via business_id alone.
CREATE TABLE staff_user_branch (
    id UUID PRIMARY KEY,
    staff_user_id UUID NOT NULL REFERENCES staff_user (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    CONSTRAINT uq_staff_user_branch UNIQUE (staff_user_id, branch_id)
);

-- Mirrors anonymous_customer_session's shape (Milestone 2) - a DB-backed session row,
-- the cookie carries only this row's id, never the staff_user_id directly.
CREATE TABLE staff_session (
    id UUID PRIMARY KEY,
    staff_user_id UUID NOT NULL REFERENCES staff_user (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Section 3: "Kritik yönetimsel aksiyonların (menü/BranchProduct değişikliği, iade, QR
-- revoke, ordering-enabled toggle vb.) business_id ile birlikte audit log'a yazılması."
-- actor_staff_user_id is nullable for the rare system-initiated action with no human
-- actor; details is a free-form JSON blob (same "no premature structure" reasoning as
-- outbox_event.payload).
CREATE TABLE audit_log_entry (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    actor_staff_user_id UUID REFERENCES staff_user (id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_entry_business_id_created_at ON audit_log_entry (business_id, created_at DESC);
