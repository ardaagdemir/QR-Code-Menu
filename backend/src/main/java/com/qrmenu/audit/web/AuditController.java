package com.qrmenu.audit.web;

import com.qrmenu.audit.AuditEntryView;
import com.qrmenu.audit.AuditService;
import com.qrmenu.audit.web.dto.AuditEntryResponse;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffContext;
import com.qrmenu.staffaccess.StaffCookieSupport;
import java.util.List;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Section 4, staff-web screen #7: "Sipariş/Ödeme/İade geçmişi ve audit görünümü" - BUSINESS_ADMIN only. */
@RestController
@RequestMapping("/api/staff/audit")
public class AuditController {

    private final StaffAuthService staffAuthService;
    private final AuditService auditService;

    public AuditController(StaffAuthService staffAuthService, AuditService auditService) {
        this.staffAuthService = staffAuthService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEntryResponse> list(@CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie) {
        StaffContext context =
                staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie), Permission.AUDIT_VIEW);
        return auditService.getRecentForBusiness(context.businessId()).stream().map(AuditController::toResponse).toList();
    }

    private static AuditEntryResponse toResponse(AuditEntryView view) {
        return new AuditEntryResponse(
                view.id(), view.actorStaffUserId(), view.entityType(), view.entityId(), view.action(), view.details(), view.createdAt());
    }
}
