package com.library.controller;

import com.library.dto.*;
import com.library.service.CrawlerService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for controlled web crawling, review, and import (Member 2).
 */
@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    /**
     * Run a controlled crawl starting from a seed URL.
     * POST /api/crawler/run?seedUrl=...
     */
    @PostMapping("/run")
    public ResponseEntity<ApiResponse<CrawlResultDto>> runCrawler(
            @RequestParam("seedUrl") String seedUrl) {
        CrawlResultDto result = crawlerService.runCrawl(seedUrl);
        return ResponseEntity.ok(ApiResponse.success("Crawler execution finished", result));
    }

    /**
     * Import reviewed books into the library catalogue.
     * POST /api/crawler/import
     */
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<CrawlImportResultDto>> importReviewedBooks(
            @Valid @RequestBody CrawlImportRequest request) {
        CrawlImportResultDto result = crawlerService.importReviewedBooks(request);
        return ResponseEntity.ok(ApiResponse.success("Books successfully imported into catalogue", result));
    }

    /**
     * Simulated catalogue source endpoint for safe offline demonstration and automated testing.
     * GET /api/crawler/simulated-source?page=1
     */
    @GetMapping(value = "/simulated-source", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getSimulatedCataloguePage(
            @RequestParam(name = "page", defaultValue = "1") int page) {
        String html = generateSimulatedHtml(page);
        return ResponseEntity.ok(html);
    }

    private String generateSimulatedHtml(int page) {
        if (page == 2) {
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head><title>Simulated Publisher Catalogue - Page 2</title></head>
                <body>
                  <h1>Technical Books Catalogue - Volume 2</h1>
                  <div class="book-card" data-book="true">
                    <h2 class="book-title">Kafka: The Definitive Guide</h2>
                    <p class="book-authors">By Neha Narkhede, Gwen Shapira, Todd Palino</p>
                    <span class="book-isbn">978-1491936160</span>
                    <span class="book-category">Distributed Systems</span>
                    <p class="book-description">Real-time data and stream processing at scale with Apache Kafka.</p>
                    <span class="book-rating">4.7</span>
                  </div>
                  <div class="book-card" data-book="true">
                    <h2 class="book-title">Designing Distributed Systems</h2>
                    <p class="book-authors">By Brendan Burns</p>
                    <span class="book-isbn">978-1491983645</span>
                    <span class="book-category">Cloud & Containers</span>
                    <p class="book-description">Patterns and paradigms for scalable, reliable containerized services.</p>
                    <span class="book-rating">4.5</span>
                  </div>
                  <!-- Malformed test item: missing ISBN to test crawler error handling -->
                  <div class="book-card" data-book="true">
                    <h2 class="book-title">Corrupted Record Without ISBN</h2>
                    <p class="book-authors">By Unknown Author</p>
                    <p class="book-description">This record has no ISBN and will be safely skipped.</p>
                  </div>
                  <div class="pagination">
                    <a href="/api/crawler/simulated-source?page=1">Previous Page</a>
                  </div>
                </body>
                </html>
                """;
        }

        // Default Page 1
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>Simulated Publisher Catalogue - Page 1</title></head>
            <body>
              <h1>Technical Books Catalogue - Volume 1</h1>
              <div class="book-card" data-book="true">
                <h2 class="book-title">Building Microservices: Designing Fine-Grained Systems</h2>
                <p class="book-authors">By Sam Newman</p>
                <span class="book-isbn">978-1492034025</span>
                <span class="book-category">Microservices</span>
                <p class="book-description">Comprehensive guide to modeling, integrating, testing, and deploying microservices.</p>
                <span class="book-rating">4.8</span>
              </div>
              <div class="book-card" data-book="true">
                <h2 class="book-title">Concurrency in Go: Tools and Techniques for Developers</h2>
                <p class="book-authors">By Katherine Cox-Buday</p>
                <span class="book-isbn">978-1491941195</span>
                <span class="book-category">Concurrency</span>
                <p class="book-description">Concurrency patterns, goroutines, channels, and synchronization primitives.</p>
                <span class="book-rating">4.6</span>
              </div>
              <div class="pagination">
                <a href="/api/crawler/simulated-source?page=2">Next Page</a>
              </div>
            </body>
            </html>
            """;
    }
}
