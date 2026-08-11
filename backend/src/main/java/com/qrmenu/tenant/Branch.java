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
import java.time.ZoneId;
import java.util.UUID;

/**
 * businessId is a plain foreign-key column, not a JPA @ManyToOne relationship - kept
 * explicit/id-based on purpose, in the same spirit as the "no magic
 * TenantScopedRepository" decision in Section 2: every business_id used in a query is
 * visible in the code, not hidden behind lazy-loaded associations.
 *
 * <p>Gap-analysis #4: the single openingTime/closingTime pair this used to carry
 * couldn't represent different hours per day of week, so it was replaced by
 * BranchBusinessHours (one row per java.time.DayOfWeek, product-requirements.md
 * Section 12.2). orderingEnabled is what already fulfills that section's "temporary
 * closed/open override" - a manual toggle that overrides the weekly schedule
 * regardless of what day/hour it is; a second field for the same concept would be
 * redundant (Section 26: "gereksiz karmaşıklık").
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

    /** Opsiyonel (Section 12.2). */
    @Column
    private String address;

    /** Opsiyonel (Section 12.2) - null olduğunda tüketen tarafın Business.defaultTimeZone'a düşmesi beklenir. */
    @Column
    private String timezone;

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

    public Branch(UUID businessId, String name, boolean orderingEnabled, String address, DeliveryModel deliveryModel) {
        this.businessId = businessId;
        this.name = name;
        this.orderingEnabled = orderingEnabled;
        this.address = address;
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

    public void setAddress(String address) {
        this.address = address;
        this.updatedAt = Instant.now();
    }

    public void setTimezone(String timezone) {
        if (timezone != null) {
            try {
                ZoneId.of(timezone);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid IANA time zone id: " + timezone);
            }
        }
        this.timezone = timezone;
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

    public String getAddress() {
        return address;
    }

    public String getTimezone() {
        return timezone;
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
