-- Milestone 2 (tenant module): Business/Branch/RestaurantTable/TableQrToken.
-- business_id is stored directly on every tenant-owned table (not only reachable via a
-- join through Branch) so every repository query can filter on it explicitly, per
-- docs/product-requirements.md Section 2 ("her repository sorgusu business_id'yi
-- açıkça WHERE koşuluna ekler").

CREATE TABLE business (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE branch (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    name VARCHAR(255) NOT NULL,
    ordering_enabled BOOLEAN NOT NULL DEFAULT true,
    opening_time TIME,
    closing_time TIME,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_branch_business_id ON branch (business_id);

-- Named "restaurant_table" (not "table") to avoid clashing with the SQL/JPA reserved word.
CREATE TABLE restaurant_table (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    label VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_restaurant_table_branch_label UNIQUE (branch_id, label)
);
CREATE INDEX idx_restaurant_table_business_id ON restaurant_table (business_id);
CREATE INDEX idx_restaurant_table_branch_id ON restaurant_table (branch_id);

CREATE TABLE table_qr_token (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    table_id UUID NOT NULL REFERENCES restaurant_table (id),
    token VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_table_qr_token_token UNIQUE (token)
);
CREATE INDEX idx_table_qr_token_business_id ON table_qr_token (business_id);
CREATE INDEX idx_table_qr_token_table_id ON table_qr_token (table_id);

-- Table <-> TableQrToken is one-to-many, but at most one ACTIVE token per table
-- (docs/product-requirements.md Section 5, decision #5/#8): revoked tokens are kept
-- (not deleted) for audit purposes, so this can only be enforced with a partial
-- unique index rather than a plain UNIQUE(table_id).
CREATE UNIQUE INDEX uq_table_qr_token_one_active_per_table
    ON table_qr_token (table_id)
    WHERE status = 'ACTIVE';
