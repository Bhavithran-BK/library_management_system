package com.library.controller;

import com.library.dto.*;
import com.library.exception.BadRequestException;
import com.library.exception.GlobalExceptionHandler;
import com.library.model.Book;
import com.library.service.CrawlerService;
import com.library.service.RedisCacheService;
import com.library.service.SearchService;
import com.library.service.TrieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SearchAndCrawlerControllerTest {

    private MockMvc mockMvcSearch;
    private MockMvc mockMvcCrawler;

    private SearchService searchService;
    private TrieService trieService;
    private RedisCacheService cacheService;
    private CrawlerService crawlerService;

    @BeforeEach
    public void setup() {
        searchService = mock(SearchService.class);
        trieService = mock(TrieService.class);
        cacheService = mock(RedisCacheService.class);
        crawlerService = mock(CrawlerService.class);

        SearchController searchController = new SearchController(searchService, trieService, cacheService);
        CrawlerController crawlerController = new CrawlerController(crawlerService);

        mockMvcSearch = MockMvcBuilders.standaloneSetup(searchController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvcCrawler = MockMvcBuilders.standaloneSetup(crawlerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/search returns 200 with SearchResultDto")
    public void testSearchEndpoint() throws Exception {
        Book b = new Book("BK-01", "Clean Code", List.of("Robert C. Martin"), "978-0132350884",
                "Software Engineering", "Desc", 5, 4, "Shelf A", "INTERNAL");
        SearchResultDto<Book> dto = new SearchResultDto<>(List.of(b), 0, 10, 1L, 1, "clean");

        when(searchService.search(eq("clean"), eq(0), eq(10), eq(true))).thenReturn(dto);

        mockMvcSearch.perform(get("/api/search")
                        .param("q", "clean")
                        .param("page", "0")
                        .param("size", "10")
                        .param("availability", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.query").value("clean"))
                .andExpect(jsonPath("$.data.content[0].title").value("Clean Code"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/search/suggest returns 200 with suggestions list")
    public void testSuggestEndpoint() throws Exception {
        when(trieService.getSuggestions("har", 5)).thenReturn(List.of("Harry Potter", "Haruki Murakami"));

        mockMvcSearch.perform(get("/api/search/suggest")
                        .param("prefix", "har")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.prefix").value("har"))
                .andExpect(jsonPath("$.data.suggestions[0]").value("Harry Potter"))
                .andExpect(jsonPath("$.data.suggestions[1]").value("Haruki Murakami"));
    }

    @Test
    @DisplayName("GET /api/search/cache/stats returns 200 with cache metrics")
    public void testCacheStatsEndpoint() throws Exception {
        CacheStatsDto stats = new CacheStatsDto(10, 2, 20, 5, "81.08%", true, 300);
        when(cacheService.getStats()).thenReturn(stats);

        mockMvcSearch.perform(get("/api/search/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bookCacheHits").value(10))
                .andExpect(jsonPath("$.data.searchCacheHits").value(20))
                .andExpect(jsonPath("$.data.connected").value(true));
    }

    @Test
    @DisplayName("POST /api/crawler/run with valid seed returns 200 and crawl stats")
    public void testCrawlerRunSuccess() throws Exception {
        CrawlResultDto result = new CrawlResultDto("http://localhost:8080/catalogue", 2, 4, 1, 0, "COMPLETED", List.of());
        when(crawlerService.runCrawl(anyString())).thenReturn(result);

        mockMvcCrawler.perform(post("/api/crawler/run")
                        .param("seedUrl", "http://localhost:8080/catalogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pagesVisited").value(2))
                .andExpect(jsonPath("$.data.validRecordsExtracted").value(4));
    }

    @Test
    @DisplayName("POST /api/crawler/run with disallowed domain returns 400 Bad Request")
    public void testCrawlerRunDisallowedDomain() throws Exception {
        when(crawlerService.runCrawl(contains("external-bad-domain"))).thenThrow(new BadRequestException("Domain not permitted"));

        mockMvcCrawler.perform(post("/api/crawler/run")
                        .param("seedUrl", "https://external-bad-domain.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Domain not permitted"));
    }

    @Test
    @DisplayName("GET /api/crawler/simulated-source returns HTML page")
    public void testSimulatedSourceEndpoint() throws Exception {
        mockMvcCrawler.perform(get("/api/crawler/simulated-source").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Technical Books Catalogue")));
    }
}
