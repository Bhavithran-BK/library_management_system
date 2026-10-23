package com.library.repository;

import com.library.model.ShortLink;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB Repository for ShortLink entities (Member 4).
 */
@Repository
public interface ShortLinkRepository extends MongoRepository<ShortLink, String> {

    Optional<ShortLink> findByCode(String code);

    boolean existsByCode(String code);

    Optional<ShortLink> findByBookId(String bookId);

    Optional<ShortLink> findByOriginalUrl(String originalUrl);

    List<ShortLink> findAllByOrderByCreatedAtDesc();
}
