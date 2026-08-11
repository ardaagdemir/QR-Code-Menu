package com.qrmenu.tenant.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
class InternalAdminSecurityConfig {

    @Bean
    FilterRegistrationBean<InternalAdminAuthFilter> internalAdminAuthFilter(
            @Value("${internal.admin.token:}") String internalAdminToken) {
        FilterRegistrationBean<InternalAdminAuthFilter> registration =
                new FilterRegistrationBean<>(new InternalAdminAuthFilter(internalAdminToken));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
