package com.library.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TrieTest {

    private Trie trie;

    @BeforeEach
    public void setup() {
        trie = new Trie();
    }

    @Test
    @DisplayName("Should insert terms and find prefix matches")
    public void testInsertAndSearchPrefix() {
        trie.insert("Harry Potter");
        trie.insert("Harry Potter and the Chamber of Secrets");
        trie.insert("Haruki Murakami");
        trie.insert("Clean Code");

        List<String> results = trie.searchPrefix("Har", 5);
        assertEquals(3, results.size());
        assertTrue(results.contains("Harry Potter"));
        assertTrue(results.contains("Harry Potter and the Chamber of Secrets"));
        assertTrue(results.contains("Haruki Murakami"));
        assertFalse(results.contains("Clean Code"));
    }

    @Test
    @DisplayName("Prefix matching should be case-insensitive")
    public void testCaseInsensitivePrefix() {
        trie.insert("Designing Data-Intensive Applications");
        trie.insert("Designing Distributed Systems");

        List<String> lower = trie.searchPrefix("design", 5);
        assertEquals(2, lower.size());

        List<String> upper = trie.searchPrefix("DESIGN", 5);
        assertEquals(2, upper.size());

        List<String> mixed = trie.searchPrefix("DeSiGnInG", 5);
        assertEquals(2, mixed.size());
    }

    @Test
    @DisplayName("Should strictly respect top-K limit")
    public void testTopKLimit() {
        trie.insert("Clean Code");
        trie.insert("Clean Architecture");
        trie.insert("Clean Agile");
        trie.insert("Clean Coder");

        List<String> top2 = trie.searchPrefix("Clean", 2);
        assertEquals(2, top2.size());

        List<String> top3 = trie.searchPrefix("Clean", 3);
        assertEquals(3, top3.size());
    }

    @Test
    @DisplayName("Should prevent duplicate terms and maintain count")
    public void testDuplicatePrevention() {
        trie.insert("Database Internals");
        trie.insert("Database Internals");
        trie.insert("database internals");

        assertEquals(1, trie.getTermCount());
        List<String> results = trie.searchPrefix("Data", 10);
        assertEquals(1, results.size());
        assertEquals("Database Internals", results.get(0));
    }

    @Test
    @DisplayName("Should remove term accurately and preserve other terms")
    public void testDeletionBehavior() {
        trie.insert("System Design Interview");
        trie.insert("System Architecture");

        assertTrue(trie.remove("System Design Interview"));
        assertEquals(1, trie.getTermCount());

        List<String> results = trie.searchPrefix("System", 10);
        assertEquals(1, results.size());
        assertEquals("System Architecture", results.get(0));

        assertFalse(trie.remove("NonExistentTerm"));
    }

    @Test
    @DisplayName("Should return empty list for empty, null, or blank prefixes")
    public void testEmptyAndBlankPrefixBehavior() {
        trie.insert("Some Book");

        assertTrue(trie.searchPrefix(null, 5).isEmpty());
        assertTrue(trie.searchPrefix("", 5).isEmpty());
        assertTrue(trie.searchPrefix("   ", 5).isEmpty());
        assertTrue(trie.searchPrefix("Some", 0).isEmpty());
        assertTrue(trie.searchPrefix("Some", -1).isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for non-matching prefix")
    public void testNonExistentPrefix() {
        trie.insert("Algorithms");

        List<String> results = trie.searchPrefix("Zzz", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should support concurrent insertions and searches without race conditions")
    public void testConcurrentInsertAndSearch() throws InterruptedException {
        int threadCount = 8;
        int itemsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < itemsPerThread; i++) {
                    trie.insert("Title-" + threadId + "-" + i);
                    trie.searchPrefix("Title-" + threadId, 5);
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(threadCount * itemsPerThread, trie.getTermCount());
    }
}
