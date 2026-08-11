package com.qrmenu.staffaccess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mirrors AnonymousCustomerSession's shape (Milestone 2) - a DB-backed session row, the
 * qrmenu_staff_session cookie carries only this row's id, never staffUserId directly.
 * Deliberately not a stateless JWT: logout must actually revoke access (delete the
 * row), which a self-contained token can't do without a separate blocklist.
 */
@Entity
@Table(name = "staff_session")
public class StaffSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    protected StaffSession() {
        // JPA
    }

    public StaffSession(UUID staffUserId) {
        this.staffUserId = staffUserId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastActivityAt = now;
    }

    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getStaffUserId() {
        return staffUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }
}
