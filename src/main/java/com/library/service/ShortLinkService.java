package com.library.service;

import com.library.dto.CreateShortLinkRequest;
import com.library.dto.ShortLinkAnalyticsDto;
import com.library.dto.ShortLinkResponseDto;
import com.library.exception.ResourceNotFoundException;
import com.library.model.ShortLink;
import com.library.repository.ShortLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Dual-Tier Persistence URL Shortener Service (Member 4).
 * Tier 1: Redis Key-Value Cache (shortlink:{code} -> targetUrl) for sub-millisecond redirects.
 * Tier 2: MongoDB (short_links collection) for durable analytics, click counts, and auditing.
 */
@Service
public class ShortLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);

    private static final String REDIS_SHORTLINK_PREFIX = "shortlink:";

    private final ShortLinkRepository shortLinkRepository;
    private final Base62Service base62Service;
    private final StringRedisTemplate stringRedisTemplate;
    private final MongoTemplate mongoTemplate;

    @Value("${app.short-link.cache-ttl-hours:24}")
    private long cacheTtlHours = 24;

    public ShortLinkService(ShortLinkRepository shortLinkRepository,
                            Base62Service base62Service,
                            StringRedisTemplate stringRedisTemplate,
                            MongoTemplate mongoTemplate) {
        this.shortLinkRepository = shortLinkRepository;
        this.base62Service = base62Service;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Creates a new short link or returns existing link if already created for this target/book.
     */
    public ShortLinkResponseDto createShortLink(CreateShortLinkRequest request) {
        String targetUrl = request.getResolvedTarget();
        String bookId = request.getBookId();
        String title = request.getTitle();

        // Check if an existing link exists for this bookId or targetUrl
        Optional<ShortLink> existing = Optional.empty();
        if (bookId != null && !bookId.isBlank()) {
            existing = shortLinkRepository.findByBookId(bookId.trim());
        }
        if (existing.isEmpty() && targetUrl != null && !targetUrl.isBlank()) {
            existing = shortLinkRepository.findByOriginalUrl(targetUrl);
        }

        if (existing.isPresent()) {
            ShortLink link = existing.get();
            cacheInRedis(link.getCode(), link.getOriginalUrl());
            return toResponseDto(link);
        }

        // Generate a unique Base62 code
        String code = base62Service.generateShortCode(targetUrl, bookId);
        int attempts = 0;
        while (shortLinkRepository.existsByCode(code) && attempts < 5) {
            code = base62Service.generateShortCode(targetUrl + attempts, bookId);
            attempts++;
        }

        ShortLink shortLink = new ShortLink(code, targetUrl, bookId, title);
        ShortLink saved = shortLinkRepository.save(shortLink);

        // Tier 1: Cache in Redis
        cacheInRedis(code, targetUrl);

        log.info("Created ShortLink: code={}, targetUrl={}, bookId={}", code, targetUrl, bookId);
        return toResponseDto(saved);
    }

    /**
     * Resolves short link code to target URL.
     * Tier 1 (Redis): Sub-millisecond lookup.
     * Tier 2 (MongoDB): Durable fallback with cache-aside warming.
     *
     * @param code Base62 short code
     * @return Target URL
     */
    public String resolveShortLink(String code) {
        if (code == null || code.isBlank()) {
            return "/";
        }

        String cacheKey = REDIS_SHORTLINK_PREFIX + code;

        // Tier 1: Check Redis
        try {
            String cachedUrl = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedUrl != null && !cachedUrl.isBlank()) {
                log.debug("SHORTLINK CACHE HIT: code={} -> {}", code, cachedUrl);
                recordAccessAsync(code);
                return cachedUrl;
            }
        } catch (Exception ex) {
            log.warn("Redis error resolving shortlink {}: {}", code, ex.getMessage());
        }

        // Tier 2: Check MongoDB
        Optional<ShortLink> entityOpt = shortLinkRepository.findByCode(code);
        if (entityOpt.isPresent()) {
            ShortLink link = entityOpt.get();
            log.info("SHORTLINK CACHE MISS (Found in Mongo): code={} -> {}", code, link.getOriginalUrl());
            cacheInRedis(code, link.getOriginalUrl());
            recordAccessAsync(code);
            return link.getOriginalUrl();
        }

        throw new ResourceNotFoundException("Short link not found for code: " + code);
    }

    /**
     * Retrieves analytics and metadata for a short link.
     */
    public ShortLinkAnalyticsDto getAnalytics(String code) {
        ShortLink link = shortLinkRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Short link not found: " + code));

        boolean isCached = false;
        try {
            Boolean hasKey = stringRedisTemplate.hasKey(REDIS_SHORTLINK_PREFIX + code);
            isCached = Boolean.TRUE.equals(hasKey);
        } catch (Exception ignored) {
        }

        return new ShortLinkAnalyticsDto(
                link.getCode(),
                "/s/" + link.getCode(),
                link.getOriginalUrl(),
                link.getBookId(),
                link.getTitle(),
                link.getClickCount(),
                link.getCreatedAt(),
                link.getLastAccessedAt(),
                isCached
        );
    }

    /**
     * Returns all short links sorted by creation time descending.
     */
    public List<ShortLinkResponseDto> getAllShortLinks() {
        return shortLinkRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    private void cacheInRedis(String code, String targetUrl) {
        try {
            stringRedisTemplate.opsForValue().set(
                    REDIS_SHORTLINK_PREFIX + code,
                    targetUrl,
                    Duration.ofHours(cacheTtlHours)
            );
        } catch (Exception ex) {
            log.warn("Failed to cache shortlink in Redis: {}", ex.getMessage());
        }
    }

    /**
     * Asynchronously updates click count and last access timestamp in MongoDB.
     */
    private void recordAccessAsync(String code) {
        CompletableFuture.runAsync(() -> {
            try {
                Query query = new Query(Criteria.where("code").is(code));
                Update update = new Update()
                        .inc("clickCount", 1)
                        .set("lastAccessedAt", Instant.now());
                mongoTemplate.updateFirst(query, update, ShortLink.class);
            } catch (Exception ex) {
                log.warn("Failed to record access analytics for code {}: {}", code, ex.getMessage());
            }
        });
    }

    private ShortLinkResponseDto toResponseDto(ShortLink link) {
        return new ShortLinkResponseDto(
                link.getCode(),
                "/s/" + link.getCode(),
                link.getOriginalUrl(),
                link.getBookId(),
                link.getTitle(),
                link.getClickCount(),
                link.getCreatedAt(),
                link.getLastAccessedAt()
        );
    }
}
