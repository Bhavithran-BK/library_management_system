package com.library.dto;

import java.util.Map;

/**
 * Diagnostics and observability metrics for Distributed Token Bucket Rate Limiter (Member 4).
 */
public class RateLimitStatsDto {

    private String status;
    private String targetModule;
    private String algorithm;
    private int bucketCapacity;
    private int refillRatePerSec;
    private long totalRequests;
    private long allowedRequests;
    private long rejectedRequests;
    private double rejectionRatePercent;
    private boolean redisConnected;
    private long activeKeysCount;
    private Map<String, Object> configuration;

    public RateLimitStatsDto() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTargetModule() {
        return targetModule;
    }

    public void setTargetModule(String targetModule) {
        this.targetModule = targetModule;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getBucketCapacity() {
        return bucketCapacity;
    }

    public void setBucketCapacity(int bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
    }

    public int getRefillRatePerSec() {
        return refillRatePerSec;
    }

    public void setRefillRatePerSec(int refillRatePerSec) {
        this.refillRatePerSec = refillRatePerSec;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getAllowedRequests() {
        return allowedRequests;
    }

    public void setAllowedRequests(long allowedRequests) {
        this.allowedRequests = allowedRequests;
    }

    public long getRejectedRequests() {
        return rejectedRequests;
    }

    public void setRejectedRequests(long rejectedRequests) {
        this.rejectedRequests = rejectedRequests;
    }

    public double getRejectionRatePercent() {
        return rejectionRatePercent;
    }

    public void setRejectionRatePercent(double rejectionRatePercent) {
        this.rejectionRatePercent = rejectionRatePercent;
    }

    public boolean isRedisConnected() {
        return redisConnected;
    }

    public void setRedisConnected(boolean redisConnected) {
        this.redisConnected = redisConnected;
    }

    public long getActiveKeysCount() {
        return activeKeysCount;
    }

    public void setActiveKeysCount(long activeKeysCount) {
        this.activeKeysCount = activeKeysCount;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }
}
