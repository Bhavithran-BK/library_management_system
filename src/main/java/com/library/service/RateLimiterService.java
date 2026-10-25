package com.library.service;

import com.library.dto.RateLimitResult;
import com.library.dto.RateLimitStatsDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distributed Token Bucket Rate Limiter Service backed by Redis Lua Scripts (Member 4).
 * Enforces per-client token buckets with atomic replenishment, burst tolerance,
 * and high-availability fallback.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled = true;

    @Value("${app.rate-limit.capacity:100}")
    private int capacity = 100;

    @Value("${app.rate-limit.refill-rate-per-sec:10}")
    private int refillRatePerSec = 10;

    @Value("${app.rate-limit.protected-paths:/api/books/**,/api/loans/**,/api/search/**,/api/short-links/**}")
    private String protectedPathsRaw;

    private DefaultRedisScript<List> rateLimiterScript;

    // Observability & Evaluation Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong allowedRequests = new AtomicLong(0);
    private final AtomicLong rejectedRequests = new AtomicLong(0);

    // High-Availability In-Memory Fallback
    private final Map<String, FallbackBucket> inMemoryFallbackBuckets = new ConcurrentHashMap<>();

    public RateLimiterService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            this.rateLimiterScript = new DefaultRedisScript<>();
            this.rateLimiterScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/rate_limiter.lua")));
            this.rateLimiterScript.setResultType(List.class);
            log.info("Initialized Distributed Rate Limiter: capacity={}, refillRate={}/s, enabled={}",
                    capacity, refillRatePerSec, enabled);
        } catch (Exception e) {
            log.error("Failed to load Redis Lua script rate_limiter.lua: {}", e.getMessage(), e);
        }
    }

    /**
     * Checks rate limit for a client identifier.
     *
     * @param clientKey Client key (e.g. rate:ip:127.0.0.1 or rate:user:john)
     * @param requestedTokens Number of tokens requested (typically 1)
     * @return RateLimitResult containing allowance, remaining tokens, and retry-after
     */
    public RateLimitResult checkLimit(String clientKey, int requestedTokens) {
        totalRequests.incrementAndGet();

        if (!enabled) {
            allowedRequests.incrementAndGet();
            return new RateLimitResult(true, capacity, 0, capacity);
        }

        String redisKey = clientKey.startsWith("rate:") ? clientKey : "rate:" + clientKey;
        double refillRatePerMs = (double) refillRatePerSec / 1000.0;
        long nowMs = System.currentTimeMillis();

        try {
            List<String> keys = Collections.singletonList(redisKey);
            List<?> result = stringRedisTemplate.execute(
                    rateLimiterScript,
                    keys,
                    String.valueOf(capacity),
                    String.valueOf(refillRatePerMs),
                    String.valueOf(nowMs),
                    String.valueOf(requestedTokens)
            );

            if (result != null && result.size() >= 3) {
                Number allowedNum = (Number) result.get(0);
                Number remainingNum = (Number) result.get(1);
                Number retryAfterNum = (Number) result.get(2);

                boolean allowed = allowedNum != null && allowedNum.longValue() == 1L;
                long remaining = remainingNum != null ? Math.max(0, remainingNum.longValue()) : 0L;
                long retryAfter = retryAfterNum != null ? Math.max(0, retryAfterNum.longValue()) : 0L;

                if (allowed) {
                    allowedRequests.incrementAndGet();
                } else {
                    rejectedRequests.incrementAndGet();
                }

                return new RateLimitResult(allowed, remaining, retryAfter, capacity);
            }
        } catch (Exception ex) {
            log.warn("Redis error during rate limiting check for {}: {}. Using in-memory fallback.",
                    redisKey, ex.getMessage());
            return checkFallback(redisKey, requestedTokens, nowMs, refillRatePerMs);
        }

        // Default safety allowance if script returned empty
        allowedRequests.incrementAndGet();
        return new RateLimitResult(true, capacity, 0, capacity);
    }

    /**
     * High-availability in-memory token bucket fallback when Redis is unreachable.
     */
    private synchronized RateLimitResult checkFallback(String key, int requested, long nowMs, double refillRatePerMs) {
        FallbackBucket bucket = inMemoryFallbackBuckets.computeIfAbsent(key, k -> new FallbackBucket(capacity, nowMs));
        double elapsed = Math.max(0, nowMs - bucket.lastUpdated);
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * refillRatePerMs);
        bucket.lastUpdated = nowMs;

        if (bucket.tokens >= requested) {
            bucket.tokens -= requested;
            allowedRequests.incrementAndGet();
            return new RateLimitResult(true, (long) Math.floor(bucket.tokens), 0, capacity);
        } else {
            double missing = requested - bucket.tokens;
            long retryAfter = Math.max(1, (long) Math.ceil(missing / (refillRatePerMs * 1000.0)));
            rejectedRequests.incrementAndGet();
            return new RateLimitResult(false, (long) Math.floor(bucket.tokens), retryAfter, capacity);
        }
    }

    /**
     * Retrieves live diagnostics and metrics for rate limiting evaluation.
     */
    public RateLimitStatsDto getStats() {
        RateLimitStatsDto dto = new RateLimitStatsDto();
        dto.setStatus(enabled ? "ACTIVE" : "DISABLED");
        dto.setTargetModule("Distributed Token Bucket Rate Limiter");
        dto.setAlgorithm("Token Bucket (Redis Lua)");
        dto.setBucketCapacity(capacity);
        dto.setRefillRatePerSec(refillRatePerSec);

        long total = totalRequests.get();
        long allowed = allowedRequests.get();
        long rejected = rejectedRequests.get();
        dto.setTotalRequests(total);
        dto.setAllowedRequests(allowed);
        dto.setRejectedRequests(rejected);

        double rate = total > 0 ? ((double) rejected / total) * 100.0 : 0.0;
        dto.setRejectionRatePercent(Math.round(rate * 100.0) / 100.0);

        boolean redisOk = false;
        long keyCount = 0;
        try {
            Set<String> keys = stringRedisTemplate.keys("rate:*");
            redisOk = true;
            keyCount = keys != null ? keys.size() : 0;
        } catch (Exception e) {
            redisOk = false;
        }
        dto.setRedisConnected(redisOk);
        dto.setActiveKeysCount(keyCount);

        dto.setConfiguration(Map.of(
                "enabled", enabled,
                "capacity", capacity,
                "refillRatePerSec", refillRatePerSec,
                "protectedPaths", protectedPathsRaw != null ? protectedPathsRaw : ""
        ));

        return dto;
    }

    /**
     * Resets rate limiter metrics and flushes rate limit Redis keys.
     */
    public void reset() {
        totalRequests.set(0);
        allowedRequests.set(0);
        rejectedRequests.set(0);
        inMemoryFallbackBuckets.clear();
        try {
            Set<String> keys = stringRedisTemplate.keys("rate:*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Resets a specific simulated client's token bucket in Redis.
     */
    public void resetClient(String clientIdentifier) {
        String key = clientIdentifier.startsWith("rate:") ? clientIdentifier : "rate:sim:" + clientIdentifier;
        inMemoryFallbackBuckets.remove(key);
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }

    /**
     * Retrieves remaining token balance for a client without consuming tokens.
     */
    public RateLimitResult getClientRemaining(String clientIdentifier) {
        String key = clientIdentifier.startsWith("rate:") ? clientIdentifier : "rate:sim:" + clientIdentifier;
        return checkLimit(key, 0);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getRefillRatePerSec() {
        return refillRatePerSec;
    }

    public void setRefillRatePerSec(int refillRatePerSec) {
        this.refillRatePerSec = refillRatePerSec;
    }

    private static class FallbackBucket {
        double tokens;
        long lastUpdated;

        FallbackBucket(double capacity, long lastUpdated) {
            this.tokens = capacity;
            this.lastUpdated = lastUpdated;
        }
    }
}
