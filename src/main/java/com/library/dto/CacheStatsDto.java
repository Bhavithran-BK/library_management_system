package com.library.dto;

import java.io.Serializable;

/**
 * Redis cache hit/miss statistics and health status DTO.
 */
public class CacheStatsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private long bookCacheHits;
    private long bookCacheMisses;
    private long searchCacheHits;
    private long searchCacheMisses;
    private String hitRatio;
    private boolean connected;
    private long ttlSeconds;

    public CacheStatsDto() {
    }

    public CacheStatsDto(long bookCacheHits, long bookCacheMisses,
                         long searchCacheHits, long searchCacheMisses,
                         String hitRatio, boolean connected, long ttlSeconds) {
        this.bookCacheHits = bookCacheHits;
        this.bookCacheMisses = bookCacheMisses;
        this.searchCacheHits = searchCacheHits;
        this.searchCacheMisses = searchCacheMisses;
        this.hitRatio = hitRatio;
        this.connected = connected;
        this.ttlSeconds = ttlSeconds;
    }

    public long getBookCacheHits() {
        return bookCacheHits;
    }

    public void setBookCacheHits(long bookCacheHits) {
        this.bookCacheHits = bookCacheHits;
    }

    public long getBookCacheMisses() {
        return bookCacheMisses;
    }

    public void setBookCacheMisses(long bookCacheMisses) {
        this.bookCacheMisses = bookCacheMisses;
    }

    public long getSearchCacheHits() {
        return searchCacheHits;
    }

    public void setSearchCacheHits(long searchCacheHits) {
        this.searchCacheHits = searchCacheHits;
    }

    public long getSearchCacheMisses() {
        return searchCacheMisses;
    }

    public void setSearchCacheMisses(long searchCacheMisses) {
        this.searchCacheMisses = searchCacheMisses;
    }

    public String getHitRatio() {
        return hitRatio;
    }

    public void setHitRatio(String hitRatio) {
        this.hitRatio = hitRatio;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
