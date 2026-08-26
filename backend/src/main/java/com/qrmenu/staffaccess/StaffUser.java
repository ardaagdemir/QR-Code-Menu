package com.qrmenu.staffaccess;

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
 * Section 5: a StaffUser belongs to exactly one Business (null businessId is the
 * PLATFORM_ADMIN exception). passwordHash is a BCrypt digest (StaffAuthService) -
 * the plaintext password is never persisted or logged.
 */
@Entity
@Table(name = "staff_user")
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffUser() {
        // JPA
    }

    public StaffUser(UUID businessId, String email, String passwordHash, StaffRole role) {
        this.businessId = businessId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    /** Platform admin reactivation - never reactivates via password reset (see updatePasswordHash), only this. */
    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void changeRole(StaffRole newRole) {
        this.role = newRole;
        this.updatedAt = Instant.now();
    }

    public void updateEmail(String newEmail) {
        this.email = newEmail;
        this.updatedAt = Instant.now();
    }

    /** Never reactivates a disabled user - self-service change and admin reset both go through
     * this same setter, and neither is a login re-authorization mechanism. */
    public void updatePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public StaffRole getRole() {
        return role;
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
