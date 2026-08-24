package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.Branch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByIdAndBusinessId(UUID id, UUID businessId);

    List<Branch> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    /**
     * Serializes the platform-admin deactivate flow (check-then-flip) against any
     * concurrent order/payment that would newly become "active" for this branch - see
     * TenantService.assertOrderingCurrentlyAllowed, which takes this same lock before its
     * business/branch-active check, so the two paths always agree on a single, up-to-date
     * view of the branch row rather than racing each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id and b.businessId = :businessId")
    Optional<Branch> findByIdAndBusinessIdForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);
}
