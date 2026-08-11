package com.qrmenu.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_option")
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "option_group_id", nullable = false)
    private UUID optionGroupId;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_delta_minor_units", nullable = false)
    private long priceDeltaMinorUnits;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductOption() {
        // JPA
    }

    public ProductOption(UUID businessId, UUID optionGroupId, String name, long priceDeltaMinorUnits, int displayOrder) {
        this.businessId = businessId;
        this.optionGroupId = optionGroupId;
        this.name = name;
        this.priceDeltaMinorUnits = priceDeltaMinorUnits;
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

    public UUID getOptionGroupId() {
        return optionGroupId;
    }

    public String getName() {
        return name;
    }

    public long getPriceDeltaMinorUnits() {
        return priceDeltaMinorUnits;
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
