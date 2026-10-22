package com.library.crawler;

import com.library.config.CrawlerConfig;
import com.library.dto.CrawlImportRequest;
import com.library.dto.CrawlImportResultDto;
import com.library.dto.CrawlResultDto;
import com.library.dto.CrawledBookDto;
import com.library.exception.BadRequestException;
import com.library.idgenerator.IdGeneratorService;
import com.library.model.Book;
import com.library.repository.BookRepository;
import com.library.service.CrawlerService;
import com.library.service.RedisCacheService;
import com.library.service.TrieService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CrawlerTest {

    private CrawlerConfig config;
    private UrlValidator urlValidator;
    private CatalogueParser parser;
    private BookRepository bookRepository;
    private TrieService trieService;
    private RedisCacheService cacheService;
    private IdGeneratorService idGeneratorService;
    private CrawlerService crawlerService;

    @BeforeEach
    public void setup() {
        config = new CrawlerConfig();
        config.setEnabled(true);
        config.setMaxPages(5);
        config.setMaxDepth(2);
        config.setTimeoutMs(2000);
        config.setAllowedDomainsRaw("localhost,127.0.0.1,testlibrary.org");

        urlValidator = new UrlValidator(config);
        parser = new CatalogueParser();

        bookRepository = mock(BookRepository.class);
        trieService = mock(TrieService.class);
        cacheService = mock(RedisCacheService.class);
        idGeneratorService = mock(IdGeneratorService.class);

        crawlerService = new CrawlerService(
                config,
                urlValidator,
                parser,
                bookRepository,
                trieService,
                cacheService,
                idGeneratorService
        );
    }

    @Test
    @DisplayName("UrlValidator: Valid allowed URL vs disallowed domain")
    public void testUrlValidation() {
        assertTrue(urlValidator.isValidUrl("http://localhost:8080/catalogue"));
        assertTrue(urlValidator.isValidUrl("https://testlibrary.org/books"));
        assertFalse(urlValidator.isValidUrl("not-a-url"));
        assertFalse(urlValidator.isValidUrl("ftp://localhost:8080"));

        assertTrue(urlValidator.isDomainAllowed("http://localhost:8080/catalogue"));
        assertTrue(urlValidator.isDomainAllowed("http://127.0.0.1:8080/api/books"));
        assertTrue(urlValidator.isDomainAllowed("https://sub.testlibrary.org/page1"));
        assertFalse(urlValidator.isDomainAllowed("https://malicious-external-site.com/hack"));
    }

    @Test
    @DisplayName("UrlValidator: URL normalization removes fragments and trailing slashes")
    public void testUrlNormalization() {
        assertEquals("http://localhost:8080/page", urlValidator.normalizeUrl("http://localhost:8080/page#section1"));
        assertEquals("http://localhost:8080/page", urlValidator.normalizeUrl("http://localhost:8080/page/"));
    }

    @Test
    @DisplayName("CatalogueParser: Extract book metadata from valid HTML")
    public void testCatalogueParserExtraction() {
        String html = """
            <html>
            <body>
              <div class="book-card" data-book="true">
                <h2 class="book-title">Designing Data-Intensive Applications</h2>
                <p class="book-authors">By Martin Kleppmann</p>
                <span class="book-isbn">978-1449373320</span>
                <span class="book-category">System Architecture</span>
                <p class="book-description">A deep dive into distributed data storage.</p>
                <span class="book-rating">4.9</span>
              </div>
              <div class="pagination">
                <a href="/catalogue/page2">Next</a>
              </div>
            </body>
            </html>
            """;

        Document doc = Jsoup.parse(html, "http://localhost:8080");
        CatalogueParser.ParseResult result = parser.parse(doc, "http://localhost:8080/catalogue");

        assertEquals(1, result.getValidBooks().size());
        CrawledBookDto book = result.getValidBooks().get(0);
        assertEquals("Designing Data-Intensive Applications", book.getTitle());
        assertEquals("Martin Kleppmann", book.getAuthors().get(0));
        assertEquals("978-1449373320", book.getIsbn());
        assertEquals("System Architecture", book.getCategory());
        assertEquals(4.9, book.getRating());
        assertEquals(1, result.getDiscoveredLinks().size());
    }

    @Test
    @DisplayName("CatalogueParser: Malformed book records without ISBN are skipped and counted")
    public void testCatalogueParserMalformedRecords() {
        String html = """
            <html>
            <body>
              <div class="book-card">
                <h2 class="book-title">Corrupted Entry</h2>
                <!-- Missing ISBN -->
                <p class="book-authors">Anonymous</p>
              </div>
            </body>
            </html>
            """;

        Document doc = Jsoup.parse(html, "http://localhost:8080");
        CatalogueParser.ParseResult result = parser.parse(doc, "http://localhost:8080/catalogue");

        assertEquals(0, result.getValidBooks().size());
        assertEquals(1, result.getInvalidRecordCount());
    }

    @Test
    @DisplayName("CrawlerService: Disallowed domain must throw BadRequestException")
    public void testDisallowedDomainThrowsException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                crawlerService.runCrawl("https://unauthorized-domain.com/books")
        );
        assertTrue(ex.getMessage().contains("Domain not permitted"));
    }

    @Test
    @DisplayName("CrawlerService: Invalid URL syntax must throw BadRequestException")
    public void testInvalidUrlThrowsException() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                crawlerService.runCrawl("not-a-valid-url")
        );
        assertTrue(ex.getMessage().contains("Invalid seed URL"));
    }

    @Test
    @DisplayName("CrawlerService: Import reviewed books saves to MongoDB, updates Trie, and evicts cache")
    public void testImportReviewedBooks() {
        when(idGeneratorService.generateBookId()).thenReturn("BK-CRAWLED-001");
        when(bookRepository.existsByIsbn("978-1111111111")).thenReturn(false);
        when(bookRepository.existsByIsbn("978-2222222222")).thenReturn(true); // duplicate

        Book savedBook = new Book("BK-CRAWLED-001", "Imported Book", List.of("Author A"), "978-1111111111",
                "Tech", "Desc", 3, 3, "Shelf", "MEMBER2_CRAWLER");
        when(bookRepository.save(any(Book.class))).thenReturn(savedBook);

        List<CrawledBookDto> booksToImport = List.of(
                new CrawledBookDto("Imported Book", List.of("Author A"), "978-1111111111", "Tech", "Desc", 4.5, "http://loc"),
                new CrawledBookDto("Duplicate Book", List.of("Author B"), "978-2222222222", "Tech", "Desc", 4.0, "http://loc")
        );

        CrawlImportResultDto result = crawlerService.importReviewedBooks(new CrawlImportRequest(booksToImport, 3, "Shelf"));

        assertEquals(1, result.getImportedCount());
        assertEquals(1, result.getSkippedDuplicateCount());
        assertEquals(1, result.getImportedBookIds().size());

        verify(bookRepository, times(1)).save(any(Book.class));
        verify(trieService, times(1)).addBook(savedBook);
        verify(cacheService, times(1)).evictAllSearchResults();
    }
}
