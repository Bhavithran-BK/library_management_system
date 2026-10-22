package com.library.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result report for crawler records imported into library collection.
 */
public class CrawlImportResultDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private int importedCount;
    private int skippedDuplicateCount;
    private List<String> importedBookIds = new ArrayList<>();

    public CrawlImportResultDto() {
    }

    public CrawlImportResultDto(int importedCount, int skippedDuplicateCount, List<String> importedBookIds) {
        this.importedCount = importedCount;
        this.skippedDuplicateCount = skippedDuplicateCount;
        this.importedBookIds = importedBookIds != null ? importedBookIds : new ArrayList<>();
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public int getSkippedDuplicateCount() {
        return skippedDuplicateCount;
    }

    public void setSkippedDuplicateCount(int skippedDuplicateCount) {
        this.skippedDuplicateCount = skippedDuplicateCount;
    }

    public List<String> getImportedBookIds() {
        return importedBookIds;
    }

    public void setImportedBookIds(List<String> importedBookIds) {
        this.importedBookIds = importedBookIds;
    }
}
