package com.library.trie;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe Trie (Prefix Tree) implementation for fast autocomplete suggestion retrieval.
 * Supports case-insensitive matching while preserving original canonical terms.
 */
public class Trie {

    private final TrieNode root = new TrieNode();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private int termCount = 0;

    /**
     * Insert a phrase or title into the Trie.
     *
     * @param phrase original canonical phrase (e.g. "Harry Potter")
     */
    public void insert(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return;
        }

        String canonical = phrase.trim();
        String normalized = normalize(canonical);

        rwLock.writeLock().lock();
        try {
            TrieNode current = root;
            for (char ch : normalized.toCharArray()) {
                current = current.computeChildIfAbsent(ch);
            }
            if (current.addCanonicalTerm(canonical)) {
                termCount++;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Search for autocomplete suggestions matching the given prefix.
     *
     * @param prefix the prefix to search for (e.g. "har")
     * @param limit  maximum number of suggestions to return
     * @return list of unique matching canonical terms up to limit
     */
    public List<String> searchPrefix(String prefix, int limit) {
        if (prefix == null || prefix.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }

        String normalizedPrefix = normalize(prefix.trim());
        rwLock.readLock().lock();
        try {
            TrieNode current = root;
            for (char ch : normalizedPrefix.toCharArray()) {
                current = current.getChild(ch);
                if (current == null) {
                    return Collections.emptyList();
                }
            }

            Set<String> results = new LinkedHashSet<>();
            collectSuggestions(current, results, limit);
            return new ArrayList<>(results);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Remove a term from the Trie.
     *
     * @param phrase term to remove
     * @return true if removed, false otherwise
     */
    public boolean remove(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return false;
        }

        String canonical = phrase.trim();
        String normalized = normalize(canonical);

        rwLock.writeLock().lock();
        try {
            return deleteHelper(root, normalized, canonical, 0);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private boolean deleteHelper(TrieNode current, String normalized, String canonical, int index) {
        if (current == null) {
            return false;
        }

        if (index == normalized.length()) {
            if (!current.getCanonicalTerms().contains(canonical)) {
                return false;
            }
            boolean removed = current.removeCanonicalTerm(canonical);
            if (removed) {
                termCount--;
            }
            return removed;
        }

        char ch = normalized.charAt(index);
        TrieNode child = current.getChild(ch);
        if (child == null) {
            return false;
        }

        boolean shouldDeleteChild = deleteHelper(child, normalized, canonical, index + 1)
                && !child.isEndOfWord()
                && !child.hasChildren();

        if (shouldDeleteChild) {
            current.getChildren().remove(ch);
        }

        return true;
    }

    /**
     * Reset and clear all Trie data.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            root.getChildren().clear();
            termCount = 0;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public int getTermCount() {
        rwLock.readLock().lock();
        try {
            return termCount;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private void collectSuggestions(TrieNode node, Set<String> results, int limit) {
        if (node == null || results.size() >= limit) {
            return;
        }

        if (node.isEndOfWord()) {
            for (String term : node.getCanonicalTerms()) {
                results.add(term);
                if (results.size() >= limit) {
                    return;
                }
            }
        }

        // Traverse children in deterministic sorted order
        List<Character> keys = new ArrayList<>(node.getChildren().keySet());
        Collections.sort(keys);
        for (char ch : keys) {
            collectSuggestions(node.getChild(ch), results, limit);
            if (results.size() >= limit) {
                return;
            }
        }
    }

    private String normalize(String str) {
        return str.toLowerCase(Locale.ROOT);
    }
}
