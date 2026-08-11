package com.qrmenu.menu.repository;

import com.qrmenu.menu.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndBusinessId(UUID id, UUID businessId);

    List<Product> findAllByCategoryIdInOrderByDisplayOrderAsc(List<UUID> categoryIds);
}
