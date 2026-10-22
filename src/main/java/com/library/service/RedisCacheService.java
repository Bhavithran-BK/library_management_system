package com.library.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.library.dto.CacheStatsDto;
import com.library.dto.SearchResultDto;
import com.library.model.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance Cache-Aside service backed by Redis (Member 2).
 * Handles deterministic key generation, JSON caching with TTL, invalidation,
 * and hit/miss observability metrics.
 */
@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.enabled:true}")
    private boolean cacheEnabled = true;

    @Value("${app.cache.ttl-seconds:300}")
    private long defaultTtlSeconds = 300;

    // Hit / Miss Metrics Counters
    private final AtomicLong bookCacheHits = new AtomicLong(0);
    private final AtomicLong bookCacheMisses = new AtomicLong(0);
    private final AtomicLong searchCacheHits = new AtomicLong(0);
    private final AtomicLong searchCacheMisses = new AtomicLong(0);

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // -------------------------------------------------------------
    // Book Details Caching: book:{bookId}
    // -------------------------------------------------------------

    public String generateBookKey(String bookId) {
        return "book:" + (bookId != null ? bookId.trim() : "null");
    }

    public Book getBook(String bookId) {
        if (!cacheEnabled || bookId == null) {
            return null;
        }

        String key = generateBookKey(bookId);
        long start = System.currentTimeMillis();
        try {
            Object raw = redisTemplate.opsForValue().get(key);
            long latency = System.currentTimeMillis() - start;
            if (raw != null) {
                bookCacheHits.incrementAndGet();
                log.info("CACHE HIT: {} (latency: {} ms)", key, latency);
                return convertValue(raw, Book.class);
            } else {
                bookCacheMisses.incrementAndGet();
                log.info("CACHE MISS: {} (latency: {} ms)", key, latency);
                return null;
            }
        } catch (Exception ex) {
            log.warn("Redis error on getBook [{}]: {}. Falling back to DB.", key, ex.getMessage());
            bookCacheMisses.incrementAndGet();
            return null;
        }
    }

    public void putBook(String bookId, Book book) {
        if (!cacheEnabled || bookId == null || book == null) {
            return;
        }

        String key = generateBookKey(bookId);
        try {
            redisTemplate.opsForValue().set(key, book, Duration.ofSeconds(defaultTtlSeconds));
            log.debug("CACHE PUT: {} (TTL: {}s)", key, defaultTtlSeconds);
        } catch (Exception ex) {
            log.warn("Redis error on putBook [{}]: {}", key, ex.getMessage());
        }
    }

    public void evictBook(String bookId) {
        if (!cacheEnabled || bookId == null) {
            return;
        }

        String key = generateBookKey(bookId);
        try {
            Boolean deleted = redisTemplate.delete(key);
            log.info("CACHE INVALIDATE: {} (result: {})", key, deleted);
        } catch (Exception ex) {
            log.warn("Redis error evicting book [{}]: {}", key, ex.getMessage());
        }
    }

    // -------------------------------------------------------------
    // Search Results Caching: search:{normalizedQuery}:{page}:{size}:{availability}
    // -------------------------------------------------------------

    public String generateSearchKey(String query, int page, int size, Boolean availability) {
        String normalizedQuery = (query != null && !query.isBlank()) ? query.trim().toLowerCase() : "*";
        String availStr = (availability != null) ? availability.toString() : "all";
        return String.format("search:%s:%d:%d:%s", normalizedQuery, page, size, availStr);
    }

    @SuppressWarnings("unchecked")
    public SearchResultDto<Book> getSearchResult(String cacheKey) {
        if (!cacheEnabled || cacheKey == null) {
            return null;
        }

        long start = System.currentTimeMillis();
        try {
            Object raw = redisTemplate.opsForValue().get(cacheKey);
            long latency = System.currentTimeMillis() - start;
            if (raw != null) {
                searchCacheHits.incrementAndGet();
                log.info("CACHE HIT: {} (latency: {} ms)", cacheKey, latency);
                return convertValue(raw, SearchResultDto.class);
            } else {
                searchCacheMisses.incrementAndGet();
                log.info("CACHE MISS: {} (latency: {} ms)", cacheKey, latency);
                return null;
            }
        } catch (Exception ex) {
            log.warn("Redis error on getSearchResult [{}]: {}. Falling back to DB.", cacheKey, ex.getMessage());
            searchCacheMisses.incrementAndGet();
            return null;
        }
    }

    public void putSearchResult(String cacheKey, SearchResultDto<Book> result) {
        if (!cacheEnabled || cacheKey == null || result == null) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofSeconds(defaultTtlSeconds));
            log.debug("CACHE PUT: {} (TTL: {}s)", cacheKey, defaultTtlSeconds);
        } catch (Exception ex) {
            log.warn("Redis error on putSearchResult [{}]: {}", cacheKey, ex.getMessage());
        }
    }

    /**
     * Invalidate all cached search results whenever catalogue metadata or availability changes.
     */
    public void evictAllSearchResults() {
        if (!cacheEnabled) {
            return;
        }

        try {
            Set<String> keysToDelete = redisTemplate.keys("search:*");
            if (keysToDelete != null && !keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.info("CACHE INVALIDATE: Evicted {} search cache keys", keysToDelete.size());
            }
        } catch (Exception ex) {
            log.warn("Redis error invalidating search cache: {}", ex.getMessage());
        }
    }

    /**
     * Evicts both individual book key and all search cache entries.
     */
    public void evictBookAndSearches(String bookId) {
        evictBook(bookId);
        evictAllSearchResults();
    }

    // -------------------------------------------------------------
    // Metrics & Diagnostics
    // -------------------------------------------------------------

    public CacheStatsDto getStats() {
        long bHits = bookCacheHits.get();
        long bMisses = bookCacheMisses.get();
        long sHits = searchCacheHits.get();
        long sMisses = searchCacheMisses.get();

        long totalHits = bHits + sHits;
        long totalRequests = totalHits + bMisses + sMisses;
        String ratioStr = (totalRequests > 0)
                ? String.format("%.2f%%", (totalHits * 100.0) / totalRequests)
                : "0.00%";

        boolean connected = isRedisConnected();

        return new CacheStatsDto(
                bHits, bMisses,
                sHits, sMisses,
                ratioStr,
                connected,
                defaultTtlSeconds
        );
    }

    public void resetMetrics() {
        bookCacheHits.set(0);
        bookCacheMisses.set(0);
        searchCacheHits.set(0);
        searchCacheMisses.set(0);
    }

    public boolean isRedisConnected() {
        try {
            String ping = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(ping);
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T convertValue(Object raw, Class<T> targetClass) {
        if (targetClass.isInstance(raw)) {
            return targetClass.cast(raw);
        }
        return objectMapper.convertValue(raw, targetClass);
    }
}
