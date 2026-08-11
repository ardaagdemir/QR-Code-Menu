package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.Business;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
}
