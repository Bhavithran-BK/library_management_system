package com.library.service;

import com.library.dto.BorrowRequest;
import com.library.exception.BadRequestException;
import com.library.exception.ConflictException;
import com.library.exception.ResourceNotFoundException;
import com.library.idgenerator.IdGeneratorService;
import com.library.model.*;
import com.library.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final ReservationRepository reservationRepository;
    private final FineTransactionRepository fineTransactionRepository;
    private final IdGeneratorService idGeneratorService;
    private final MongoTemplate mongoTemplate;
    private final RedisCacheService cacheService;

    @Value("${app.library.fine.per-day-rate:5.0}")
    private double finePerDayRate;

    @Value("${app.library.loan.default-duration-days:14}")
    private int defaultDurationDays;

    public LoanService(LoanRepository loanRepository,
                       BookRepository bookRepository,
                       MemberRepository memberRepository,
                       ReservationRepository reservationRepository,
                       FineTransactionRepository fineTransactionRepository,
                       IdGeneratorService idGeneratorService,
                       MongoTemplate mongoTemplate,
                       RedisCacheService cacheService) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.reservationRepository = reservationRepository;
        this.fineTransactionRepository = fineTransactionRepository;
        this.idGeneratorService = idGeneratorService;
        this.mongoTemplate = mongoTemplate;
        this.cacheService = cacheService;
    }

    /**
     * Borrow a book with atomic concurrency safety.
     * Guaranteed that two concurrent requests cannot borrow the final copy twice.
     */
    public Loan borrowBook(BorrowRequest request) {
        String memberId = request.getMemberId().trim();
        String bookId = request.getBookId().trim();

        if (!memberRepository.existsByMemberId(memberId)) {
            throw new ResourceNotFoundException("Member not found with ID: " + memberId);
        }

        Book book = bookRepository.findByBookId(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with ID: " + bookId));

        // Prevent duplicate active loan for the same book by the same member
        if (loanRepository.existsByMemberIdAndBookIdAndStatus(memberId, bookId, LoanStatus.ACTIVE)) {
            throw new ConflictException("Member already has an active loan for this book");
        }

        // Atomic decrement in MongoDB: updates document IF AND ONLY IF availableCopies > 0
        Query query = new Query(Criteria.where("bookId").is(bookId).and("availableCopies").gt(0));
        Update update = new Update().inc("availableCopies", -1).set("updatedAt", Instant.now());
        Book updated = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Book.class);

        if (updated == null) {
            throw new ConflictException("No copies available for book '" + book.getTitle() + "'. You may place a reservation instead.");
        }

        // Fulfill any pending reservation the member had for this book
        List<Reservation> userReservations = reservationRepository.findByBookIdAndStatusOrderByQueuePositionAsc(bookId, ReservationStatus.PENDING);
        for (Reservation res : userReservations) {
            if (res.getMemberId().equals(memberId)) {
                res.setStatus(ReservationStatus.FULFILLED);
                reservationRepository.save(res);
                reorderQueue(bookId);
                break;
            }
        }

        // Create Loan record with collision-resistant ID
        Instant now = Instant.now();
        Instant dueAt = now.plus(Duration.ofDays(defaultDurationDays));

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            String loanId = idGeneratorService.generateLoanId();
            Loan loan = new Loan(loanId, memberId, bookId, now, dueAt);
            try {
                Loan saved = loanRepository.save(loan);
                cacheService.evictBookAndSearches(bookId);
                return saved;
            } catch (DuplicateKeyException ex) {
                if (i == maxRetries - 1) {
                    // Compensate inventory if loan creation fails
                    mongoTemplate.updateFirst(new Query(Criteria.where("bookId").is(bookId)), new Update().inc("availableCopies", 1), Book.class);
                    throw new ConflictException("Failed to generate unique loan ID: " + ex.getMessage());
                }
            }
        }

        throw new IllegalStateException("Failed to create loan");
    }

    /**
     * Return a book, calculate overdue fine if applicable, and increment inventory atomically.
     */
    public Loan returnBook(String loanId) {
        Loan loan = loanRepository.findByLoanId(loanId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));

        if (loan.getStatus() == LoanStatus.RETURNED) {
            throw new BadRequestException("Book has already been returned for loan ID: " + loanId);
        }

        Instant now = Instant.now();
        loan.setReturnedAt(now);
        loan.setStatus(LoanStatus.RETURNED);
        Loan savedLoan = loanRepository.save(loan);

        // Atomic inventory increment (ensuring availableCopies does not exceed totalCopies)
        Book book = bookRepository.findByBookId(loan.getBookId()).orElse(null);
        if (book != null) {
            Query query = new Query(Criteria.where("bookId").is(loan.getBookId()).and("availableCopies").lt(book.getTotalCopies()));
            Update update = new Update().inc("availableCopies", 1).set("updatedAt", now);
            mongoTemplate.findAndModify(query, update, Book.class);
            cacheService.evictBookAndSearches(loan.getBookId());
        }

        // Calculate overdue fine if applicable
        if (now.isAfter(loan.getDueAt())) {
            long overdueDays = ChronoUnit.DAYS.between(loan.getDueAt(), now);
            if (overdueDays <= 0) {
                overdueDays = 1; // At least 1 day overdue if returned past due timestamp
            }
            BigDecimal fineAmount = BigDecimal.valueOf(overdueDays * finePerDayRate);

            String txnId = idGeneratorService.generateTransactionId();
            FineTransaction fine = new FineTransaction(txnId, loan.getLoanId(), loan.getMemberId(), fineAmount);
            fineTransactionRepository.save(fine);
        }

        return savedLoan;
    }

    public List<Loan> getMemberLoans(String memberId) {
        return loanRepository.findByMemberIdOrderByBorrowedAtDesc(memberId.trim());
    }

    public List<Loan> getAllActiveLoans() {
        return loanRepository.findByStatusOrderByBorrowedAtDesc(LoanStatus.ACTIVE);
    }

    public List<Loan> getAllLoans() {
        return loanRepository.findAll();
    }

    private void reorderQueue(String bookId) {
        List<Reservation> pending = reservationRepository.findByBookIdAndStatusOrderByQueuePositionAsc(bookId, ReservationStatus.PENDING);
        int pos = 1;
        for (Reservation r : pending) {
            r.setQueuePosition(pos++);
            reservationRepository.save(r);
        }
    }
}
