package com.qrmenu.menu;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Business-level product. basePriceMinorUnits is an integer minor-unit amount (kuruş) -
 * no floating point (Section 5, "Para birimi", confirmed). No Money value object yet:
 * that's introduced in Milestone 4 alongside Order, not before it's actually needed
 * (Section 9/12, YAGNI).
 *
 * `active` (Section 3.2, gap-analysis "Product alanları") is a business-level kill
 * switch distinct from the Branch-level BranchProduct.availability: an inactive product
 * is hidden from every branch's public menu and un-orderable regardless of any
 * BranchProduct opt-in row, whereas availability toggles per-branch stock. `allergens`
 * is an ElementCollection (not its own repository/entity) - it's a value collection
 * always read through the owning Product, same module, no cross-module access to guard.
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

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "estimated_preparation_minutes")
    private Integer estimatedPreparationMinutes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_allergen", joinColumns = @JoinColumn(name = "product_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "allergen", nullable = false)
    private Set<Allergen> allergens = new LinkedHashSet<>();

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
            int displayOrder,
            boolean active,
            Integer estimatedPreparationMinutes,
            Set<Allergen> allergens) {
        this.businessId = businessId;
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.basePriceMinorUnits = basePriceMinorUnits;
        this.displayOrder = displayOrder;
        this.active = active;
        this.estimatedPreparationMinutes = estimatedPreparationMinutes;
        this.allergens = allergens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allergens);
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Gap-analysis "Product alanları" - staff-web edit form for the fields not fixed at
     * creation. Gap-analysis #15 added imageUrl here too: it was only settable at
     * creation before, so an already-created product had no UI path to attach an image.
     * Later widened to also cover name/description/price so create and edit support the
     * same field set - previously those "temel ticari alanlar" were only ever settable
     * once, at creation.
     */
    public void updateDetails(
            String name,
            String description,
            long basePriceMinorUnits,
            boolean active,
            Integer estimatedPreparationMinutes,
            Set<Allergen> allergens,
            String imageUrl) {
        this.name = name;
        this.description = description;
        this.basePriceMinorUnits = basePriceMinorUnits;
        this.active = active;
        this.estimatedPreparationMinutes = estimatedPreparationMinutes;
        this.allergens = allergens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allergens);
        this.imageUrl = imageUrl;
        this.updatedAt = Instant.now();
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
        this.updatedAt = Instant.now();
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

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public Integer getEstimatedPreparationMinutes() {
        return estimatedPreparationMinutes;
    }

    public Set<Allergen> getAllergens() {
        return Set.copyOf(allergens);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
