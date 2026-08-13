package com.qrmenu.shared.media;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Section 22 ("zararlı dosya kontrolleri"): the browser-supplied Content-Type header is
 * never trusted - a renamed executable can claim to be "image/jpeg". Instead the first
 * bytes of the actual upload are sniffed against each format's magic number, exactly
 * like file(1)/libmagic does, without adding a new dependency (Apache Tika would be
 * overkill for four fixed signatures).
 */
public enum DetectedFileType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf");

    private final String contentType;
    private final String extension;

    DetectedFileType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<DetectedFileType> sniff(byte[] content) {
        if (isJpeg(content)) {
            return Optional.of(JPEG);
        }
        if (isPng(content)) {
            return Optional.of(PNG);
        }
        if (isWebp(content)) {
            return Optional.of(WEBP);
        }
        if (isPdf(content)) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean isJpeg(byte[] c) {
        return c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] c) {
        int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (c.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((c[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] c) {
        if (c.length < 12) {
            return false;
        }
        String riff = new String(c, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(c, 8, 4, StandardCharsets.US_ASCII);
        return "RIFF".equals(riff) && "WEBP".equals(webp);
    }

    private static boolean isPdf(byte[] c) {
        if (c.length < 5) {
            return false;
        }
        return "%PDF-".equals(new String(c, 0, 5, StandardCharsets.US_ASCII));
    }
}
