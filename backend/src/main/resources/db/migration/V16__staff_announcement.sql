-- Gap-analysis #7 (product-requirements.md Section 18.1): staff announcements ("Yeni
-- menü/fiyat yayında" gibi duyurular), shown as a banner on every staff-web page.
CREATE TABLE staff_announcement (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business (id),
    title VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    target VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL REFERENCES staff_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ
);
CREATE INDEX idx_staff_announcement_business_id ON staff_announcement (business_id);

CREATE TABLE staff_announcement_branch (
    staff_announcement_id UUID NOT NULL REFERENCES staff_announcement (id),
    branch_id UUID NOT NULL REFERENCES branch (id),
    PRIMARY KEY (staff_announcement_id, branch_id)
);
