package com.library.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracted book metadata from crawler pages.
 */
public class CrawledBookDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;
    private List<String> authors = new ArrayList<>();
    private String isbn;
    private String category;
    private String description;
    private Double rating;
    private String sourceUrl;

    public CrawledBookDto() {
    }

    public CrawledBookDto(String title, List<String> authors, String isbn, String category,
                          String description, Double rating, String sourceUrl) {
        this.title = title;
        this.authors = authors != null ? authors : new ArrayList<>();
        this.isbn = isbn;
        this.category = category;
        this.description = description;
        this.rating = rating;
        this.sourceUrl = sourceUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors != null ? authors : new ArrayList<>();
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
}
