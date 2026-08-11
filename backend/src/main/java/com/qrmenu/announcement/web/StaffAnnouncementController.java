package com.qrmenu.announcement.web;

import com.qrmenu.announcement.AnnouncementService;
import com.qrmenu.announcement.StaffAnnouncement;
import com.qrmenu.announcement.web.dto.AnnouncementResponse;
import com.qrmenu.announcement.web.dto.CreateAnnouncementRequest;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import jakarta.validation.Valid;
import java.util.List;
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
        return announcementService.listForBusiness(context.businessId()).stream().map(StaffAnnouncementController::toResponse).toList();
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
        StaffAnnouncement announcement = announcementService.create(
                context.businessId(),
                request.title(),
                request.message(),
                request.target(),
                request.branchIds(),
                request.expiresAt(),
                context.staffUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(announcement));
    }

    @PostMapping("/{announcementId}/end")
    public AnnouncementResponse end(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @PathVariable UUID announcementId) {
        StaffContext context = requireManage(sessionCookie);
        return toResponse(announcementService.endNow(context.businessId(), announcementId, context.staffUserId()));
    }

    private StaffContext requireManage(String sessionCookie) {
        return staffAuthService.resolveStaffContext(
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
