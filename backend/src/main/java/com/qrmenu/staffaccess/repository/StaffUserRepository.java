package com.qrmenu.staffaccess.repository;

import com.qrmenu.staffaccess.StaffUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    Optional<StaffUser> findByEmail(String email);

    List<StaffUser> findAllByBusinessIdOrderByCreatedAtAsc(UUID businessId);

    Optional<StaffUser> findByIdAndBusinessId(UUID id, UUID businessId);

    /**
     * Serializes hardDeleteStaffUserAsPlatformAdmin (session cleanup, then the actual
     * DELETE) against any concurrent login() for the same user - see
     * StaffAuthService.hardDeleteStaffUserAsPlatformAdmin's Javadoc. login()'s
     * staffSessionRepository.save(...) inserts a row with a FK to this staff_user, so
     * Postgres needs a FOR KEY SHARE lock on this row to complete that insert; holding a
     * real FOR UPDATE lock here for the rest of the hard-delete transaction makes that
     * insert block until the hard-delete either commits (in which case the insert then
     * fails - the row is gone) or rolls back (in which case the insert proceeds normally)
     * - never sandwiched in between with a live session left pointing at a since-deleted
     * user.
     *
     * <p>Deliberately a native query with a literal "FOR UPDATE" rather than
     * {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} (contrast BranchRepository's own
     * findByIdAndBusinessIdForUpdate) - Hibernate's Postgres dialect maps
     * PESSIMISTIC_WRITE to "FOR NO KEY UPDATE", which does NOT conflict with FOR KEY
     * SHARE (that's the whole reason FOR NO KEY UPDATE exists: so a plain column update
     * doesn't block FK-referencing inserts elsewhere) - it would fail to block the exact
     * login() race this method exists for. Confirmed by hand: a PESSIMISTIC_WRITE-locked
     * version of this method let a concurrent login() insert a session immediately,
     * un-blocked, in PlatformAdminStaffHardDeleteIntegrationTest's
     * hardDeleteRowLockBlocksAConcurrentLoginUntilTheLockIsReleasedAndTheLoginThenFailsOnceTheUserIsGone.
     */
    @Query(value = "select * from staff_user where id = :id and business_id = :businessId for update", nativeQuery = true)
    Optional<StaffUser> findByIdAndBusinessIdForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);
}
