package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.CreateShortLinkRequest;
import com.library.dto.ShortLinkAnalyticsDto;
import com.library.dto.ShortLinkResponseDto;
import com.library.service.ShortLinkService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * REST Controller for Base62 URL Shortener and Redirects (Member 4).
 */
@RestController
@RequestMapping
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    public ShortLinkController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    /**
     * Creates a new short link for a book or catalogue target URL.
     */
    @PostMapping("/api/short-links")
    public ResponseEntity<ApiResponse<ShortLinkResponseDto>> createShortLink(@RequestBody CreateShortLinkRequest request) {
        ShortLinkResponseDto response = shortLinkService.createShortLink(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Short link created successfully", response));
    }

    /**
     * Resolves short code and redirects (HTTP 302) to original target URL.
     */
    @GetMapping("/s/{code}")
    public ResponseEntity<Void> resolveShortLink(@PathVariable String code) {
        String targetUrl;
        try {
            targetUrl = shortLinkService.resolveShortLink(code);
        } catch (Exception e) {
            targetUrl = "/";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(targetUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    /**
     * Retrieves analytics and metadata for a given short code.
     */
    @GetMapping("/api/short-links/{code}/analytics")
    public ResponseEntity<ApiResponse<ShortLinkAnalyticsDto>> getAnalytics(@PathVariable String code) {
        ShortLinkAnalyticsDto analytics = shortLinkService.getAnalytics(code);
        return ResponseEntity.ok(ApiResponse.success("Short link analytics retrieved", analytics));
    }

    /**
     * Lists all generated short links for administrative overview and demonstration UI.
     */
    @GetMapping("/api/short-links")
    public ResponseEntity<ApiResponse<List<ShortLinkResponseDto>>> getAllShortLinks() {
        List<ShortLinkResponseDto> list = shortLinkService.getAllShortLinks();
        return ResponseEntity.ok(ApiResponse.success("Short links retrieved", list));
    }
}
