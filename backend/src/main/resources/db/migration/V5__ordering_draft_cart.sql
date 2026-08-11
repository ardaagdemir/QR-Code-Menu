-- Milestone 4 (ordering module): DRAFT Order/cart + OrderItem/OrderItemOption snapshots.
-- Table named "customer_order" (not "order") to avoid the SQL reserved word, same
-- reasoning as "restaurant_table" in V2.

CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    table_visit_id UUID NOT NULL REFERENCES table_visit (id),
    -- Only DRAFT/CANCELLED exist as of Milestone 4; AWAITING_PAYMENT and later states
    -- (Section 6) are added by later migrations when the milestones that use them land.
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'CANCELLED')),
    -- SHA-256 hex digest of orderTrackingToken - the raw value is never stored
    -- (Section 2: "token'ın açık değeri değil, hash'i saklanır").
    tracking_token_hash VARCHAR(64) NOT NULL,
    total_minor_units BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_order_tracking_token_hash UNIQUE (tracking_token_hash)
);
CREATE INDEX idx_customer_order_business_id ON customer_order (business_id);
CREATE INDEX idx_customer_order_table_visit_id ON customer_order (table_visit_id);
-- At most one DRAFT (active cart) per table visit at a time.
CREATE UNIQUE INDEX uq_customer_order_one_draft_per_visit
    ON customer_order (table_visit_id)
    WHERE status = 'DRAFT';
-- Supports the TTL cleanup scan (Section 7: 2h inactivity -> CANCELLED).
CREATE INDEX idx_customer_order_status_last_activity ON customer_order (status, last_activity_at);

-- productId deliberately has no FK constraint to product(id): OrderItem stores an
-- immutable name/price SNAPSHOT (Section 5, "Product'a canlı referans değil"), so the
-- row must remain valid even if the referenced product is later changed or removed.
CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order (id),
    product_id UUID NOT NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    unit_price_minor_units BIGINT NOT NULL,
    ordered_quantity INT NOT NULL,
    -- Kitchen accept/reject decision (Section 5/6) - always 0/0 until Milestone 6 sets them.
    accepted_quantity INT NOT NULL DEFAULT 0,
    rejected_quantity INT NOT NULL DEFAULT 0,
    line_total_minor_units BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_item_order_id ON order_item (order_id);

-- Same snapshot reasoning as order_item: option_id has no FK constraint.
CREATE TABLE order_item_option (
    id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL REFERENCES order_item (id),
    option_id UUID,
    option_name_snapshot VARCHAR(255) NOT NULL,
    price_delta_minor_units BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_item_option_order_item_id ON order_item_option (order_item_id);
