package com.library.dto;

public class BenchmarkRequest {

    private int count = 10000;
    private int threads = 8;
    private String strategy = "snowflake";

    public BenchmarkRequest() {
    }

    public BenchmarkRequest(int count, int threads, String strategy) {
        this.count = count;
        this.threads = threads;
        this.strategy = strategy;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }
}
