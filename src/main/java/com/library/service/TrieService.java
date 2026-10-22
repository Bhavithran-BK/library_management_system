package com.library.service;

import com.library.model.Book;
import com.library.repository.BookRepository;
import com.library.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Autocomplete service backed by the in-memory Trie data structure (Member 2).
 * Automatically synchronizes with book additions, updates, deletions, and crawler imports.
 */
@Service
public class TrieService {

    private static final Logger log = LoggerFactory.getLogger(TrieService.class);

    private final Trie trie = new Trie();
    private final BookRepository bookRepository;

    public TrieService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    /**
     * Build the Trie index on application startup from existing MongoDB books.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        rebuildIndex();
    }

    /**
     * Clear and rebuild the entire Trie index from MongoDB.
     */
    public synchronized void rebuildIndex() {
        long start = System.currentTimeMillis();
        trie.clear();
        List<Book> allBooks = bookRepository.findAll();
        for (Book book : allBooks) {
            indexBookTerms(book);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("TRIE INDEX REBUILT: indexed {} books with {} distinct terms in {} ms",
                allBooks.size(), trie.getTermCount(), elapsed);
    }

    /**
     * Search prefix suggestions.
     *
     * @param prefix the search prefix (e.g. "har")
     * @param limit  maximum suggestions to return (top-K)
     * @return list of suggestions
     */
    public List<String> getSuggestions(String prefix, int limit) {
        if (!StringUtils.hasText(prefix)) {
            return Collections.emptyList();
        }

        long start = System.currentTimeMillis();
        int safeLimit = (limit <= 0) ? 5 : Math.min(limit, 50);
        List<String> suggestions = trie.searchPrefix(prefix.trim(), safeLimit);
        long latencyMs = System.currentTimeMillis() - start;

        log.info("TRIE AUTOCOMPLETE: prefix='{}' suggestions={} latency={}ms",
                prefix.trim(), suggestions, latencyMs);
        return suggestions;
    }

    /**
     * Add terms from a newly created or imported book into the Trie.
     */
    public void addBook(Book book) {
        if (book == null) {
            return;
        }
        indexBookTerms(book);
        log.debug("TRIE: added terms for book '{}'", book.getTitle());
    }

    /**
     * Update terms when a book is modified.
     */
    public void updateBook(Book oldBook, Book newBook) {
        if (oldBook != null) {
            removeBookTerms(oldBook);
        }
        if (newBook != null) {
            indexBookTerms(newBook);
        }
        log.debug("TRIE: updated terms for book '{}'", newBook != null ? newBook.getTitle() : "null");
    }

    /**
     * Remove terms when a book is deleted.
     */
    public void removeBook(Book book) {
        if (book == null) {
            return;
        }
        removeBookTerms(book);
        log.debug("TRIE: removed terms for book '{}'", book.getTitle());
    }

    public int getIndexedTermCount() {
        return trie.getTermCount();
    }

    private void indexBookTerms(Book book) {
        if (book == null) {
            return;
        }

        if (StringUtils.hasText(book.getTitle())) {
            trie.insert(book.getTitle().trim());
        }

        if (book.getAuthors() != null) {
            for (String author : book.getAuthors()) {
                if (StringUtils.hasText(author)) {
                    trie.insert(author.trim());
                }
            }
        }

        if (StringUtils.hasText(book.getCategory())) {
            trie.insert(book.getCategory().trim());
        }
    }

    private void removeBookTerms(Book book) {
        if (book == null) {
            return;
        }

        if (StringUtils.hasText(book.getTitle())) {
            trie.remove(book.getTitle().trim());
        }

        if (book.getAuthors() != null) {
            for (String author : book.getAuthors()) {
                if (StringUtils.hasText(author)) {
                    trie.remove(author.trim());
                }
            }
        }

        if (StringUtils.hasText(book.getCategory())) {
            trie.remove(book.getCategory().trim());
        }
    }
}
