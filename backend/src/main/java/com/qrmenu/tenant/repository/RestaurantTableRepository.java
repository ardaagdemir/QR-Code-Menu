package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.RestaurantTable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    Optional<RestaurantTable> findByIdAndBusinessId(UUID id, UUID businessId);

    List<RestaurantTable> findAllByBranchIdOrderByLabelAsc(UUID branchId);
}
