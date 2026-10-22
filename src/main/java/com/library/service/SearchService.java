package com.library.service;

import com.library.dto.SearchResultDto;
import com.library.model.Book;
import com.library.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Production-grade search service for the library catalogue (Member 2).
 * Integrates MongoDB queries across multiple fields with Redis cache-aside acceleration.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final MongoTemplate mongoTemplate;
    private final BookRepository bookRepository;
    private final RedisCacheService cacheService;

    public SearchService(MongoTemplate mongoTemplate,
                         BookRepository bookRepository,
                         RedisCacheService cacheService) {
        this.mongoTemplate = mongoTemplate;
        this.bookRepository = bookRepository;
        this.cacheService = cacheService;
    }

    /**
     * Search books with multi-field matching, pagination, availability filtering, and cache-aside.
     *
     * @param query        text search query (matches title, authors, isbn, category)
     * @param page         0-indexed page number
     * @param size         page size
     * @param availability optional filter: true for in-stock, false for out-of-stock, null for all
     * @return paginated search results with metadata
     */
    public SearchResultDto<Book> search(String query, int page, int size, Boolean availability) {
        String cleanQuery = (query != null) ? query.trim() : "";
        int safePage = Math.max(0, page);
        int safeSize = (size <= 0) ? 10 : Math.min(size, 100);

        String cacheKey = cacheService.generateSearchKey(cleanQuery, safePage, safeSize, availability);

        // 1. Check Redis Cache
        SearchResultDto<Book> cached = cacheService.getSearchResult(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. Cache MISS: Query MongoDB
        long dbStart = System.currentTimeMillis();
        Query mongoQuery = buildSearchQuery(cleanQuery, availability);

        long totalElements = mongoTemplate.count(mongoQuery, Book.class);
        int totalPages = (totalElements == 0) ? 0 : (int) Math.ceil((double) totalElements / safeSize);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        mongoQuery.with(pageable);

        List<Book> content = mongoTemplate.find(mongoQuery, Book.class);
        long dbDuration = System.currentTimeMillis() - dbStart;

        log.debug("MongoDB search executed for '{}' in {} ms (found {} results)", cleanQuery, dbDuration, totalElements);

        SearchResultDto<Book> result = new SearchResultDto<>(
                content,
                safePage,
                safeSize,
                totalElements,
                totalPages,
                cleanQuery
        );

        // 3. Populate Redis Cache
        cacheService.putSearchResult(cacheKey, result);

        return result;
    }

    /**
     * Fetch a book by its business ID using Cache-Aside.
     */
    public Book getBookByIdCached(String bookId) {
        if (!StringUtils.hasText(bookId)) {
            return null;
        }

        // 1. Check Redis
        Book cached = cacheService.getBook(bookId);
        if (cached != null) {
            return cached;
        }

        // 2. Query MongoDB
        Book book = bookRepository.findByBookId(bookId.trim()).orElse(null);
        if (book != null) {
            cacheService.putBook(bookId, book);
        }

        return book;
    }

    private Query buildSearchQuery(String query, Boolean availability) {
        Query q = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (StringUtils.hasText(query)) {
            String escaped = Pattern.quote(query.trim());
            Criteria textMatch = new Criteria().orOperator(
                    Criteria.where("title").regex(escaped, "i"),
                    Criteria.where("authors").regex(escaped, "i"),
                    Criteria.where("isbn").regex(escaped, "i"),
                    Criteria.where("category").regex(escaped, "i")
            );
            criteriaList.add(textMatch);
        }

        if (availability != null) {
            if (availability) {
                criteriaList.add(Criteria.where("availableCopies").gt(0));
            } else {
                criteriaList.add(Criteria.where("availableCopies").is(0));
            }
        }

        if (!criteriaList.isEmpty()) {
            if (criteriaList.size() == 1) {
                q.addCriteria(criteriaList.get(0));
            } else {
                q.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
            }
        }

        return q;
    }
}
