package com.library.crawler;

import com.library.dto.CrawledBookDto;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * HTML parser for catalogue pages using Jsoup.
 * Extracts structured book metadata and navigates pagination / catalogue links.
 */
@Component
public class CatalogueParser {

    private static final Logger log = LoggerFactory.getLogger(CatalogueParser.class);

    public static class ParseResult {
        private final List<CrawledBookDto> validBooks = new ArrayList<>();
        private int invalidRecordCount = 0;
        private final Set<String> discoveredLinks = new HashSet<>();

        public List<CrawledBookDto> getValidBooks() {
            return validBooks;
        }

        public int getInvalidRecordCount() {
            return invalidRecordCount;
        }

        public Set<String> getDiscoveredLinks() {
            return discoveredLinks;
        }
    }

    /**
     * Parse HTML document for book metadata and catalogue links.
     */
    public ParseResult parse(Document doc, String pageUrl) {
        ParseResult result = new ParseResult();
        if (doc == null) {
            return result;
        }

        // 1. Extract book items matching common catalogue structures (.book-card, .book-item, or [data-isbn])
        Elements bookElements = doc.select(".book-card, .book-item, .catalogue-entry, [data-book]");
        if (bookElements.isEmpty()) {
            // Fallback: look for article or div elements with book info
            bookElements = doc.select("article, .book");
        }

        for (Element el : bookElements) {
            CrawledBookDto book = extractBook(el, pageUrl);
            if (isValidBook(book)) {
                result.validBooks.add(book);
            } else {
                result.invalidRecordCount++;
            }
        }

        // 2. Discover internal hyperlinks
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String absUrl = link.attr("abs:href");
            if (StringUtils.hasText(absUrl)) {
                result.discoveredLinks.add(absUrl.trim());
            }
        }

        return result;
    }

    private CrawledBookDto extractBook(Element el, String pageUrl) {
        String title = firstTextOrAttr(el, ".book-title, .title, h2, h3", "data-title");
        String authorsRaw = firstTextOrAttr(el, ".book-authors, .author, .byline", "data-author");
        String isbn = firstTextOrAttr(el, ".book-isbn, .isbn, span.isbn", "data-isbn");
        String category = firstTextOrAttr(el, ".book-category, .category, .genre", "data-category");
        String description = firstTextOrAttr(el, ".book-description, .description, p.desc", "data-description");
        String ratingRaw = firstTextOrAttr(el, ".book-rating, .rating, span.rating", "data-rating");

        List<String> authors = parseAuthors(authorsRaw);
        Double rating = parseRating(ratingRaw);
        String cleanIsbn = normalizeIsbn(isbn);

        return new CrawledBookDto(
                title != null ? title.trim() : null,
                authors,
                cleanIsbn,
                StringUtils.hasText(category) ? category.trim() : "General",
                StringUtils.hasText(description) ? description.trim() : "Imported via crawler.",
                rating,
                pageUrl
        );
    }

    private String firstTextOrAttr(Element parent, String cssQuery, String attrName) {
        if (parent.hasAttr(attrName) && StringUtils.hasText(parent.attr(attrName))) {
            return parent.attr(attrName).trim();
        }
        Element target = parent.selectFirst(cssQuery);
        if (target != null) {
            if (target.hasAttr(attrName) && StringUtils.hasText(target.attr(attrName))) {
                return target.attr(attrName).trim();
            }
            return target.text().trim();
        }
        return null;
    }

    private List<String> parseAuthors(String authorsRaw) {
        if (!StringUtils.hasText(authorsRaw)) {
            return List.of("Unknown Author");
        }
        String cleaned = authorsRaw.replaceAll("^(?i)by\\s+", "").trim();
        String[] parts = cleaned.split("[,;&|]|\\band\\b");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String clean = p.trim();
            if (!clean.isEmpty()) {
                list.add(clean);
            }
        }
        return list.isEmpty() ? List.of("Unknown Author") : list;
    }

    private Double parseRating(String ratingRaw) {
        if (!StringUtils.hasText(ratingRaw)) {
            return null;
        }
        try {
            String digits = ratingRaw.replaceAll("[^0-9.]", "").trim();
            if (!digits.isEmpty()) {
                double val = Double.parseDouble(digits);
                return Math.max(0.0, Math.min(5.0, val));
            }
        } catch (NumberFormatException e) {
            log.debug("Unparseable rating '{}'", ratingRaw);
        }
        return null;
    }

    private String normalizeIsbn(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        // Remove label prefixes like "ISBN:", "ISBN-13:", etc.
        String clean = raw.replaceAll("^(?i)isbn(-1[03])?:?\\s*", "").trim();
        return clean.replaceAll("[^0-9X-]", "").trim();
    }

    private boolean isValidBook(CrawledBookDto book) {
        if (book == null) {
            return false;
        }
        if (!StringUtils.hasText(book.getTitle()) || book.getTitle().length() < 2) {
            return false;
        }
        if (!StringUtils.hasText(book.getIsbn()) || book.getIsbn().length() < 8) {
            return false;
        }
        return true;
    }
}
