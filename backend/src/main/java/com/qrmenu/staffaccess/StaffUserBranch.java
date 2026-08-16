package com.qrmenu.staffaccess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Gap-analysis #16: the persisted active branch assignment for every user-facing
 * staff role. Kept as a plain join entity for migration compatibility; the database
 * permits at most one row per user-facing staff user; PLATFORM_ADMIN is outside
 * that invariant.
 */
@Entity
@Table(name = "staff_user_branch")
public class StaffUserBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    protected StaffUserBranch() {
        // JPA
    }

    public StaffUserBranch(UUID staffUserId, UUID branchId) {
        this.staffUserId = staffUserId;
        this.branchId = branchId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStaffUserId() {
        return staffUserId;
    }

    public UUID getBranchId() {
        return branchId;
    }
}
