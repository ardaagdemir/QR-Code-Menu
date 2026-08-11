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
 * Identifies a browser anonymously (HttpOnly/Secure/SameSite cookie carrying this row's
 * id). Deliberately has no business_id - the same session can accumulate TableVisits
 * across many businesses/branches/tables over time (Section 5).
 */
@Entity
@Table(name = "anonymous_customer_session")
public class AnonymousCustomerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public AnonymousCustomerSession() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public void touch() {
        this.lastSeenAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
