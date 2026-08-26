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
 * Business-level catalog root (Section 2, "Menü/ürün modeli"): a category belongs to a
 * Business, not a Branch - branch-level differences are expressed entirely through
 * BranchProduct.
 */
@Entity
@Table(name = "menu_category")
public class MenuCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MenuCategory() {
        // JPA
    }

    public MenuCategory(UUID businessId, String name, int displayOrder) {
        this.businessId = businessId;
        this.name = name;
        this.displayOrder = displayOrder;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String name) {
        this.name = name;
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

    public String getName() {
        return name;
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
