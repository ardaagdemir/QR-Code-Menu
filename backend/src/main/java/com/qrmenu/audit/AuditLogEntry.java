package com.qrmenu.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Section 3: "Kritik yönetimsel aksiyonların (menü/BranchProduct değişikliği, iade, QR
 * revoke, ordering-enabled toggle vb.) business_id ile birlikte audit log'a yazılması."
 * A plain, append-only record - no update/delete mutators, entries are immutable once
 * written. actorStaffUserId is nullable for the rare system-initiated entry with no
 * human actor.
 */
@Entity
@Table(name = "audit_log_entry")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "actor_staff_user_id")
    private UUID actorStaffUserId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLogEntry() {
        // JPA
    }

    public AuditLogEntry(UUID businessId, UUID actorStaffUserId, String entityType, UUID entityId, String action, String details) {
        this.businessId = businessId;
        this.actorStaffUserId = actorStaffUserId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getActorStaffUserId() {
        return actorStaffUserId;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
