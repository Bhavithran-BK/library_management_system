package com.library.dto;

/**
 * Result representation for token bucket rate limit evaluation (Member 4).
 */
public class RateLimitResult {

    private final boolean allowed;
    private final long remainingTokens;
    private final long retryAfterSeconds;
    private final int limit;

    public RateLimitResult(boolean allowed, long remainingTokens, long retryAfterSeconds, int limit) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
        this.retryAfterSeconds = retryAfterSeconds;
        this.limit = limit;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public int getLimit() {
        return limit;
    }

    @Override
    public String toString() {
        return "RateLimitResult{" +
                "allowed=" + allowed +
                ", remainingTokens=" + remainingTokens +
                ", retryAfterSeconds=" + retryAfterSeconds +
                ", limit=" + limit +
                '}';
    }
}
