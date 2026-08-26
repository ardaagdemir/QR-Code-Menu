package com.qrmenu.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrmenu.audit.repository.AuditLogEntryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public facade for the audit module (Section 3). A leaf module - depends on nothing
 * else, so any domain module can call in here without risking a dependency cycle
 * (menu -> audit, tenant -> audit, refund -> audit are all fine; audit -> nothing).
 */
@Service
public class AuditService {

    private static final int DEFAULT_LIMIT = 200;

    private final AuditLogEntryRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogEntryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** actorStaffUserId may be null for the rare system-initiated action with no human actor. */
    @Transactional
    public void record(UUID businessId, UUID actorStaffUserId, String entityType, UUID entityId, String action, Object details) {
        String detailsJson = null;
        if (details != null) {
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize audit log details", e);
            }
        }
        repository.save(new AuditLogEntry(businessId, actorStaffUserId, entityType, entityId, action, detailsJson));
    }

    /** StaffAuthService.hardDeleteStaffUserAsPlatformAdmin: anonymizes this staff user's past
     * audit-actor references before the row itself is deleted, so history survives the delete
     * distinguishably from a genuine system-initiated entry (see AuditLogEntry's javadoc). */
    @Transactional
    public void anonymizeActor(UUID staffUserId) {
        repository.anonymizeActor(staffUserId);
    }

    @Transactional(readOnly = true)
    public List<AuditEntryView> getRecentForBranch(UUID businessId, UUID branchId) {
        return repository.findAllByBusinessIdAndBranchIdOrderByCreatedAtDesc(
                        businessId, branchId, PageRequest.of(0, DEFAULT_LIMIT)).stream()
                .map(entry -> new AuditEntryView(
                        entry.getId(),
                        entry.getActorStaffUserId(),
                        entry.isActorAccountDeleted(),
                        entry.getEntityType(),
                        entry.getEntityId(),
                        entry.getAction(),
                        entry.getDetails(),
                        entry.getCreatedAt()))
                .toList();
    }
}
