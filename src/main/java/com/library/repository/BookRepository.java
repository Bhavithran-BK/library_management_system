package com.library.repository;

import com.library.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends MongoRepository<Book, String> {

    Optional<Book> findByBookId(String bookId);

    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    boolean existsByBookId(String bookId);

    Page<Book> findByCategoryIgnoreCase(String category, Pageable pageable);

    @Query("{ '$or': [ { 'title': { $regex: ?0, $options: 'i' } }, { 'authors': { $regex: ?0, $options: 'i' } }, { 'isbn': { $regex: ?0, $options: 'i' } } ] }")
    Page<Book> searchKeyword(String keyword, Pageable pageable);

    @Query("{ 'category': { $regex: ?0, $options: 'i' }, '$or': [ { 'title': { $regex: ?1, $options: 'i' } }, { 'authors': { $regex: ?1, $options: 'i' } }, { 'isbn': { $regex: ?1, $options: 'i' } } ] }")
    Page<Book> searchCategoryAndKeyword(String category, String keyword, Pageable pageable);

    long countByAvailableCopiesGreaterThan(int count);
}
