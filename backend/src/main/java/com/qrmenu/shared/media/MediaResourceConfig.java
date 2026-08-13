package com.qrmenu.shared.media;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves LocalFileMediaStorageAdapter's product-image output back out over HTTP. Product
 * images are shown on the public customer menu (no auth by design, Section 3), so
 * /media/product-images/** is a plain static resource mapping - not behind /api/**, so it
 * is unaffected by CorsConfig/RateLimitFilter (a plain <img> tag needs neither CORS nor a
 * rate-limited JSON API).
 *
 * Expense receipts are deliberately NOT mapped here: unlike product images they are never
 * shown on any unauthenticated surface, and can contain sensitive vendor/amount
 * information, so a bare "hard to guess UUID" posture isn't enough for them. Receipts are
 * only reachable through StaffExpenseController#getReceipt, which resolves the caller's
 * session, checks Permission.EXPENSE_VIEW, and confirms the requested Expense belongs to
 * the caller's own business/branch before reading the file via MediaStoragePort#load.
 */
@Configuration
class MediaResourceConfig implements WebMvcConfigurer {

    private final String baseDir;

    MediaResourceConfig(@Value("${media.storage.local.base-dir:./data/media}") String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String productImagesDir = MediaCategory.PRODUCT_IMAGE.directoryName();
        String location = Path.of(baseDir, productImagesDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/media/" + productImagesDir + "/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
