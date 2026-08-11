package com.qrmenu.menu.repository;

import com.qrmenu.menu.ProductOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOptionRepository extends JpaRepository<ProductOption, UUID> {

    Optional<ProductOption> findByIdAndBusinessId(UUID id, UUID businessId);

    List<ProductOption> findAllByOptionGroupIdInOrderByDisplayOrderAsc(List<UUID> optionGroupIds);
}
