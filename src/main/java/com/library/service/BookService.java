package com.library.service;

import com.library.dto.BookRequest;
import com.library.exception.BadRequestException;
import com.library.exception.ConflictException;
import com.library.exception.ResourceNotFoundException;
import com.library.idgenerator.IdGeneratorService;
import com.library.model.Book;
import com.library.model.LoanStatus;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final IdGeneratorService idGeneratorService;
    private final TrieService trieService;
    private final RedisCacheService cacheService;

    public BookService(BookRepository bookRepository,
                       LoanRepository loanRepository,
                       IdGeneratorService idGeneratorService,
                       TrieService trieService,
                       RedisCacheService cacheService) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.idGeneratorService = idGeneratorService;
        this.trieService = trieService;
        this.cacheService = cacheService;
    }

    public Book addBook(BookRequest request) {
        String cleanIsbn = request.getIsbn().trim();
        if (bookRepository.existsByIsbn(cleanIsbn)) {
            throw new ConflictException("A book with ISBN '" + cleanIsbn + "' already exists");
        }

        int total = request.getTotalCopies();
        int available = request.getAvailableCopies() != null ? request.getAvailableCopies() : total;
        if (available > total || available < 0) {
            throw new BadRequestException("Available copies must be between 0 and total copies (" + total + ")");
        }

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            String bookId = idGeneratorService.generateBookId();
            Book book = new Book(
                    bookId,
                    request.getTitle().trim(),
                    request.getAuthors(),
                    cleanIsbn,
                    request.getCategory().trim(),
                    request.getDescription(),
                    total,
                    available,
                    request.getLocation(),
                    request.getMetadataSource()
            );

            try {
                Book saved = bookRepository.save(book);
                trieService.addBook(saved);
                cacheService.evictAllSearchResults();
                return saved;
            } catch (DuplicateKeyException ex) {
                if (i == maxRetries - 1) {
                    throw new ConflictException("Failed to persist unique book ID after retries: " + ex.getMessage());
                }
            }
        }
        throw new IllegalStateException("Failed to add book");
    }

    public Book updateBook(String bookId, BookRequest request) {
        Book existing = getBookByBookId(bookId);

        String cleanIsbn = request.getIsbn().trim();
        if (!cleanIsbn.equalsIgnoreCase(existing.getIsbn()) && bookRepository.existsByIsbn(cleanIsbn)) {
            throw new ConflictException("Another book with ISBN '" + cleanIsbn + "' already exists");
        }

        int total = request.getTotalCopies();
        int diff = total - existing.getTotalCopies();
        int newAvailable = existing.getAvailableCopies() + diff;
        if (newAvailable < 0) {
            throw new BadRequestException("Cannot decrease total copies below currently borrowed copies");
        }

        existing.setTitle(request.getTitle().trim());
        existing.setAuthors(request.getAuthors());
        existing.setIsbn(cleanIsbn);
        existing.setCategory(request.getCategory().trim());
        existing.setDescription(request.getDescription());
        existing.setTotalCopies(total);
        existing.setAvailableCopies(newAvailable);
        existing.setLocation(request.getLocation());
        existing.setUpdatedAt(Instant.now());

        Book saved = bookRepository.save(existing);
        trieService.updateBook(existing, saved);
        cacheService.evictBookAndSearches(bookId);
        return saved;
    }

    public void deleteBook(String bookId) {
        Book existing = getBookByBookId(bookId);
        boolean hasActiveLoans = !loanRepository.findByBookIdAndStatus(bookId, LoanStatus.ACTIVE).isEmpty();
        if (hasActiveLoans) {
            throw new BadRequestException("Cannot delete book with active loans. Process all returns first.");
        }
        bookRepository.delete(existing);
        trieService.removeBook(existing);
        cacheService.evictBookAndSearches(bookId);
    }

    public Book getBookByBookId(String bookId) {
        Book cached = cacheService.getBook(bookId);
        if (cached != null) {
            return cached;
        }
        Book book = bookRepository.findByBookId(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + bookId));
        cacheService.putBook(bookId, book);
        return book;
    }

    public Page<Book> listBooks(String category, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));

        boolean hasCat = StringUtils.hasText(category) && !category.equalsIgnoreCase("ALL");
        boolean hasQuery = StringUtils.hasText(search);

        if (hasCat && hasQuery) {
            return bookRepository.searchCategoryAndKeyword(category.trim(), search.trim(), pageable);
        } else if (hasCat) {
            return bookRepository.findByCategoryIgnoreCase(category.trim(), pageable);
        } else if (hasQuery) {
            return bookRepository.searchKeyword(search.trim(), pageable);
        } else {
            return bookRepository.findAll(pageable);
        }
    }
}
