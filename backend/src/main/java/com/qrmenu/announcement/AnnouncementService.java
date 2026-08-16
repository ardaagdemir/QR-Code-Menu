package com.qrmenu.announcement;

import com.qrmenu.announcement.repository.StaffAnnouncementRepository;
import com.qrmenu.audit.AuditService;
import com.qrmenu.common.web.ResourceNotFoundException;
import com.qrmenu.staffaccess.StaffContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public facade for the announcement module (gap-analysis #7, Section 18.1). */
@Service
public class AnnouncementService {

    private final StaffAnnouncementRepository repository;
    private final AuditService auditService;

    public AnnouncementService(StaffAnnouncementRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public StaffAnnouncement create(
            UUID businessId,
            String title,
            String message,
            AnnouncementTarget target,
            Set<UUID> branchIds,
            Instant expiresAt,
            UUID actorStaffUserId) {
        Set<UUID> resolvedBranchIds = branchIds == null ? Set.of() : branchIds;
        if (target == AnnouncementTarget.SELECTED_BRANCHES && resolvedBranchIds.isEmpty()) {
            throw new IllegalArgumentException("SELECTED_BRANCHES target requires at least one branchId");
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        StaffAnnouncement announcement = repository.save(
                new StaffAnnouncement(businessId, title, message, target, resolvedBranchIds, actorStaffUserId, expiresAt));
        auditService.record(
                businessId, actorStaffUserId, "StaffAnnouncement", announcement.getId(), "CREATED", Map.of("title", title));
        return announcement;
    }

    @Transactional(readOnly = true)
    public List<StaffAnnouncement> listForBusiness(UUID businessId) {
        return repository.findAllByBusinessIdOrderByCreatedAtDesc(businessId);
    }

    @Transactional(readOnly = true)
    public List<StaffAnnouncement> listForBranch(UUID businessId, UUID branchId) {
        return listForBusiness(businessId).stream()
                .filter(announcement -> announcement.getTarget() == AnnouncementTarget.SELECTED_BRANCHES)
                .filter(announcement -> announcement.getBranchIds().equals(Set.of(branchId)))
                .toList();
    }

    /** Section 18.1: filters to not-expired announcements visible to the viewer's branch access. */
    @Transactional(readOnly = true)
    public List<StaffAnnouncement> listActiveFor(StaffContext context) {
        Instant now = Instant.now();
        return repository.findAllByBusinessIdOrderByCreatedAtDesc(context.businessId()).stream()
                .filter(announcement -> announcement.isActiveAt(now))
                .filter(announcement -> announcement.getTarget() == AnnouncementTarget.ALL_BRANCHES
                        || announcement.getBranchIds().stream().anyMatch(context::canAccessBranch))
                .toList();
    }

    @Transactional
    public StaffAnnouncement endNow(UUID businessId, UUID announcementId, UUID actorStaffUserId) {
        StaffAnnouncement announcement = repository
                .findById(announcementId)
                .filter(existing -> existing.getBusinessId().equals(businessId))
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found for business: " + announcementId));
        announcement.endNow();
        StaffAnnouncement saved = repository.save(announcement);
        auditService.record(businessId, actorStaffUserId, "StaffAnnouncement", saved.getId(), "ENDED", Map.of());
        return saved;
    }

    @Transactional
    public StaffAnnouncement endNowForBranch(
            UUID businessId, UUID branchId, UUID announcementId, UUID actorStaffUserId) {
        StaffAnnouncement announcement = repository
                .findById(announcementId)
                .filter(existing -> existing.getBusinessId().equals(businessId))
                .filter(existing -> existing.getTarget() == AnnouncementTarget.SELECTED_BRANCHES)
                .filter(existing -> existing.getBranchIds().equals(Set.of(branchId)))
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found for active branch: " + announcementId));
        announcement.endNow();
        StaffAnnouncement saved = repository.save(announcement);
        auditService.record(businessId, actorStaffUserId, "StaffAnnouncement", saved.getId(), "ENDED", Map.of());
        return saved;
    }
}
