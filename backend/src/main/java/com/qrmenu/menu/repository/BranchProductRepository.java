package com.qrmenu.menu.repository;

import com.qrmenu.menu.BranchProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchProductRepository extends JpaRepository<BranchProduct, UUID> {

    Optional<BranchProduct> findByBranchIdAndProductId(UUID branchId, UUID productId);

    List<BranchProduct> findAllByBranchIdAndProductIdIn(UUID branchId, List<UUID> productIds);

    List<BranchProduct> findAllByBranchId(UUID branchId);
}
