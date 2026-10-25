package com.library.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring WebMvc Configuration (Member 4).
 * Registers RateLimitInterceptor on protected API paths while excluding static assets
 * and administrative inspection diagnostics.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/admin/rate-limit/**", // Rate limiter diagnostics and reset
                        "/api/admin/**",            // Admin inspection endpoints
                        "/api/auth/**",             // Authentication
                        "/actuator/**",             // Spring health metrics
                        "/error",                   // Error dispatcher
                        "/",                        // Static home
                        "/index.html",
                        "/book.html",
                        "/simulation.html",
                        "/css/**",
                        "/js/**",
                        "/favicon.ico"
                );
    }
}
