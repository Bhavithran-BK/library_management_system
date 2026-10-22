package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.BookRequest;
import com.library.model.Book;
import com.library.service.BookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Book>>> listBooks(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Page<Book> books = bookService.listBooks(category, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(books));
    }

    @GetMapping("/{bookId}")
    public ResponseEntity<ApiResponse<Book>> getBook(@PathVariable String bookId) {
        Book book = bookService.getBookByBookId(bookId);
        return ResponseEntity.ok(ApiResponse.success(book));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Book>> createBook(@Valid @RequestBody BookRequest request) {
        Book book = bookService.addBook(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Book created successfully with ID: " + book.getBookId(), book));
    }

    @PutMapping("/{bookId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Book>> updateBook(@PathVariable String bookId,
                                                        @Valid @RequestBody BookRequest request) {
        Book book = bookService.updateBook(bookId, request);
        return ResponseEntity.ok(ApiResponse.success("Book updated successfully", book));
    }

    @DeleteMapping("/{bookId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable String bookId) {
        bookService.deleteBook(bookId);
        return ResponseEntity.ok(ApiResponse.success("Book deleted successfully", null));
    }
}
