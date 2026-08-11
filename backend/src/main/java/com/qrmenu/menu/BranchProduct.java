package com.qrmenu.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The opt-in join between a Branch and a Product (Section 5, "BranchProduct varsayılan
 * davranışı"): no row for a (branch, product) pair means the product is not for sale
 * there and must not appear in the public menu at all. One row per pair (Section 5's
 * "hâlâ opt-in, hâlâ satır-bazlı" - re-adding a product to a branch updates this row
 * rather than creating a duplicate; a bulk "add all products to branch" admin
 * convenience, if ever built, would still go through this same row-based upsert).
 */
@Entity
@Table(name = "branch_product")
public class BranchProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BranchProductAvailability availability;

    @Column(name = "price_override_minor_units")
    private Long priceOverrideMinorUnits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BranchProduct() {
        // JPA
    }

    public BranchProduct(
            UUID businessId,
            UUID branchId,
            UUID productId,
            BranchProductAvailability availability,
            Long priceOverrideMinorUnits) {
        this.businessId = businessId;
        this.branchId = branchId;
        this.productId = productId;
        this.availability = availability;
        this.priceOverrideMinorUnits = priceOverrideMinorUnits;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(BranchProductAvailability availability, Long priceOverrideMinorUnits) {
        this.availability = availability;
        this.priceOverrideMinorUnits = priceOverrideMinorUnits;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getProductId() {
        return productId;
    }

    public BranchProductAvailability getAvailability() {
        return availability;
    }

    public Long getPriceOverrideMinorUnits() {
        return priceOverrideMinorUnits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
