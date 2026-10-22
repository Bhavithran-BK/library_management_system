package com.library.dto;

import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Request payload to import selected crawler records into library inventory.
 */
public class CrawlImportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "Books list to import must not be empty")
    private List<CrawledBookDto> books = new ArrayList<>();

    private Integer defaultCopies = 3;

    private String location = "Main Catalogue / Member 2 Crawler Import";

    public CrawlImportRequest() {
    }

    public CrawlImportRequest(List<CrawledBookDto> books, Integer defaultCopies, String location) {
        this.books = books != null ? books : new ArrayList<>();
        this.defaultCopies = defaultCopies != null ? defaultCopies : 3;
        this.location = location != null ? location : "Main Catalogue / Member 2 Crawler Import";
    }

    public List<CrawledBookDto> getBooks() {
        return books;
    }

    public void setBooks(List<CrawledBookDto> books) {
        this.books = books;
    }

    public Integer getDefaultCopies() {
        return defaultCopies;
    }

    public void setDefaultCopies(Integer defaultCopies) {
        this.defaultCopies = defaultCopies;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
