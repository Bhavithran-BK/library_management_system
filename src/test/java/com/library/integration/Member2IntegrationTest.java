package com.library.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.CrawlImportRequest;
import com.library.dto.CrawledBookDto;
import com.library.model.Book;
import com.library.repository.BookRepository;
import com.library.service.RedisCacheService;
import com.library.service.TrieService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class Member2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private TrieService trieService;

    @Autowired
    private RedisCacheService redisCacheService;

    @Test
    @DisplayName("Member 2 Integration: Full lifecycle test covering Search, Trie, Redis cache-aside, Invalidation, and Crawler import")
    public void testMember2FullLifecycle() throws Exception {
        // 1. Trie Autocomplete suggestion verification
        mockMvc.perform(get("/api/search/suggest")
                        .param("prefix", "cle")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestions").isArray())
                .andExpect(jsonPath("$.data.suggestions[0]").exists());

        // 2. Search API: First request (Cache MISS)
        redisCacheService.evictAllSearchResults();
        long initialMisses = redisCacheService.getStats().getSearchCacheMisses();
        long initialHits = redisCacheService.getStats().getSearchCacheHits();

        mockMvc.perform(get("/api/search")
                        .param("q", "clean")
                        .param("page", "0")
                        .param("size", "10")
                        .param("availability", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        assertEquals(initialMisses + 1, redisCacheService.getStats().getSearchCacheMisses(),
                "First search must record a Redis cache miss and query MongoDB");

        // 3. Search API: Second identical request (Cache HIT)
        mockMvc.perform(get("/api/search")
                        .param("q", "clean")
                        .param("page", "0")
                        .param("size", "10")
                        .param("availability", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals(initialHits + 1, redisCacheService.getStats().getSearchCacheHits(),
                "Second identical search must be served directly from Redis cache (HIT)");

        // 4. Cache Stats Endpoint
        mockMvc.perform(get("/api/search/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.searchCacheHits").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.searchCacheMisses").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // 5. Crawler Simulated Source & Safe Crawler Execution
        mockMvc.perform(get("/api/crawler/simulated-source").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Technical Books Catalogue")));

        // 6. Crawler Import: Import a new book into the catalogue
        String uniqueIsbn = "978-9999" + (System.currentTimeMillis() % 1000000);
        CrawledBookDto newBookDto = new CrawledBookDto(
                "Modern Distributed Tracing and Observability",
                List.of("Observability Specialist"),
                uniqueIsbn,
                "DevOps & SRE",
                "OpenTelemetry, Jaeger, and distributed system observability.",
                4.8,
                "http://localhost:8080/simulated"
        );

        CrawlImportRequest importRequest = new CrawlImportRequest(List.of(newBookDto), 4, "Shelf Obs-1");

        mockMvc.perform(post("/api/crawler/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.importedCount").value(1));

        // 7. Verify Trie now includes the newly imported book terms
        List<String> updatedSuggestions = trieService.getSuggestions("Modern Distributed", 5);
        assertFalse(updatedSuggestions.isEmpty(), "Trie must immediately suggest newly imported book title");
        assertTrue(updatedSuggestions.contains("Modern Distributed Tracing and Observability"));

        // 8. Search for the newly imported book
        mockMvc.perform(get("/api/search")
                        .param("q", "Observability")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title")
                        .value("Modern Distributed Tracing and Observability"));

        // Cleanup test book
        bookRepository.findByIsbn(uniqueIsbn).ifPresent(b -> bookRepository.delete(b));
    }
}
