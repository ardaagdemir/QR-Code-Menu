-- Milestone 2 (customer-session module): AnonymousCustomerSession/TableVisit.
-- AnonymousCustomerSession deliberately has no business_id: the same anonymous browser
-- identity can visit multiple businesses/branches/tables over time
-- (docs/product-requirements.md Section 5).

CREATE TABLE anonymous_customer_session (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE table_visit (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    table_id UUID NOT NULL REFERENCES restaurant_table (id),
    anonymous_customer_session_id UUID NOT NULL REFERENCES anonymous_customer_session (id),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_table_visit_business_id ON table_visit (business_id);
-- Supports the "continue the existing visit for this session+table if still fresh,
-- otherwise start a new one" lookup (Section 5).
CREATE INDEX idx_table_visit_session_table ON table_visit (anonymous_customer_session_id, table_id);
