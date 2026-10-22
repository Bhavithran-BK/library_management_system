package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.CacheStatsDto;
import com.library.dto.SearchResultDto;
import com.library.dto.SuggestResponseDto;
import com.library.model.Book;
import com.library.service.RedisCacheService;
import com.library.service.SearchService;
import com.library.service.TrieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Primary Search and Autocomplete Controller (Member 2).
 * Provides multi-field book search, Trie autocomplete suggestions, and Redis cache telemetry.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final TrieService trieService;
    private final RedisCacheService cacheService;

    public SearchController(SearchService searchService,
                            TrieService trieService,
                            RedisCacheService cacheService) {
        this.searchService = searchService;
        this.trieService = trieService;
        this.cacheService = cacheService;
    }

    /**
     * Search books with multi-field matching, pagination, and availability filtering.
     * GET /api/search?q={query}&page={page}&size={size}&availability={availability}
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResultDto<Book>>> search(
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "availability", required = false) Boolean availability) {

        SearchResultDto<Book> results = searchService.search(query, page, size, availability);
        return ResponseEntity.ok(ApiResponse.success("Search results retrieved successfully", results));
    }

    /**
     * Autocomplete suggestions using in-memory Trie data structure.
     * GET /api/search/suggest?prefix={prefix}&limit={limit}
     */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<SuggestResponseDto>> suggest(
            @RequestParam(name = "prefix", required = false, defaultValue = "") String prefix,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {

        List<String> suggestions = trieService.getSuggestions(prefix, limit);
        SuggestResponseDto response = new SuggestResponseDto(prefix, suggestions);
        return ResponseEntity.ok(ApiResponse.success("Autocomplete suggestions", response));
    }

    /**
     * Redis cache hit/miss statistics and health diagnostics.
     * GET /api/search/cache/stats
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<ApiResponse<CacheStatsDto>> getCacheStats() {
        CacheStatsDto stats = cacheService.getStats();
        return ResponseEntity.ok(ApiResponse.success("Redis cache performance metrics", stats));
    }
}
