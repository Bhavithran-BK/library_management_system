package com.library.service;

import com.library.dto.CacheStatsDto;
import com.library.dto.SearchResultDto;
import com.library.model.Book;
import com.library.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SearchAndCacheTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private MongoTemplate mongoTemplate;
    private BookRepository bookRepository;
    private RedisCacheService cacheService;
    private SearchService searchService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        mongoTemplate = mock(MongoTemplate.class);
        bookRepository = mock(BookRepository.class);

        cacheService = new RedisCacheService(redisTemplate);
        searchService = new SearchService(mongoTemplate, bookRepository, cacheService);
    }

    @Test
    @DisplayName("Cache miss queries MongoDB and populates Redis")
    public void testSearchCacheMissQueriesMongoAndCaches() {
        // Given Redis has no cached entry
        when(valueOperations.get(anyString())).thenReturn(null);

        Book b1 = new Book("BK-01", "Clean Code", List.of("Robert C. Martin"), "978-0132350884",
                "Software Engineering", "Desc", 5, 5, "Shelf A", "INTERNAL");
        when(mongoTemplate.count(any(Query.class), eq(Book.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Book.class))).thenReturn(List.of(b1));

        // When
        SearchResultDto<Book> result = searchService.search("clean", 0, 10, true);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Clean Code", result.getContent().get(0).getTitle());
        assertEquals(1, result.getTotalElements());

        // Verify MongoDB was queried
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(Book.class));
        // Verify Redis was populated with TTL
        verify(valueOperations, times(1)).set(contains("search:clean:0:10:true"), any(SearchResultDto.class), any(Duration.class));

        // Check metrics: 1 miss, 0 hits
        CacheStatsDto stats = cacheService.getStats();
        assertEquals(1, stats.getSearchCacheMisses());
        assertEquals(0, stats.getSearchCacheHits());
    }

    @Test
    @DisplayName("Cache hit returns cached search result directly without hitting MongoDB")
    public void testSearchCacheHitReturnsFromRedis() {
        Book b1 = new Book("BK-01", "Clean Code", List.of("Robert C. Martin"), "978-0132350884",
                "Software Engineering", "Desc", 5, 5, "Shelf A", "INTERNAL");
        SearchResultDto<Book> cachedResult = new SearchResultDto<>(List.of(b1), 0, 10, 1L, 1, "clean");

        // Given Redis already has cached entry
        when(valueOperations.get(contains("search:clean:0:10:true"))).thenReturn(cachedResult);

        // When
        SearchResultDto<Book> result = searchService.search("clean", 0, 10, true);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Clean Code", result.getContent().get(0).getTitle());

        // Verify MongoDB was NEVER queried!
        verify(mongoTemplate, never()).find(any(Query.class), eq(Book.class));
        verify(mongoTemplate, never()).count(any(Query.class), eq(Book.class));

        // Check metrics: 1 hit, 0 misses
        CacheStatsDto stats = cacheService.getStats();
        assertEquals(1, stats.getSearchCacheHits());
        assertEquals(0, stats.getSearchCacheMisses());
    }

    @Test
    @DisplayName("Deterministic search cache key generation handles whitespace and case")
    public void testDeterministicCacheKeyGeneration() {
        String key1 = cacheService.generateSearchKey("  Harry Potter  ", 0, 10, true);
        String key2 = cacheService.generateSearchKey("harry potter", 0, 10, true);
        assertEquals(key1, key2);
        assertEquals("search:harry potter:0:10:true", key1);

        String keyAll = cacheService.generateSearchKey(null, 0, 10, null);
        assertEquals("search:*:0:10:all", keyAll);
    }

    @Test
    @DisplayName("Book detail cache-aside flow for single book")
    public void testBookDetailCacheAside() {
        when(valueOperations.get("book:BK-100")).thenReturn(null);
        Book b = new Book("BK-100", "Title", List.of("Author"), "978-0000000000", "Cat", "Desc", 3, 3, "Shelf", "INTERNAL");
        when(bookRepository.findByBookId("BK-100")).thenReturn(Optional.of(b));

        // First call: Miss -> DB
        Book first = searchService.getBookByIdCached("BK-100");
        assertNotNull(first);
        verify(bookRepository, times(1)).findByBookId("BK-100");
        verify(valueOperations, times(1)).set(eq("book:BK-100"), eq(b), any(Duration.class));

        // Second call: Hit -> Redis
        when(valueOperations.get("book:BK-100")).thenReturn(b);
        Book second = searchService.getBookByIdCached("BK-100");
        assertNotNull(second);
        verify(bookRepository, times(1)).findByBookId("BK-100"); // still only 1 invocation!
    }

    @Test
    @DisplayName("Evicting single book deletes key from Redis")
    public void testEvictBook() {
        when(redisTemplate.delete("book:BK-100")).thenReturn(true);
        cacheService.evictBook("BK-100");
        verify(redisTemplate, times(1)).delete("book:BK-100");
    }
}
