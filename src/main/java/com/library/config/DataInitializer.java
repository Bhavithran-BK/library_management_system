package com.library.config;

import com.library.idgenerator.IdGeneratorService;
import com.library.model.*;
import com.library.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final MemberRepository memberRepository;
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final FineTransactionRepository fineRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdGeneratorService idGeneratorService;
    private final ShortLinkRepository shortLinkRepository;
    private final com.library.service.ShortLinkService shortLinkService;

    public DataInitializer(MemberRepository memberRepository,
                           BookRepository bookRepository,
                           LoanRepository loanRepository,
                           ReservationRepository reservationRepository,
                           FineTransactionRepository fineRepository,
                           PasswordEncoder passwordEncoder,
                           IdGeneratorService idGeneratorService,
                           ShortLinkRepository shortLinkRepository,
                           com.library.service.ShortLinkService shortLinkService) {
        this.memberRepository = memberRepository;
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.reservationRepository = reservationRepository;
        this.fineRepository = fineRepository;
        this.passwordEncoder = passwordEncoder;
        this.idGeneratorService = idGeneratorService;
        this.shortLinkRepository = shortLinkRepository;
        this.shortLinkService = shortLinkService;
    }

    @Override
    public void run(String... args) {
        seedMembers();
        seedBooks();
        seedLoansAndFines();
        seedShortLinks();
    }

    private void seedMembers() {
        if (!memberRepository.existsByEmailIgnoreCase("admin@library.com")) {
            Member admin = new Member(
                    "MEM-ADMIN01",
                    "Library Administrator",
                    "admin@library.com",
                    passwordEncoder.encode("Admin@123"),
                    Role.ADMIN,
                    "+1-555-0100"
            );
            memberRepository.save(admin);
            log.info("Seeded Admin account: admin@library.com / Admin@123");
        }

        if (!memberRepository.existsByEmailIgnoreCase("alice@example.com")) {
            Member member1 = new Member(
                    "MEM-ALICE01",
                    "Alice Johnson",
                    "alice@example.com",
                    passwordEncoder.encode("Alice@123"),
                    Role.MEMBER,
                    "+1-555-0101"
            );
            memberRepository.save(member1);
            log.info("Seeded Member account: alice@example.com / Alice@123");
        }

        if (!memberRepository.existsByEmailIgnoreCase("bob@example.com")) {
            Member member2 = new Member(
                    "MEM-BOB01",
                    "Bob Smith",
                    "bob@example.com",
                    passwordEncoder.encode("Bob@123"),
                    Role.MEMBER,
                    "+1-555-0102"
            );
            memberRepository.save(member2);
            log.info("Seeded Member account: bob@example.com / Bob@123");
        }
    }

    private void seedBooks() {
        if (bookRepository.count() > 0) {
            return;
        }

        List<Book> initialBooks = List.of(
                new Book(
                        idGeneratorService.generateBookId(),
                        "Designing Data-Intensive Applications",
                        List.of("Martin Kleppmann"),
                        "978-1449373320",
                        "System Architecture",
                        "The definitive guide to distributed data systems, storage, replication, and partitioning.",
                        5, 3, "Shelf A-101", "INTERNAL"
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "System Design Interview – An Insider's Guide",
                        List.of("Alex Xu"),
                        "979-8664653403",
                        "System Architecture",
                        "Step-by-step framework to scale systems to hundreds of millions of users.",
                        4, 0, "Shelf A-102", "INTERNAL" // 0 available -> perfect for testing reservations!
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "Clean Code: A Handbook of Agile Software Craftsmanship",
                        List.of("Robert C. Martin"),
                        "978-0132350884",
                        "Software Engineering",
                        "Principles, patterns, and practices of writing clean, maintainable code.",
                        6, 4, "Shelf B-201", "INTERNAL"
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "Database Internals: A Deep Dive into How Distributed Systems Work",
                        List.of("Alex Petrov"),
                        "978-1492040347",
                        "Databases",
                        "In-depth guide to modern B-trees, LSM trees, consensus algorithms, and distributed storage.",
                        3, 2, "Shelf B-204", "INTERNAL"
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "Site Reliability Engineering: How Google Runs Production Systems",
                        List.of("Niall Richard Murphy", "Betsy Beyer", "Chris Jones"),
                        "978-1491929124",
                        "DevOps & SRE",
                        "Collection of essays and articles by Google engineers explaining how SRE operates at scale.",
                        4, 4, "Shelf C-301", "INTERNAL"
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "Distributed Systems: Principles and Paradigms",
                        List.of("Andrew S. Tanenbaum", "Maarten van Steen"),
                        "978-0132392273",
                        "Computer Science",
                        "Covers architectures, processes, communication, naming, synchronization, and fault tolerance.",
                        3, 1, "Shelf C-305", "INTERNAL"
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "Grokking Algorithms: An Illustrated Guide",
                        List.of("Aditya Bhargava"),
                        "978-1617292231",
                        "Algorithms",
                        "A friendly, illustrated introduction to essential algorithms and data structures.",
                        5, 5, "Shelf D-101", "INTERNAL"
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "Release It!: Design and Deploy Production-Ready Software",
                        List.of("Michael T. Nygard"),
                        "978-1680502398",
                        "System Architecture",
                        "Circuit breakers, bulkheads, timeouts, and designing systems that survive failure.",
                        2, 1, "Shelf D-108", "INTERNAL"
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "Clean Architecture: A Craftsman's Guide to Software Structure",
                        List.of("Robert C. Martin"),
                        "978-0134494166",
                        "Software Engineering",
                        "Universal rules of software architecture from the author of Clean Code.",
                        4, 3, "Shelf B-202", "INTERNAL"
                ),
                new Book(
                        idGeneratorService.generateBookId(),
                        "Designing Distributed Systems: Patterns and Paradigms",
                        List.of("Brendan Burns"),
                        "978-1491983640",
                        "System Architecture",
                        "Practical patterns for containerized and distributed cloud architecture.",
                        3, 2, "Shelf A-105", "INTERNAL"
                )
        );

        bookRepository.saveAll(initialBooks);
        log.info("Seeded {} initial books into library catalogue", initialBooks.size());
    }

    private void seedLoansAndFines() {
        if (loanRepository.count() > 0) {
            return;
        }

        Book book1 = bookRepository.findByIsbn("978-1449373320").orElse(null);
        Book bookUnavailable = bookRepository.findByIsbn("979-8664653403").orElse(null);

        if (book1 != null) {
            // Seed an active loan for Alice
            String loanId1 = idGeneratorService.generateLoanId();
            Loan loan1 = new Loan(
                    loanId1,
                    "MEM-ALICE01",
                    book1.getBookId(),
                    Instant.now().minus(Duration.ofDays(5)),
                    Instant.now().plus(Duration.ofDays(9))
            );
            loanRepository.save(loan1);

            // Seed an overdue loan with a pending fine for Alice to test simulated fine payment
            String loanIdOverdue = idGeneratorService.generateLoanId();
            Loan overdueLoan = new Loan(
                    loanIdOverdue,
                    "MEM-ALICE01",
                    book1.getBookId(),
                    Instant.now().minus(Duration.ofDays(20)),
                    Instant.now().minus(Duration.ofDays(6)) // 6 days overdue!
            );
            overdueLoan.setStatus(LoanStatus.OVERDUE);
            loanRepository.save(overdueLoan);

            String txnId = idGeneratorService.generateTransactionId();
            FineTransaction fine = new FineTransaction(txnId, loanIdOverdue, "MEM-ALICE01", BigDecimal.valueOf(30.00));
            fineRepository.save(fine);

            log.info("Seeded sample active and overdue loans + fine for Alice");
        }

        if (bookUnavailable != null) {
            // Seed a reservation for Bob on the 0-available book
            String resId = idGeneratorService.generateReservationId();
            Reservation res = new Reservation(resId, "MEM-BOB01", bookUnavailable.getBookId(), 1);
            reservationRepository.save(res);
            log.info("Seeded sample reservation #1 for Bob on unavailable book");
        }
    }

    private void seedShortLinks() {
        if (shortLinkRepository.count() == 0) {
            try {
                com.library.dto.CreateShortLinkRequest req1 = new com.library.dto.CreateShortLinkRequest("/book.html?id=BK-100", "BK-100");
                req1.setTitle("Designing Data-Intensive Applications");
                shortLinkService.createShortLink(req1);

                com.library.dto.CreateShortLinkRequest req2 = new com.library.dto.CreateShortLinkRequest("/book.html?id=BK-101", "BK-101");
                req2.setTitle("Clean Architecture: Craftsman's Guide");
                shortLinkService.createShortLink(req2);

                log.info("Seeded initial demonstration short links (Member 4)");
            } catch (Exception ex) {
                log.warn("Failed to seed initial short links: {}", ex.getMessage());
            }
        }
    }
}
