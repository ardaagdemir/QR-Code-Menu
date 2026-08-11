package com.qrmenu.tenant.repository;

import com.qrmenu.tenant.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByIdAndBusinessId(UUID id, UUID businessId);

    List<Branch> findAllByBusinessIdOrderByNameAsc(UUID businessId);
}
