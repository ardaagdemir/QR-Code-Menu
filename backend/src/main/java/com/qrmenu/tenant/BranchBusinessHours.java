package com.qrmenu.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Gap-analysis #4 (product-requirements.md Section 12.2): one row per day of week for
 * a branch - replaces the old single Branch.openingTime/closingTime pair, which
 * couldn't represent e.g. "Saturdays until midnight, everything else until 22:00". A
 * branch with no row at all for a given day is treated as unrestricted for that day
 * (only Branch.orderingEnabled applies) - the same default the old nullable
 * openingTime/closingTime pair had, preserved so a branch that never configures hours
 * keeps working exactly as before. java.time.DayOfWeek is reused directly rather than
 * a bespoke enum - it already is exactly "MONDAY..SUNDAY", no reason to reinvent it.
 */
@Entity
@Table(name = "branch_business_hours")
public class BranchBusinessHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(nullable = false)
    private boolean closed;

    protected BranchBusinessHours() {
        // JPA
    }

    public BranchBusinessHours(
            UUID businessId, UUID branchId, DayOfWeek dayOfWeek, LocalTime openingTime, LocalTime closingTime, boolean closed) {
        this.businessId = businessId;
        this.branchId = branchId;
        this.dayOfWeek = dayOfWeek;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.closed = closed;
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

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public boolean isClosed() {
        return closed;
    }
}
