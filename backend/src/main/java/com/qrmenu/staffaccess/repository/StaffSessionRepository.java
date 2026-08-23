package com.qrmenu.staffaccess.repository;

import com.qrmenu.staffaccess.StaffSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffSessionRepository extends JpaRepository<StaffSession, UUID> {

    /** Admin-triggered reset: no session of the target user is "current" to preserve. */
    void deleteAllByStaffUserId(UUID staffUserId);

    /** Self-service change: keeps the session the request itself authenticated with. */
    void deleteAllByStaffUserIdAndIdNot(UUID staffUserId, UUID id);
}
