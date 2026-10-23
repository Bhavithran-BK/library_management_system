package com.library.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Automated test suite for Distributed Token Bucket Rate Limiter (Member 4).
 * Verifies token depletion, replenishment calculations, 429 status code handling, and admin diagnostics.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RateLimiterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimiterService rateLimiterService;

    @BeforeEach
    public void setup() {
        rateLimiterService.reset();
    }

    @Test
    @DisplayName("Token Bucket checkLimit accurately decrements tokens and rejects when depleted")
    public void testTokenBucketDepletionAndRejection() {
        String clientKey = "rate:test:client-" + System.currentTimeMillis();
        int capacity = rateLimiterService.getCapacity();

        // 1. Consume all capacity tokens in one atomic request
        RateLimitResult first = rateLimiterService.checkLimit(clientKey, capacity);
        assertTrue(first.isAllowed());
        assertEquals(0, first.getRemainingTokens());
        assertEquals(0, first.getRetryAfterSeconds());

        // 2. Bucket is now fully depleted -> immediate next request must be rejected
        RateLimitResult throttled = rateLimiterService.checkLimit(clientKey, 1);
        assertFalse(throttled.isAllowed(), "Request beyond capacity must be throttled");
        assertEquals(0, throttled.getRemainingTokens());
        assertTrue(throttled.getRetryAfterSeconds() > 0, "Retry-After should be at least 1 second");
    }

    @Test
    @DisplayName("Protected API requests return standard rate limit headers (X-RateLimit-Limit, X-RateLimit-Remaining)")
    public void testRateLimitHeadersOnProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().string("X-RateLimit-Limit", String.valueOf(rateLimiterService.getCapacity())));
    }

    @Test
    @DisplayName("Excessive requests trigger HTTP 429 Too Many Requests with Retry-After header and JSON body")
    public void testHttp429TooManyRequests() throws Exception {
        String testIp = "192.168.100.42";
        int capacity = rateLimiterService.getCapacity();

        // Deplete token bucket for this IP in one atomic operation
        rateLimiterService.checkLimit("rate:ip:" + testIp, capacity);

        // The next HTTP request with this IP must receive HTTP 429
        mockMvc.perform(get("/api/books")
                        .header("X-Forwarded-For", testIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Too Many Requests")))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber());
    }

    @Test
    @DisplayName("GET /api/admin/rate-limit/stats returns live diagnostics and metrics")
    public void testRateLimitStatsEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/rate-limit/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.algorithm").value("Token Bucket (Redis Lua)"))
                .andExpect(jsonPath("$.data.bucketCapacity").value(rateLimiterService.getCapacity()))
                .andExpect(jsonPath("$.data.refillRatePerSec").value(rateLimiterService.getRefillRatePerSec()))
                .andExpect(jsonPath("$.data.totalRequests").isNumber())
                .andExpect(jsonPath("$.data.allowedRequests").isNumber())
                .andExpect(jsonPath("$.data.rejectedRequests").isNumber());
    }

    @Test
    @DisplayName("POST /api/admin/rate-limit/simulate executes burst traffic test and returns allowed/rejected breakdown")
    public void testBurstSimulationEndpoint() throws Exception {
        int burstCount = 120;
        int capacity = rateLimiterService.getCapacity();

        mockMvc.perform(post("/api/admin/rate-limit/simulate")
                        .param("count", String.valueOf(burstCount))
                        .param("clientIdentifier", "automated-test-client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.burstSize").value(burstCount))
                .andExpect(jsonPath("$.data.allowedCount").value(greaterThanOrEqualTo(capacity)))
                .andExpect(jsonPath("$.data.rejectedCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.samplePoints").isArray());
    }
}
