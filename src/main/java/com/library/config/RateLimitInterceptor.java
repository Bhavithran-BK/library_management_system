package com.library.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.library.dto.ApiResponse;
import com.library.dto.RateLimitResult;
import com.library.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

/**
 * Spring WebMvc HandlerInterceptor for Distributed Token Bucket Rate Limiting (Member 4).
 * Intercepts protected requests, verifies token availability via Redis Lua scripts,
 * and sets standard RFC rate limiting response headers.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip rate limiting if disabled
        if (!rateLimiterService.isEnabled()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimiterService.getCapacity()));
            return true;
        }

        // Determine client identifier: authenticated username if logged in, otherwise client IP
        String clientKey = resolveClientKey(request);

        RateLimitResult result = rateLimiterService.checkLimit(clientKey, 1);

        // Populate standard rate limit response headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));

        if (result.isAllowed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));
            return true;
        } else {
            // Reject request with HTTP 429 Too Many Requests
            response.setStatus(429); // HttpStatus.TOO_MANY_REQUESTS
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            response.setContentType("application/json;charset=UTF-8");

            Map<String, Object> errorData = Map.of(
                    "retryAfterSeconds", result.getRetryAfterSeconds()
            );
            ApiResponse<Map<String, Object>> apiResponse = new ApiResponse<>(
                    false,
                    "Too Many Requests: Rate limit exceeded. Please retry after specified interval.",
                    errorData
            );

            try {
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                response.getWriter().flush();
            } catch (IOException ignored) {
            }

            return false;
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        // Check for authenticated user principal
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                    && auth.getName() != null
                    && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
                return "rate:user:" + auth.getName().trim();
            }
        } catch (Exception ignored) {
        }

        // Fallback to IP address (checking proxy forward header)
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp != null && !clientIp.isBlank()) {
            clientIp = clientIp.split(",")[0].trim();
        } else {
            clientIp = request.getRemoteAddr();
        }

        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "127.0.0.1";
        }
        return "rate:ip:" + clientIp;
    }
}
