package com.library.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Statistics and results from a controlled crawler execution.
 */
public class CrawlResultDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String seedUrl;
    private int pagesVisited;
    private int validRecordsExtracted;
    private int duplicateUrlsSkipped;
    private int invalidRecordsSkipped;
    private String status;
    private List<CrawledBookDto> extractedBooks = new ArrayList<>();

    public CrawlResultDto() {
    }

    public CrawlResultDto(String seedUrl, int pagesVisited, int validRecordsExtracted,
                          int duplicateUrlsSkipped, int invalidRecordsSkipped,
                          String status, List<CrawledBookDto> extractedBooks) {
        this.seedUrl = seedUrl;
        this.pagesVisited = pagesVisited;
        this.validRecordsExtracted = validRecordsExtracted;
        this.duplicateUrlsSkipped = duplicateUrlsSkipped;
        this.invalidRecordsSkipped = invalidRecordsSkipped;
        this.status = status;
        this.extractedBooks = extractedBooks != null ? extractedBooks : new ArrayList<>();
    }

    public String getSeedUrl() {
        return seedUrl;
    }

    public void setSeedUrl(String seedUrl) {
        this.seedUrl = seedUrl;
    }

    public int getPagesVisited() {
        return pagesVisited;
    }

    public void setPagesVisited(int pagesVisited) {
        this.pagesVisited = pagesVisited;
    }

    public int getValidRecordsExtracted() {
        return validRecordsExtracted;
    }

    public void setValidRecordsExtracted(int validRecordsExtracted) {
        this.validRecordsExtracted = validRecordsExtracted;
    }

    public int getDuplicateUrlsSkipped() {
        return duplicateUrlsSkipped;
    }

    public void setDuplicateUrlsSkipped(int duplicateUrlsSkipped) {
        this.duplicateUrlsSkipped = duplicateUrlsSkipped;
    }

    public int getInvalidRecordsSkipped() {
        return invalidRecordsSkipped;
    }

    public void setInvalidRecordsSkipped(int invalidRecordsSkipped) {
        this.invalidRecordsSkipped = invalidRecordsSkipped;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<CrawledBookDto> getExtractedBooks() {
        return extractedBooks;
    }

    public void setExtractedBooks(List<CrawledBookDto> extractedBooks) {
        this.extractedBooks = extractedBooks != null ? extractedBooks : new ArrayList<>();
    }
}
