package com.qrmenu.announcement;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Section 18.1 "Şube duyuruları" (gap-analysis #7): a manual, staff-authored announcement
 * shown to every staff-web viewer scoped to the caller's business. Fully separate feature
 * from bulk menu assignment - not auto-created by it. `branchIds` is only populated for
 * SELECTED_BRANCHES; for ALL_BRANCHES it stays empty and visibility is business-wide.
 */
@Entity
@Table(name = "staff_announcement")
public class StaffAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementTarget target;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_announcement_branch", joinColumns = @JoinColumn(name = "staff_announcement_id"))
    @Column(name = "branch_id", nullable = false)
    private Set<UUID> branchIds = new LinkedHashSet<>();

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected StaffAnnouncement() {
        // JPA
    }

    public StaffAnnouncement(
            UUID businessId,
            String title,
            String message,
            AnnouncementTarget target,
            Set<UUID> branchIds,
            UUID createdBy,
            Instant expiresAt) {
        this.businessId = businessId;
        this.title = title;
        this.message = message;
        this.target = target;
        this.branchIds = branchIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(branchIds);
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    /** Early retraction (Section 18.1): pull the announcement without deleting its history. */
    public void endNow() {
        this.expiresAt = Instant.now();
    }

    public boolean isActiveAt(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public boolean visibleToBranch(UUID branchId) {
        return target == AnnouncementTarget.ALL_BRANCHES || branchIds.contains(branchId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public AnnouncementTarget getTarget() {
        return target;
    }

    public Set<UUID> getBranchIds() {
        return branchIds;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
