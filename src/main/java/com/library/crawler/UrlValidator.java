package com.library.crawler;

import com.library.config.CrawlerConfig;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Validates and normalizes URLs and enforces crawler domain restrictions.
 */
@Component
public class UrlValidator {

    private final CrawlerConfig crawlerConfig;

    public UrlValidator(CrawlerConfig crawlerConfig) {
        this.crawlerConfig = crawlerConfig;
    }

    /**
     * Check if a URL has valid syntax and an allowed scheme (http/https).
     */
    public boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Check if the host of the URL is in the allowed domain whitelist.
     */
    public boolean isDomainAllowed(String url) {
        if (!isValidUrl(url)) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost().toLowerCase();
            List<String> allowed = crawlerConfig.getAllowedDomains();
            for (String domain : allowed) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    return true;
                }
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Normalize URL by removing query fragments and trailing slashes.
     */
    public String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(url.trim());
            String normalizedPath = uri.getPath();
            if (normalizedPath != null && normalizedPath.length() > 1 && normalizedPath.endsWith("/")) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            URI clean = new URI(
                    uri.getScheme().toLowerCase(),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(),
                    uri.getPort(),
                    normalizedPath,
                    uri.getQuery(),
                    null // strip anchor/fragment
            );
            return clean.toString();
        } catch (Exception e) {
            return url.trim();
        }
    }
}
