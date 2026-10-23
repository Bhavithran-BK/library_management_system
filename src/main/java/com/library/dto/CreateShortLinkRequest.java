package com.library.dto;

import java.time.Instant;

/**
 * Request payload for creating a short link (Member 4).
 * Supports targetUrl, targetPath, bookId, and title.
 */
public class CreateShortLinkRequest {

    private String targetUrl;
    private String targetPath;
    private String bookId;
    private String title;

    public CreateShortLinkRequest() {
    }

    public CreateShortLinkRequest(String targetUrl, String bookId) {
        this.targetUrl = targetUrl;
        this.bookId = bookId;
    }

    public String getResolvedTarget() {
        if (targetUrl != null && !targetUrl.isBlank()) {
            return targetUrl.trim();
        }
        if (targetPath != null && !targetPath.isBlank()) {
            return targetPath.trim();
        }
        return "/";
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
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
}
