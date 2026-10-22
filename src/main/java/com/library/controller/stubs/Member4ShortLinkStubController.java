package com.library.controller.stubs;

import com.library.dto.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration Interface for Member 4 (Rate Limiter, URL Shortener and Evaluation).
 * Provides agreed API contract endpoints for short links and rate limiting metrics.
 */
@RestController
@RequestMapping
public class Member4ShortLinkStubController {

    private final Map<String, String> shortLinkStore = new ConcurrentHashMap<>();

    @PostMapping("/api/short-links")
    public ResponseEntity<ApiResponse<Map<String, String>>> createShortLink(@RequestBody Map<String, String> request) {
        String targetPath = request.getOrDefault("targetPath", "/");
        // Lightweight default code generator for demonstration until Member 4 connects Redis/custom shortener
        String code = Integer.toHexString(targetPath.hashCode()).toLowerCase();
        shortLinkStore.put(code, targetPath);

        Map<String, String> result = new HashMap<>();
        result.put("code", code);
        result.put("shortUrl", "/s/" + code);
        result.put("targetPath", targetPath);
        result.put("status", "ACTIVE");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Short link created (Member 4 integration interface)", result));
    }

    @GetMapping("/s/{code}")
    public ResponseEntity<Void> resolveShortLink(@PathVariable String code) {
        String target = shortLinkStore.getOrDefault(code, "/");
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(target));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/api/admin/rate-limit/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRateLimitStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("status", "READY_FOR_MEMBER_4_INTEGRATION");
        stats.put("targetModule", "Member 4 Token Bucket Rate Limiter");
        stats.put("algorithm", "Token Bucket (Redis)");
        stats.put("metricsTemplate", Map.of(
                "allowedRequests", 0,
                "rejectedRequests", 0,
                "rejectionRatePercent", 0.0,
                "bucketCapacity", 100,
                "refillRatePerSec", 20
        ));
        return ResponseEntity.ok(ApiResponse.success("Member 4 Rate Limiter metrics interface ready", stats));
    }
}
