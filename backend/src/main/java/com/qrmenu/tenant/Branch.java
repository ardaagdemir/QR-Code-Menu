package com.qrmenu.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * businessId is a plain foreign-key column, not a JPA @ManyToOne relationship - kept
 * explicit/id-based on purpose, in the same spirit as the "no magic
 * TenantScopedRepository" decision in Section 2: every business_id used in a query is
 * visible in the code, not hidden behind lazy-loaded associations.
 */
@Entity
@Table(name = "branch")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String name;

    @Column(name = "ordering_enabled", nullable = false)
    private boolean orderingEnabled;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "delivery_model", nullable = false)
    @Enumerated(EnumType.STRING)
    private DeliveryModel deliveryModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Branch() {
        // JPA
    }

    public Branch(
            UUID businessId,
            String name,
            boolean orderingEnabled,
            LocalTime openingTime,
            LocalTime closingTime,
            DeliveryModel deliveryModel) {
        this.businessId = businessId;
        this.name = name;
        this.orderingEnabled = orderingEnabled;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.deliveryModel = deliveryModel;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Section 9, Milestone 8: BUSINESS_ADMIN can toggle ordering on/off for their branch. */
    public void setOrderingEnabled(boolean orderingEnabled) {
        this.orderingEnabled = orderingEnabled;
        this.updatedAt = Instant.now();
    }

    /** Section 9, Milestone 9: BUSINESS_ADMIN can change a branch's delivery model after creation. */
    public void setDeliveryModel(DeliveryModel deliveryModel) {
        this.deliveryModel = deliveryModel;
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

    public boolean isOrderingEnabled() {
        return orderingEnabled;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public DeliveryModel getDeliveryModel() {
        return deliveryModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
