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
 * (Section 5, "Ürün seçenekleri (modifier)"): SINGLE/MULTIPLE selection type only - no
 * generic/abstract "attribute engine" (Section 12). selectionType bounds how many options
 * may be picked (SINGLE: at most one), required independently says whether picking at
 * least one is mandatory.
 */
@Entity
@Table(name = "product_option_group")
public class ProductOptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 20)
    private SelectionType selectionType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductOptionGroup() {
        // JPA
    }

    public ProductOptionGroup(
            UUID businessId, UUID productId, String name, SelectionType selectionType, boolean required, int displayOrder) {
        this.businessId = businessId;
        this.productId = productId;
        this.name = name;
        this.selectionType = selectionType;
        this.required = required;
        this.displayOrder = displayOrder;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, SelectionType selectionType, boolean required) {
        this.name = name;
        this.selectionType = selectionType;
        this.required = required;
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

    public UUID getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public SelectionType getSelectionType() {
        return selectionType;
    }

    public boolean isRequired() {
        return required;
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
