package com.qrmenu.audit.repository;

import com.qrmenu.audit.AuditLogEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {

    List<AuditLogEntry> findAllByBusinessIdOrderByCreatedAtDesc(UUID businessId, Pageable pageable);
}
