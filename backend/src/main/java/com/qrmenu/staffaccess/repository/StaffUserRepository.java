package com.qrmenu.staffaccess.repository;

import com.qrmenu.staffaccess.StaffUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    Optional<StaffUser> findByEmail(String email);

    List<StaffUser> findAllByBusinessIdOrderByCreatedAtAsc(UUID businessId);

    Optional<StaffUser> findByIdAndBusinessId(UUID id, UUID businessId);
}
