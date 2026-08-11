-- Milestone 3 (menu module): Business-level catalog (MenuCategory/Product/
-- ProductOptionGroup/ProductOption) + branch-level BranchProduct join.
-- business_id is a direct column on every table here too, same discipline as V2
-- (Section 2 - explicit WHERE business_id, no join required for tenant checks).

CREATE TABLE menu_category (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    name VARCHAR(255) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_menu_category_business_id ON menu_category (business_id);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    category_id UUID NOT NULL REFERENCES menu_category (id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    -- Integer minor units (kuruş), no floating point (Section 5, "Para birimi" - confirmed
    -- rule). No dedicated Money value object yet - that lands in Milestone 4 alongside Order.
    base_price_minor_units BIGINT NOT NULL,
    tax_rate_percent INT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_business_id ON product (business_id);
CREATE INDEX idx_product_category_id ON product (category_id);

CREATE TABLE product_option_group (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    product_id UUID NOT NULL REFERENCES product (id),
    name VARCHAR(255) NOT NULL,
    selection_type VARCHAR(20) NOT NULL CHECK (selection_type IN ('SINGLE', 'MULTIPLE')),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_option_group_business_id ON product_option_group (business_id);
CREATE INDEX idx_product_option_group_product_id ON product_option_group (product_id);

CREATE TABLE product_option (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    option_group_id UUID NOT NULL REFERENCES product_option_group (id),
    name VARCHAR(255) NOT NULL,
    price_delta_minor_units BIGINT NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_option_business_id ON product_option (business_id);
CREATE INDEX idx_product_option_option_group_id ON product_option (option_group_id);

-- Opt-in join: a Product is NOT for sale at a Branch unless a row exists here
-- (Section 5, "BranchProduct varsayılan davranışı - opt-in", confirmed rule).
-- One row per (branch, product) - re-adding a product to a branch updates the existing
-- row (availability/price) rather than creating a duplicate.
CREATE TABLE branch_product (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    product_id UUID NOT NULL REFERENCES product (id),
    availability VARCHAR(20) NOT NULL CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE')),
    price_override_minor_units BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_branch_product_branch_id_product_id UNIQUE (branch_id, product_id)
);
CREATE INDEX idx_branch_product_business_id ON branch_product (business_id);
CREATE INDEX idx_branch_product_branch_id ON branch_product (branch_id);
CREATE INDEX idx_branch_product_product_id ON branch_product (product_id);
