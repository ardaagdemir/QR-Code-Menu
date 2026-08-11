package com.qrmenu.staffaccess.repository;

import com.qrmenu.staffaccess.StaffSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffSessionRepository extends JpaRepository<StaffSession, UUID> {
}
