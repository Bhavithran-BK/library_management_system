package com.library.trie;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Node representation for the Trie data structure used in autocomplete.
 */
public class TrieNode {

    private final Map<Character, TrieNode> children = new HashMap<>();
    private boolean endOfWord;
    private final Set<String> canonicalTerms = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public TrieNode getChild(char ch) {
        return children.get(ch);
    }

    public TrieNode computeChildIfAbsent(char ch) {
        return children.computeIfAbsent(ch, k -> new TrieNode());
    }

    public boolean isEndOfWord() {
        return endOfWord;
    }

    public void setEndOfWord(boolean endOfWord) {
        this.endOfWord = endOfWord;
    }

    public Set<String> getCanonicalTerms() {
        return Collections.unmodifiableSet(canonicalTerms);
    }

    public boolean addCanonicalTerm(String term) {
        if (term != null && !term.isBlank()) {
            boolean added = this.canonicalTerms.add(term.trim());
            if (added) {
                this.endOfWord = true;
            }
            return added;
        }
        return false;
    }

    public boolean removeCanonicalTerm(String term) {
        if (term == null) {
            return false;
        }
        boolean removed = this.canonicalTerms.remove(term.trim());
        if (this.canonicalTerms.isEmpty()) {
            this.endOfWord = false;
        }
        return removed;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }
}
