package com.qrmenu.media.web;

import com.qrmenu.media.web.dto.MediaUploadResponse;
import com.qrmenu.shared.media.MediaCategory;
import com.qrmenu.shared.media.MediaStoragePort;
import com.qrmenu.shared.media.StoredMedia;
import com.qrmenu.staffaccess.Permission;
import com.qrmenu.staffaccess.StaffAuthService;
import com.qrmenu.staffaccess.StaffCookieSupport;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gap-analysis #15 (Section 3.2/16.1): generic upload surface for MediaStoragePort - not
 * owned by the menu or expense module (mirrors chain/reporting's "thin, no persistence"
 * shape) since both product images and expense receipts go through the same port. The
 * caller uploads first, gets a URL back, then submits that URL through the existing
 * Product/Expense create-or-update endpoints unchanged - no schema change on either
 * entity.
 */
@RestController
@RequestMapping("/api/staff/media")
public class StaffMediaUploadController {

    private final MediaStoragePort mediaStoragePort;
    private final StaffAuthService staffAuthService;

    public StaffMediaUploadController(MediaStoragePort mediaStoragePort, StaffAuthService staffAuthService) {
        this.mediaStoragePort = mediaStoragePort;
        this.staffAuthService = staffAuthService;
    }

    @PostMapping("/product-images")
    public MediaUploadResponse uploadProductImage(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @RequestParam("file") MultipartFile file) {
        staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie), Permission.MENU_MANAGE);
        StoredMedia stored = mediaStoragePort.store(MediaCategory.PRODUCT_IMAGE, readBytes(file));
        return new MediaUploadResponse(stored.url());
    }

    @PostMapping("/receipts")
    public MediaUploadResponse uploadReceipt(
            @CookieValue(name = StaffCookieSupport.COOKIE_NAME, required = false) String sessionCookie,
            @RequestParam("file") MultipartFile file) {
        staffAuthService.resolveStaffContext(StaffCookieSupport.parseSessionId(sessionCookie), Permission.EXPENSE_MANAGE);
        StoredMedia stored = mediaStoragePort.store(MediaCategory.EXPENSE_RECEIPT, readBytes(file));
        return new MediaUploadResponse(stored.url());
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }
}
