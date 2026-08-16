package com.qrmenu.announcement.web;

import com.qrmenu.announcement.AnnouncementService;
import com.qrmenu.announcement.AnnouncementTarget;
import com.qrmenu.announcement.StaffAnnouncement;
import com.qrmenu.common.web.StaffPermissionDeniedException;
import com.qrmenu.announcement.web.dto.AnnouncementResponse;
import com.qrmenu.announcement.web.dto.CreateAnnouncementRequest;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Section 18.1 "Şube duyuruları" (gap-analysis #7). Manage endpoints (list/create/end)
 * require Permission.ANNOUNCEMENT_MANAGE (BUSINESS_ADMIN); /active is open to any
 * authenticated staff session so the banner works for every role.
 */
@RestController
@RequestMapping("/api/staff/announcements")
public class StaffAnnouncementController {

    private final AnnouncementService announcementService;
    private final StaffAuthService staffAuthService;

    public StaffAnnouncementController(AnnouncementService announcementService, StaffAuthService staffAuthService) {
        this.announcementService = announcementService;
        this.staffAuthService = staffAuthService;
    }

    @GetMapping
    public List<AnnouncementResponse> list(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = requireManage(sessionCookie);
        return announcementService.listForBranch(context.businessId(), context.activeBranchId()).stream()
                .map(StaffAnnouncementController::toResponse)
                .toList();
    }

    @GetMapping("/active")
    public List<AnnouncementResponse> listActive(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context = staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie));
        return announcementService.listActiveFor(context).stream().map(StaffAnnouncementController::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<AnnouncementResponse> create(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody CreateAnnouncementRequest request) {
        StaffContext context = requireManage(sessionCookie);
        Set<UUID> requestedBranchIds = request.branchIds() == null ? Set.of() : request.branchIds();
        if (request.target() != null
                && (request.target() != AnnouncementTarget.SELECTED_BRANCHES
                        || !requestedBranchIds.equals(Set.of(context.activeBranchId())))) {
            throw new StaffPermissionDeniedException("Cross-branch announcement management is not available");
        }
        StaffAnnouncement announcement = announcementService.create(
                context.businessId(),
                request.title(),
                request.message(),
                AnnouncementTarget.SELECTED_BRANCHES,
                Set.of(context.activeBranchId()),
                request.expiresAt(),
                context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(announcement));
    }

    @PostMapping("/{announcementId}/end")
    public AnnouncementResponse end(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID announcementId) {
        StaffContext context = requireManage(sessionCookie);
        return toResponse(announcementService.endNowForBranch(
                context.businessId(), context.activeBranchId(), announcementId, context.staffUserId()));
    }

    private StaffContext requireManage(String sessionCookie) {
        return staffAuthService.resolveStaffContextForActiveBranch(
                StaffCookieSupport.parseSessionId(sessionCookie), Permission.ANNOUNCEMENT_MANAGE);
    }

    private static AnnouncementResponse toResponse(StaffAnnouncement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getBusinessId(),
                announcement.getTitle(),
                announcement.getMessage(),
                announcement.getTarget().name(),
                announcement.getBranchIds(),
                announcement.getCreatedBy(),
                announcement.getCreatedAt(),
                announcement.getExpiresAt());
    }
}
