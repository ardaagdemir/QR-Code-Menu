package com.qrmenu.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * customer-web AND staff-web (separate origins/ports, e.g. http://localhost:3000 and
 * http://localhost:3002) call the public /api/** endpoints, each with its own
 * cookie (customer-web: qrmenu_session, staff-web: qrmenu_staff_session as of
 * Milestone 8), both sent via credentials: 'include' - so the browser requires
 * explicit (non-wildcard) allowed origins plus allowCredentials=true. Scoped to
 * /api/** only - /internal/** is a server-to-server bootstrap API, never called from
 * a browser, so it needs no CORS policy at all.
 */
@Configuration
class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    CorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3002}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type")
                .allowCredentials(true);
    }
}
