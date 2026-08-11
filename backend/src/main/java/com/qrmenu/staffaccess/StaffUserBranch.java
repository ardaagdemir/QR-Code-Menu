package com.qrmenu.staffaccess;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Section 5: "StaffUser *-* Branch (yalnızca BRANCH_MANAGER/KITCHEN_STAFF için
 * scoping)". A plain join entity (not a JPA @ManyToMany collection), same "every id
 * visible in code" convention as TableQrToken/BranchProduct. BUSINESS_ADMIN has no
 * rows here - their scope is every branch in their business, checked via businessId.
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
