package com.library.service;

import com.library.dto.PayFineRequest;
import com.library.exception.BadRequestException;
import com.library.exception.ConflictException;
import com.library.exception.ResourceNotFoundException;
import com.library.idgenerator.IdGeneratorService;
import com.library.model.FineStatus;
import com.library.model.FineTransaction;
import com.library.model.Loan;
import com.library.model.LoanStatus;
import com.library.repository.FineTransactionRepository;
import com.library.repository.LoanRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class FineService {

    private final FineTransactionRepository fineRepository;
    private final LoanRepository loanRepository;
    private final IdGeneratorService idGeneratorService;

    @Value("${app.library.fine.per-day-rate:5.0}")
    private double finePerDayRate;

    public FineService(FineTransactionRepository fineRepository,
                       LoanRepository loanRepository,
                       IdGeneratorService idGeneratorService) {
        this.fineRepository = fineRepository;
        this.loanRepository = loanRepository;
        this.idGeneratorService = idGeneratorService;
    }

    public List<FineTransaction> getMemberFines(String memberId) {
        syncOverdueLoans(memberId);
        return fineRepository.findByMemberIdOrderByCreatedAtDesc(memberId.trim());
    }

    /**
     * Inspect active loans for this member and create pending fine records for overdue books.
     */
    public void syncOverdueLoans(String memberId) {
        List<Loan> activeLoans = loanRepository.findByMemberIdAndStatus(memberId.trim(), LoanStatus.ACTIVE);
        Instant now = Instant.now();

        for (Loan loan : activeLoans) {
            if (now.isAfter(loan.getDueAt())) {
                // If fine record doesn't already exist for this active overdue loan, create it
                if (fineRepository.findByLoanId(loan.getLoanId()).isEmpty()) {
                    long overdueDays = ChronoUnit.DAYS.between(loan.getDueAt(), now);
                    if (overdueDays <= 0) overdueDays = 1;
                    BigDecimal amount = BigDecimal.valueOf(overdueDays * finePerDayRate);

                    String txnId = idGeneratorService.generateTransactionId();
                    FineTransaction fine = new FineTransaction(txnId, loan.getLoanId(), memberId, amount);
                    fineRepository.save(fine);
                }
            }
        }
    }

    /**
     * Process simulated payment.
     * Prevents accidental duplicate payments (idempotency check).
     */
    public FineTransaction payFine(String transactionId, PayFineRequest request) {
        FineTransaction fine = fineRepository.findByTransactionId(transactionId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Fine transaction not found with ID: " + transactionId));

        if (fine.getStatus() == FineStatus.PAID) {
            // Idempotent response: return already-settled transaction instead of double charging
            return fine;
        }

        fine.setStatus(FineStatus.PAID);
        fine.setPaidAt(Instant.now());
        fine.setSimulated(true);
        fine.setPaymentReference("SIM-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        return fineRepository.save(fine);
    }

    public List<FineTransaction> getAllFines() {
        return fineRepository.findAll();
    }
}
