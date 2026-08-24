package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.Business;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    List<Business> findAllByOrderByNameAsc();
}
