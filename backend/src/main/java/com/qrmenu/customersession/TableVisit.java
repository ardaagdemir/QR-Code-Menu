package com.qrmenu.customersession;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single AnonymousCustomerSession's visit to one Business/Branch/Table. Created when
 * a QR is scanned; the same visit is continued (not duplicated) on a later scan of the
 * same table by the same session while still fresh (Section 5).
 */
@Entity
@Table(name = "table_visit")
public class TableVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "anonymous_customer_session_id", nullable = false)
    private UUID anonymousCustomerSessionId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected TableVisit() {
        // JPA
    }

    public TableVisit(UUID businessId, UUID branchId, UUID tableId, UUID anonymousCustomerSessionId) {
        this.businessId = businessId;
        this.branchId = branchId;
        this.tableId = tableId;
        this.anonymousCustomerSessionId = anonymousCustomerSessionId;
        Instant now = Instant.now();
        this.startedAt = now;
        this.lastActivityAt = now;
    }

    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    /** Gap-analysis #13: marks a TTL-stale visit closed so it can no longer be acted on. */
    public void close() {
        this.closedAt = Instant.now();
    }

    public boolean isClosed() {
        return closedAt != null;
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

    public UUID getTableId() {
        return tableId;
    }

    public UUID getAnonymousCustomerSessionId() {
        return anonymousCustomerSessionId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
