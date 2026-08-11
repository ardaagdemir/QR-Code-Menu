package com.qrmenu.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Business-level product. basePriceMinorUnits is an integer minor-unit amount (kuruş) -
 * no floating point (Section 5, "Para birimi", confirmed). No Money value object yet:
 * that's introduced in Milestone 4 alongside Order, not before it's actually needed
 * (Section 9/12, YAGNI).
 *
 * Deliberately no business-level "active"/discontinued flag - the doc only defines
 * availability at the Branch level via BranchProduct; inventing a second on/off switch
 * here isn't asked for.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /** Optional, plain URL - no media-storage/CDN infrastructure (Section 5, 14). */
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "base_price_minor_units", nullable = false)
    private long basePriceMinorUnits;

    @Column(name = "tax_rate_percent", nullable = false)
    private int taxRatePercent;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // JPA
    }

    public Product(
            UUID businessId,
            UUID categoryId,
            String name,
            String description,
            String imageUrl,
            long basePriceMinorUnits,
            int taxRatePercent,
            int displayOrder) {
        this.businessId = businessId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.basePriceMinorUnits = basePriceMinorUnits;
        this.taxRatePercent = taxRatePercent;
        this.displayOrder = displayOrder;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public long getBasePriceMinorUnits() {
        return basePriceMinorUnits;
    }

    public int getTaxRatePercent() {
        return taxRatePercent;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
