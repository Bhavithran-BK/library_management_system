package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.DashboardStatsResponse;
import com.library.model.FineStatus;
import com.library.model.FineTransaction;
import com.library.model.LoanStatus;
import com.library.model.ReservationStatus;
import com.library.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class DashboardStatsController {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final ReservationRepository reservationRepository;
    private final FineTransactionRepository fineRepository;

    public DashboardStatsController(BookRepository bookRepository,
                                    MemberRepository memberRepository,
                                    LoanRepository loanRepository,
                                    ReservationRepository reservationRepository,
                                    FineTransactionRepository fineRepository) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.reservationRepository = reservationRepository;
        this.fineRepository = fineRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getDashboardStats() {
        long totalBooks = bookRepository.count();
        long availableBooks = bookRepository.countByAvailableCopiesGreaterThan(0);
        long activeLoans = loanRepository.countByStatus(LoanStatus.ACTIVE);
        long totalReservations = reservationRepository.countByStatus(ReservationStatus.PENDING);
        long totalMembers = memberRepository.count();

        List<FineTransaction> pendingFines = fineRepository.findByStatusOrderByCreatedAtDesc(FineStatus.PENDING);
        BigDecimal pendingSum = pendingFines.stream()
                .map(FineTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FineTransaction> paidFines = fineRepository.findByStatusOrderByCreatedAtDesc(FineStatus.PAID);
        BigDecimal paidSum = paidFines.stream()
                .map(FineTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DashboardStatsResponse stats = new DashboardStatsResponse(
                totalBooks,
                availableBooks,
                activeLoans,
                totalReservations,
                totalMembers,
                pendingSum,
                paidSum
        );

        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
