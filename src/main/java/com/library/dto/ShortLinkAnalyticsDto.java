package com.library.dto;

import java.time.Instant;

/**
 * Analytics DTO for a ShortLink (Member 4).
 */
public class ShortLinkAnalyticsDto {

    private String code;
    private String shortUrl;
    private String targetUrl;
    private String bookId;
    private String title;
    private long clickCount;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private boolean cachedInRedis;

    public ShortLinkAnalyticsDto() {
    }

    public ShortLinkAnalyticsDto(String code, String shortUrl, String targetUrl, String bookId, String title, long clickCount, Instant createdAt, Instant lastAccessedAt, boolean cachedInRedis) {
        this.code = code;
        this.shortUrl = shortUrl;
        this.targetUrl = targetUrl;
        this.bookId = bookId;
        this.title = title;
        this.clickCount = clickCount;
        this.createdAt = createdAt;
        this.lastAccessedAt = lastAccessedAt;
        this.cachedInRedis = cachedInRedis;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public boolean isCachedInRedis() {
        return cachedInRedis;
    }

    public void setCachedInRedis(boolean cachedInRedis) {
        this.cachedInRedis = cachedInRedis;
    }
}
