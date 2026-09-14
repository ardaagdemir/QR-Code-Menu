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
import java.util.UUID;

/**
 * Named RestaurantTable (not Table) to avoid colliding with jakarta.persistence.Table.
 * businessId is denormalized from branchId so tenant-scoped queries can filter on it
 * directly without a join (Section 2).
 */
@Entity
@Table(name = "restaurant_table")
public class RestaurantTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TableLocation location;

    @Column
    private Integer capacity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Archive/deactivate (never a hard delete) for a table with TableVisit history -
     * same pattern as Business/Branch.active. An inactive table rejects new QR
     * check-in (TenantService.checkIn) but existing TableVisit/order
     * history is untouched. See TenantService.archiveLockedTable/reactivateTable.
     */
    @Column(nullable = false)
    private boolean active;

    protected RestaurantTable() {
        // JPA
    }

    /** Defaults to INDOOR/no capacity - kept for the internal API and existing tests that never set location. */
    public RestaurantTable(UUID businessId, UUID branchId, String label) {
        this(businessId, branchId, label, TableLocation.INDOOR, null);
    }

    public RestaurantTable(UUID businessId, UUID branchId, String label, TableLocation location, Integer capacity) {
        this.businessId = businessId;
        this.branchId = branchId;
        this.label = label;
        this.location = location;
        this.capacity = capacity;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Masa Düzenle: isim, konum ve kapasite bir arada güncellenir - "yanlış girilirse düzeltilebilsin" akışı. */
    public void update(String label, TableLocation location, Integer capacity) {
        this.label = label;
        this.location = location;
        this.capacity = capacity;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
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

    public String getLabel() {
        return label;
    }

    public TableLocation getLocation() {
        return location;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
