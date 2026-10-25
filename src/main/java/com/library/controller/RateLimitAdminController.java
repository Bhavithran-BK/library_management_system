package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.RateLimitResult;
import com.library.dto.RateLimitStatsDto;
import com.library.service.RateLimiterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Administrative and Diagnostics REST Controller for Distributed Token Bucket Rate Limiting (Member 4).
 */
@RestController
@RequestMapping("/api/admin/rate-limit")
public class RateLimitAdminController {

    private final RateLimiterService rateLimiterService;

    public RateLimitAdminController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Live diagnostics and metrics for rate limiting.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<RateLimitStatsDto>> getRateLimitStats() {
        RateLimitStatsDto stats = rateLimiterService.getStats();
        return ResponseEntity.ok(ApiResponse.success("Rate Limiter live diagnostics", stats));
    }

    /**
     * Resets rate limiter metrics and flushes Redis rate keys.
     */
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetRateLimits() {
        rateLimiterService.reset();
        return ResponseEntity.ok(ApiResponse.success("Rate limiter metrics and active keys successfully reset", Map.of(
                "status", "RESET_COMPLETE",
                "capacity", rateLimiterService.getCapacity(),
                "refillRatePerSec", rateLimiterService.getRefillRatePerSec()
        )));
    }

    /**
     * Live interactive simulation endpoint to demonstrate rate limiting, token depletion, and 429 throttling.
     *
     * @param count Number of requests in burst (default 120)
     * @return Execution breakdown with allowed vs throttled counts
     */
    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateBurst(
            @RequestParam(defaultValue = "120") int count,
            @RequestParam(defaultValue = "test-client-sim") String clientIdentifier) {

        int allowedCount = 0;
        int rejectedCount = 0;
        long lastRemaining = 0;
        long lastRetryAfter = 0;

        List<Map<String, Object>> samples = new ArrayList<>();
        String key = "rate:sim:" + clientIdentifier;

        for (int i = 1; i <= count; i++) {
            RateLimitResult result = rateLimiterService.checkLimit(key, 1);
            if (result.isAllowed()) {
                allowedCount++;
                lastRemaining = result.getRemainingTokens();
            } else {
                rejectedCount++;
                lastRetryAfter = result.getRetryAfterSeconds();
            }

            // Capture sample points for visualization (first 5, boundary around capacity, and last 5)
            if (i <= 5 || Math.abs(i - rateLimiterService.getCapacity()) <= 2 || i > count - 5) {
                samples.add(Map.of(
                        "requestIndex", i,
                        "allowed", result.isAllowed(),
                        "remainingTokens", result.getRemainingTokens(),
                        "retryAfter", result.getRetryAfterSeconds()
                ));
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("burstSize", count);
        response.put("allowedCount", allowedCount);
        response.put("rejectedCount", rejectedCount);
        response.put("bucketCapacity", rateLimiterService.getCapacity());
        response.put("refillRatePerSec", rateLimiterService.getRefillRatePerSec());
        response.put("lastRemainingTokens", lastRemaining);
        response.put("lastRetryAfterSeconds", lastRetryAfter);
        response.put("samplePoints", samples);

        return ResponseEntity.ok(ApiResponse.success("Burst rate limit simulation complete", response));
    }

    /**
     * Retrieves live token bucket remaining capacity for a specific simulated client without consuming tokens.
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getClientRemaining(@PathVariable String clientId) {
        RateLimitResult result = rateLimiterService.getClientRemaining(clientId);
        return ResponseEntity.ok(ApiResponse.success("Client bucket status", Map.of(
                "clientId", clientId,
                "remainingTokens", result.getRemainingTokens(),
                "allowed", result.isAllowed(),
                "retryAfterSeconds", result.getRetryAfterSeconds(),
                "capacity", rateLimiterService.getCapacity(),
                "refillRatePerSec", rateLimiterService.getRefillRatePerSec()
        )));
    }

    /**
     * Resets a specific simulated client's token bucket in Redis.
     */
    @PostMapping("/client/{clientId}/reset")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetClient(@PathVariable String clientId) {
        rateLimiterService.resetClient(clientId);
        return ResponseEntity.ok(ApiResponse.success("Client bucket reset successfully", Map.of(
                "clientId", clientId,
                "remainingTokens", rateLimiterService.getCapacity(),
                "capacity", rateLimiterService.getCapacity()
        )));
    }
}
