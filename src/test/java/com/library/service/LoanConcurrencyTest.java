package com.library.service;

import com.library.dto.BorrowRequest;
import com.library.idgenerator.IdGeneratorService;
import com.library.model.Book;
import com.library.model.Member;
import com.library.model.Role;
import com.library.repository.BookRepository;
import com.library.repository.FineTransactionRepository;
import com.library.repository.LoanRepository;
import com.library.repository.MemberRepository;
import com.library.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import com.library.model.Loan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class LoanConcurrencyTest {

    @Autowired
    private LoanService loanService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private FineTransactionRepository fineRepository;

    @Autowired
    private IdGeneratorService idGeneratorService;

    @Autowired
    private MongoTemplate mongoTemplate;

    private String testBookId;
    private final List<String> testMemberIds = new ArrayList<>();

    @BeforeEach
    public void setup() {
        testBookId = idGeneratorService.generateBookId();
        String testIsbn = "978-" + (Math.abs(System.nanoTime()) % 10000000000L);
        testMemberIds.clear();

        Book book = new Book(
                testBookId,
                "Concurrency Safe Architecture",
                List.of("Concurrency Expert"),
                testIsbn,
                "Computer Science",
                "Testing atomic decrement under high concurrency",
                1, // Total copies: 1
                1, // Available copies: exactly 1
                "Shelf Test",
                "TEST"
        );
        bookRepository.save(book);

        // Create 10 distinct members to test concurrent borrowing
        for (int i = 0; i < 10; i++) {
            String mId = idGeneratorService.generateMemberId();
            testMemberIds.add(mId);
            Member member = new Member(mId, "Test Member " + i, mId.toLowerCase() + "@test.com", "hash", Role.MEMBER, null);
            memberRepository.save(member);
        }
    }

    @AfterEach
    public void cleanup() {
        if (testBookId != null) {
            mongoTemplate.remove(Query.query(Criteria.where("bookId").is(testBookId)), Book.class);
        }
        for (String mId : testMemberIds) {
            mongoTemplate.remove(Query.query(Criteria.where("memberId").is(mId)), Member.class);
            mongoTemplate.remove(Query.query(Criteria.where("memberId").is(mId)), Loan.class);
        }
        testMemberIds.clear();
    }

    @Test
    @DisplayName("10 concurrent threads attempting to borrow the final 1 copy must result in exactly 1 success and 9 failures")
    public void testConcurrentBorrowingOfFinalCopy() throws InterruptedException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final String memberId = testMemberIds.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    loanService.borrowBook(new BorrowRequest(memberId, testBookId));
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    System.err.println("Thread borrow error: " + ex.getClass().getName() + " - " + ex.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire simultaneous requests
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Book finalBook = bookRepository.findByBookId(testBookId).orElseThrow();

        assertEquals(1, successCount.get(), "Exactly 1 concurrent request must successfully borrow the final copy");
        assertEquals(9, failureCount.get(), "All other 9 concurrent requests must fail with out-of-stock / ConflictException");
        assertEquals(0, finalBook.getAvailableCopies(), "Available copies must be exactly 0, never negative!");
    }
}
