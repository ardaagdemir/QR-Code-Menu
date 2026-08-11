package com.qrmenu.announcement.repository;

import com.qrmenu.announcement.StaffAnnouncement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAnnouncementRepository extends JpaRepository<StaffAnnouncement, UUID> {

    List<StaffAnnouncement> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId);
}
