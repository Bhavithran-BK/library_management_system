package com.library.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration holder and safety boundaries for the controlled web crawler (Member 2).
 */
@Component
public class CrawlerConfig {

    @Value("${app.crawler.enabled:true}")
    private boolean enabled;

    @Value("${app.crawler.max-pages:20}")
    private int maxPages;

    @Value("${app.crawler.max-depth:2}")
    private int maxDepth;

    @Value("${app.crawler.timeout-ms:3000}")
    private int timeoutMs;

    @Value("${app.crawler.allowed-domains:localhost,127.0.0.1}")
    private String allowedDomainsRaw;

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public List<String> getAllowedDomains() {
        if (allowedDomainsRaw == null || allowedDomainsRaw.isBlank()) {
            return List.of("localhost", "127.0.0.1");
        }
        return Arrays.stream(allowedDomainsRaw.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void setAllowedDomainsRaw(String allowedDomainsRaw) {
        this.allowedDomainsRaw = allowedDomainsRaw;
    }
}
