package com.qrmenu.menu.repository;

import com.qrmenu.menu.ProductOptionGroup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionGroupRepository extends JpaRepository<ProductOptionGroup, UUID> {

    Optional<ProductOptionGroup> findByIdAndBusinessId(UUID id, UUID businessId);

    List<ProductOptionGroup> findAllByProductIdInOrderByDisplayOrderAsc(List<UUID> productIds);
}
