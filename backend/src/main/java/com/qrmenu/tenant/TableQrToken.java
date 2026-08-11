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
 * Table <-> TableQrToken is one-to-many; revoked tokens are kept (not deleted) for
 * audit purposes (Section 5). The DB partial unique index
 * (uq_table_qr_token_one_active_per_table) is the real guarantee that at most one
 * ACTIVE token exists per table - the service layer additionally revokes-then-inserts
 * so that guarantee is never even challenged in the normal path.
 *
 * The raw token value is stored as plaintext (not hashed): unlike orderTrackingToken
 * (Section 2), a leaked QR token can only ever start a new TableVisit - it grants no
 * access to an existing cart/order (Section 5, "QR güvenlik modeli" risk correction) -
 * so the extra hashing layer the doc reserves for orderTrackingToken isn't warranted
 * here, and it needs to remain readable so it can be re-printed on demand.
 */
@Entity
@Table(name = "table_qr_token")
public class TableQrToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QrTokenStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected TableQrToken() {
        // JPA
    }

    public TableQrToken(UUID businessId, UUID tableId, String token) {
        this.businessId = businessId;
        this.tableId = tableId;
        this.token = token;
        this.status = QrTokenStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void revoke() {
        if (this.status == QrTokenStatus.ACTIVE) {
            this.status = QrTokenStatus.REVOKED;
            this.revokedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public UUID getTableId() {
        return tableId;
    }

    public String getToken() {
        return token;
    }

    public QrTokenStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
