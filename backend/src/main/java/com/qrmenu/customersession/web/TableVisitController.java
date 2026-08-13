package com.qrmenu.customersession.web;

import com.qrmenu.customersession.CustomerSessionService;
import com.qrmenu.customersession.SessionCookieSupport;
import com.qrmenu.customersession.TableVisit;
import com.qrmenu.customersession.web.dto.SetGuestCountRequest;
import com.qrmenu.customersession.web.dto.TableVisitGuestCountResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, anonymous, customer-facing TableVisit self-service (Gap-analysis #17, Section
 * 13.3). Ownership is enforced the same way as the cart (CustomerSessionService.
 * getOwnedTableVisit via the qrmenu_session cookie) - the tableVisitId in the URL is not
 * itself the access credential.
 */
@RestController
@RequestMapping("/api/table-visits/{tableVisitId}")
public class TableVisitController {

    private final CustomerSessionService customerSessionService;

    public TableVisitController(CustomerSessionService customerSessionService) {
        this.customerSessionService = customerSessionService;
    }

    /** Records, changes, or clears (guestCount: null) the real headcount for this visit. */
    @PatchMapping("/guest-count")
    public TableVisitGuestCountResponse setGuestCount(
            @PathVariable UUID tableVisitId,
            @CookieValue(name = SessionCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @Valid @RequestBody SetGuestCountRequest request) {
        UUID sessionId = SessionCookieSupport.parseSessionId(sessionCookie);
        TableVisit visit = customerSessionService.setGuestCount(tableVisitId, sessionId, request.guestCount());
        return new TableVisitGuestCountResponse(visit.getId(), visit.getGuestCount());
    }
}
