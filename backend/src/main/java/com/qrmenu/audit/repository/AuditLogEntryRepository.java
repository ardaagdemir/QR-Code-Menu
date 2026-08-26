package com.qrmenu.audit.repository;

import com.qrmenu.audit.AuditLogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {

    List<AuditLogEntry> findAllByBusinessIdAndBranchIdOrderByCreatedAtDesc(UUID businessId, UUID branchId, Pageable pageable);

    /** StaffAuthService.hardDeleteStaffUserAsPlatformAdmin: anonymizes this staff user's past
     * audit-actor references before the row itself is deleted, flagging them as
     * actorAccountDeleted so the audit view can tell them apart from genuine system actions. */
    @Modifying
    @Query("UPDATE AuditLogEntry a SET a.actorStaffUserId = NULL, a.actorAccountDeleted = true WHERE a.actorStaffUserId = :staffUserId")
    void anonymizeActor(@Param("staffUserId") UUID staffUserId);
}
