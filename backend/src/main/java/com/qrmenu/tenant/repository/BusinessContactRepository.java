package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.BusinessContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessContactRepository extends JpaRepository<BusinessContact, UUID> {

    List<BusinessContact> findAllByBusinessIdOrderByNameAsc(UUID businessId);

    Optional<BusinessContact> findByIdAndBusinessId(UUID id, UUID businessId);
}
