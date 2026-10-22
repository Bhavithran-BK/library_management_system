package com.library.service;

import com.library.config.CrawlerConfig;
import com.library.crawler.CatalogueParser;
import com.library.crawler.UrlValidator;
import com.library.dto.CrawlImportRequest;
import com.library.dto.CrawlImportResultDto;
import com.library.dto.CrawlResultDto;
import com.library.dto.CrawledBookDto;
import com.library.exception.BadRequestException;
import com.library.idgenerator.IdGeneratorService;
import com.library.model.Book;
import com.library.repository.BookRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * Controlled, safe web crawler service for catalogue discovery (Member 2).
 * Enforces domain whitelisting, crawl depth, max page thresholds, and deduplication.
 */
@Service
public class CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerService.class);

    private final CrawlerConfig crawlerConfig;
    private final UrlValidator urlValidator;
    private final CatalogueParser catalogueParser;
    private final BookRepository bookRepository;
    private final TrieService trieService;
    private final RedisCacheService cacheService;
    private final IdGeneratorService idGeneratorService;

    public CrawlerService(CrawlerConfig crawlerConfig,
                          UrlValidator urlValidator,
                          CatalogueParser catalogueParser,
                          BookRepository bookRepository,
                          TrieService trieService,
                          RedisCacheService cacheService,
                          IdGeneratorService idGeneratorService) {
        this.crawlerConfig = crawlerConfig;
        this.urlValidator = urlValidator;
        this.catalogueParser = catalogueParser;
        this.bookRepository = bookRepository;
        this.trieService = trieService;
        this.cacheService = cacheService;
        this.idGeneratorService = idGeneratorService;
    }

    private static class CrawlTarget {
        final String url;
        final int depth;

        CrawlTarget(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }
    }

    /**
     * Run a safe, controlled catalogue crawl starting at seedUrl.
     *
     * @param seedUrl the initial URL to crawl
     * @return CrawlResultDto with extracted books and statistics
     */
    public CrawlResultDto runCrawl(String seedUrl) {
        if (!crawlerConfig.isEnabled()) {
            throw new BadRequestException("Crawler is currently disabled by configuration");
        }

        if (!urlValidator.isValidUrl(seedUrl)) {
            throw new BadRequestException("Invalid seed URL format or unsupported scheme: " + seedUrl);
        }

        if (!urlValidator.isDomainAllowed(seedUrl)) {
            throw new BadRequestException("Domain not permitted. Allowed domains: " + crawlerConfig.getAllowedDomains());
        }

        int maxPages = crawlerConfig.getMaxPages();
        int maxDepth = crawlerConfig.getMaxDepth();
        int timeoutMs = crawlerConfig.getTimeoutMs();

        Set<String> visitedUrls = new HashSet<>();
        Set<String> seenIsbns = new HashSet<>();
        List<CrawledBookDto> extractedBooks = new ArrayList<>();
        Queue<CrawlTarget> queue = new ArrayDeque<>();

        String normalizedSeed = urlValidator.normalizeUrl(seedUrl);
        queue.add(new CrawlTarget(normalizedSeed, 0));
        visitedUrls.add(normalizedSeed);

        int pagesVisited = 0;
        int duplicateUrlsSkipped = 0;
        int invalidRecordsSkipped = 0;

        log.info("CRAWLER START: seedUrl='{}' maxPages={} maxDepth={} timeout={}ms",
                normalizedSeed, maxPages, maxDepth, timeoutMs);

        while (!queue.isEmpty() && pagesVisited < maxPages) {
            CrawlTarget target = queue.poll();
            pagesVisited++;

            try {
                log.debug("CRAWLER: fetching [{}] at depth {}", target.url, target.depth);
                Document doc = Jsoup.connect(target.url)
                        .timeout(timeoutMs)
                        .userAgent("LibraryCatalogueCrawler/1.0 (Academic Safe Prototype)")
                        .followRedirects(true)
                        .get();

                CatalogueParser.ParseResult parseResult = catalogueParser.parse(doc, target.url);
                invalidRecordsSkipped += parseResult.getInvalidRecordCount();

                // Deduplicate and collect extracted books
                for (CrawledBookDto book : parseResult.getValidBooks()) {
                    if (seenIsbns.add(book.getIsbn())) {
                        extractedBooks.add(book);
                    } else {
                        log.debug("CRAWLER: duplicate ISBN {} skipped within crawl run", book.getIsbn());
                    }
                }

                // If within max depth, enqueue discovered links
                if (target.depth < maxDepth) {
                    for (String link : parseResult.getDiscoveredLinks()) {
                        String normLink = urlValidator.normalizeUrl(link);
                        if (!urlValidator.isValidUrl(normLink) || !urlValidator.isDomainAllowed(normLink)) {
                            continue;
                        }
                        if (visitedUrls.contains(normLink)) {
                            duplicateUrlsSkipped++;
                        } else {
                            visitedUrls.add(normLink);
                            queue.add(new CrawlTarget(normLink, target.depth + 1));
                        }
                    }
                }

            } catch (IOException e) {
                log.warn("CRAWLER: failed to fetch page [{}] - {}", target.url, e.getMessage());
            } catch (Exception e) {
                log.error("CRAWLER: unexpected error on page [{}] - {}", target.url, e.getMessage());
            }
        }

        String status = "COMPLETED";
        log.info("CRAWLER: pagesVisited={} recordsExtracted={} duplicates={} invalidSkipped={}",
                pagesVisited, extractedBooks.size(), duplicateUrlsSkipped, invalidRecordsSkipped);

        return new CrawlResultDto(
                seedUrl,
                pagesVisited,
                extractedBooks.size(),
                duplicateUrlsSkipped,
                invalidRecordsSkipped,
                status,
                extractedBooks
        );
    }

    /**
     * Import reviewed books into the library catalogue.
     * Prevents duplicate ISBNs, generates unique IDs, updates Trie, and invalidates Redis cache.
     */
    public CrawlImportResultDto importReviewedBooks(CrawlImportRequest request) {
        if (request == null || request.getBooks() == null || request.getBooks().isEmpty()) {
            throw new BadRequestException("No books provided for import");
        }

        int defaultCopies = (request.getDefaultCopies() != null && request.getDefaultCopies() > 0)
                ? request.getDefaultCopies() : 3;
        String location = StringUtils.hasText(request.getLocation())
                ? request.getLocation().trim() : "Main Catalogue / Member 2 Crawler Import";

        int importedCount = 0;
        int skippedDuplicates = 0;
        List<String> importedIds = new ArrayList<>();

        for (CrawledBookDto dto : request.getBooks()) {
            if (dto.getIsbn() == null || dto.getTitle() == null) {
                continue;
            }

            String cleanIsbn = dto.getIsbn().trim();
            if (bookRepository.existsByIsbn(cleanIsbn)) {
                log.info("IMPORT: Book with ISBN '{}' already exists in catalogue. Skipping.", cleanIsbn);
                skippedDuplicates++;
                continue;
            }

            String bookId = idGeneratorService.generateBookId();
            Book book = new Book(
                    bookId,
                    dto.getTitle().trim(),
                    dto.getAuthors() != null && !dto.getAuthors().isEmpty() ? dto.getAuthors() : List.of("Unknown Author"),
                    cleanIsbn,
                    StringUtils.hasText(dto.getCategory()) ? dto.getCategory().trim() : "General",
                    StringUtils.hasText(dto.getDescription()) ? dto.getDescription().trim() : "Imported via Crawler",
                    defaultCopies,
                    defaultCopies,
                    location,
                    "MEMBER2_CRAWLER"
            );

            Book saved = bookRepository.save(book);
            importedIds.add(saved.getBookId());
            importedCount++;

            // Update Trie with new book terms
            trieService.addBook(saved);
        }

        // Invalidate Redis search cache so new books appear immediately
        if (importedCount > 0) {
            cacheService.evictAllSearchResults();
        }

        log.info("IMPORT COMPLETED: {} books imported, {} duplicates skipped", importedCount, skippedDuplicates);
        return new CrawlImportResultDto(importedCount, skippedDuplicates, importedIds);
    }
}
