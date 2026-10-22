package com.library.repository;

import com.library.model.Loan;
import com.library.model.LoanStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends MongoRepository<Loan, String> {

    Optional<Loan> findByLoanId(String loanId);

    List<Loan> findByMemberIdOrderByBorrowedAtDesc(String memberId);

    List<Loan> findByBookIdAndStatus(String bookId, LoanStatus status);

    List<Loan> findByMemberIdAndStatus(String memberId, LoanStatus status);

    List<Loan> findByStatusOrderByBorrowedAtDesc(LoanStatus status);

    long countByStatus(LoanStatus status);

    boolean existsByMemberIdAndBookIdAndStatus(String memberId, String bookId, LoanStatus status);
}
