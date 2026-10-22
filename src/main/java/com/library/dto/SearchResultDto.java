package com.library.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Standard pagination and search metadata DTO for Book Search API.
 */
public class SearchResultDto<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private String query;

    public SearchResultDto() {
        this.content = Collections.emptyList();
    }

    public SearchResultDto(List<T> content, int page, int size, long totalElements, int totalPages, String query) {
        this.content = content != null ? content : Collections.emptyList();
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.query = query;
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
