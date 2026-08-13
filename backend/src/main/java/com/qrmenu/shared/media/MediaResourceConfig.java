package com.qrmenu.shared.media;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves LocalFileMediaStorageAdapter's output back out over HTTP. Product images are
 * shown on the public customer menu (no auth by design, Section 3), so /media/** is a
 * plain static resource mapping - not behind /api/**, so it is unaffected by CorsConfig/
 * RateLimitFilter (a plain <img> tag needs neither CORS nor a rate-limited JSON API).
 * Receipt files reuse the same public path (upload itself is already permission-gated,
 * Section 22/21 - the filename is a high-entropy UUID, the same "hard to guess, low
 * blast radius" posture the QR/orderTrackingToken already use in this codebase).
 */
@Configuration
class MediaResourceConfig implements WebMvcConfigurer {

    private final String baseDir;

    MediaResourceConfig(@Value("${media.storage.local.base-dir:./data/media}") String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(baseDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/media/**").addResourceLocations(location).setCachePeriod(3600);
    }
}
