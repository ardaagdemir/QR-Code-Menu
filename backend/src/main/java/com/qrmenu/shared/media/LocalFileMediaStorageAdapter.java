package com.qrmenu.shared.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Only MediaStoragePort adapter today (Section 27: production provider not chosen yet).
 * Files are written under a local directory keyed by a server-generated UUID filename -
 * the original filename is never trusted (no path traversal / extension-spoofing
 * surface), and the detected (sniffed, not declared) file type decides the extension.
 * Product images are served back out via MediaResourceConfig's public static resource
 * handler at /media/product-images/**; receipts are not on any public path and are only
 * readable through {@link #load}, which callers reach via an authenticated, tenant-scoped
 * endpoint (see StaffExpenseController#getReceipt).
 */
@Component
public class LocalFileMediaStorageAdapter implements MediaStoragePort {

    private static final String MEDIA_URL_SEGMENT = "/media/";

    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalFileMediaStorageAdapter(
            @Value("${media.storage.local.base-dir:./data/media}") String baseDir,
            @Value("${media.storage.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.baseDir = Path.of(baseDir);
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create media storage directory: " + this.baseDir, e);
        }
    }

    @Override
    public StoredMedia store(MediaCategory category, byte[] content) {
        if (content == null || content.length == 0) {
            throw new MediaValidationException("Uploaded file is empty");
        }
        if (content.length > category.maxSizeBytes()) {
            throw new MediaValidationException(
                    "File exceeds the maximum allowed size of " + (category.maxSizeBytes() / (1024 * 1024)) + "MB");
        }
        DetectedFileType detected = DetectedFileType.sniff(content)
                .filter(category::allows)
                .orElseThrow(() -> new MediaValidationException("Unsupported or unrecognized file type for " + category));

        String filename = UUID.randomUUID() + "." + detected.extension();
        Path categoryDir = baseDir.resolve(category.directoryName());
        try {
            Files.createDirectories(categoryDir);
            Files.write(categoryDir.resolve(filename), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store uploaded file", e);
        }

        String key = category.directoryName() + "/" + filename;
        return new StoredMedia(key, publicBaseUrl + MEDIA_URL_SEGMENT + key);
    }

    @Override
    public Optional<LoadedMedia> load(String key) {
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir) || !Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        byte[] content;
        try {
            content = Files.readAllBytes(resolved);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read stored file: " + key, e);
        }
        String contentType = DetectedFileType.sniff(content)
                .map(DetectedFileType::contentType)
                .orElse("application/octet-stream");
        return Optional.of(new LoadedMedia(content, contentType));
    }

    @Override
    public void delete(String key) {
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            return;
        }
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete stored file: " + key, e);
        }
    }

    @Override
    public Optional<String> resolveKeyFromUrl(String url) {
        if (url == null) {
            return Optional.empty();
        }
        int index = url.indexOf(MEDIA_URL_SEGMENT);
        if (index < 0) {
            return Optional.empty();
        }
        return Optional.of(url.substring(index + MEDIA_URL_SEGMENT.length()));
    }
}
