package com.library.dto;

import java.util.List;

public class BenchmarkResultDto {

    private int totalGenerated;
    private int concurrencyThreads;
    private long durationMs;
    private double idsPerSecond;
    private int collisions;
    private String strategy;
    private List<String> sampleIds;

    public BenchmarkResultDto() {
    }

    public BenchmarkResultDto(int totalGenerated, int concurrencyThreads, long durationMs,
                              double idsPerSecond, int collisions, String strategy, List<String> sampleIds) {
        this.totalGenerated = totalGenerated;
        this.concurrencyThreads = concurrencyThreads;
        this.durationMs = durationMs;
        this.idsPerSecond = idsPerSecond;
        this.collisions = collisions;
        this.strategy = strategy;
        this.sampleIds = sampleIds;
    }

    public int getTotalGenerated() {
        return totalGenerated;
    }

    public void setTotalGenerated(int totalGenerated) {
        this.totalGenerated = totalGenerated;
    }

    public int getConcurrencyThreads() {
        return concurrencyThreads;
    }

    public void setConcurrencyThreads(int concurrencyThreads) {
        this.concurrencyThreads = concurrencyThreads;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public double getIdsPerSecond() {
        return idsPerSecond;
    }

    public void setIdsPerSecond(double idsPerSecond) {
        this.idsPerSecond = idsPerSecond;
    }

    public int getCollisions() {
        return collisions;
    }

    public void setCollisions(int collisions) {
        this.collisions = collisions;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<String> getSampleIds() {
        return sampleIds;
    }

    public void setSampleIds(List<String> sampleIds) {
        this.sampleIds = sampleIds;
    }
}
