package com.qrmenu.menu.repository;

import com.qrmenu.menu.MenuCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {

    Optional<MenuCategory> findByIdAndBusinessId(UUID id, UUID businessId);

    List<MenuCategory> findAllByBusinessIdOrderByDisplayOrderAsc(UUID businessId);
}
