package com.library.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB Document Entity for URL Shortener (Member 4).
 * Stores durable metadata, click counters, and access timestamps.
 */
@Document(collection = "short_links")
public class ShortLink {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String originalUrl;
    private String bookId;
    private String title;
    private long clickCount;
    private Instant createdAt;
    private Instant lastAccessedAt;

    public ShortLink() {
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        this.clickCount = 0;
    }

    public ShortLink(String code, String originalUrl, String bookId, String title) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.bookId = bookId;
        this.title = title;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        this.clickCount = 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
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
