package com.library.dto;

import java.time.Instant;

/**
 * Response DTO for created or retrieved short links (Member 4).
 */
public class ShortLinkResponseDto {

    private String code;
    private String shortUrl;
    private String targetUrl;
    private String targetPath;
    private String bookId;
    private String title;
    private long clickCount;
    private Instant createdAt;
    private Instant lastAccessedAt;

    public ShortLinkResponseDto() {
    }

    public ShortLinkResponseDto(String code, String shortUrl, String targetUrl, String bookId, String title, long clickCount, Instant createdAt, Instant lastAccessedAt) {
        this.code = code;
        this.shortUrl = shortUrl;
        this.targetUrl = targetUrl;
        this.targetPath = targetUrl;
        this.bookId = bookId;
        this.title = title;
        this.clickCount = clickCount;
        this.createdAt = createdAt;
        this.lastAccessedAt = lastAccessedAt;
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
        this.targetPath = targetUrl;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
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
}
