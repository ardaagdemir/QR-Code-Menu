package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.RestaurantTable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    Optional<RestaurantTable> findByIdAndBusinessId(UUID id, UUID businessId);

    List<RestaurantTable> findAllByBranchIdOrderByLabelAsc(UUID branchId);

    /**
     * Serializes the archive flow (check-then-flip) against a concurrent order/visit
     * becoming active for this table - see BranchRepository.findByIdAndBusinessIdForUpdate
     * for the identical reasoning, and OrderingService.archiveTable, the only caller.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id and t.businessId = :businessId")
    Optional<RestaurantTable> findByIdAndBusinessIdForUpdate(@Param("id") UUID id, @Param("businessId") UUID businessId);

    /**
     * Serializes QR check-in (new-visit creation) against a concurrent archive/hard-delete
     * of this table - see TenantService.checkIn, the only caller. PESSIMISTIC_READ (not
     * _WRITE): concurrent check-ins to the same table must not block each other, only
     * archive/delete's PESSIMISTIC_WRITE needs to be excluded.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> findByIdForShare(@Param("id") UUID id);
}
