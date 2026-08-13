package com.qrmenu.shared.media;

import java.util.Set;

/**
 * Section 3.2/16.1: product images and expense receipts are the two upload surfaces
 * today. Each category owns its own directory (served at /media/{directoryName}/...),
 * size limit and allowed file types - receipts allow PDF (Section 16.1: "fotoğrafı/
 * dokümanı") in addition to images, product images do not.
 */
public enum MediaCategory {
    PRODUCT_IMAGE("product-images", 5L * 1024 * 1024, Set.of(DetectedFileType.JPEG, DetectedFileType.PNG, DetectedFileType.WEBP)),
    EXPENSE_RECEIPT(
            "receipts",
            10L * 1024 * 1024,
            Set.of(DetectedFileType.JPEG, DetectedFileType.PNG, DetectedFileType.WEBP, DetectedFileType.PDF));

    private final String directoryName;
    private final long maxSizeBytes;
    private final Set<DetectedFileType> allowedTypes;

    MediaCategory(String directoryName, long maxSizeBytes, Set<DetectedFileType> allowedTypes) {
        this.directoryName = directoryName;
        this.maxSizeBytes = maxSizeBytes;
        this.allowedTypes = allowedTypes;
    }

    public String directoryName() {
        return directoryName;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }

    public boolean allows(DetectedFileType type) {
        return allowedTypes.contains(type);
    }
}
