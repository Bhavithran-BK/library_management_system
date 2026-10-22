package com.library.repository;

import com.library.model.FineStatus;
import com.library.model.FineTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FineTransactionRepository extends MongoRepository<FineTransaction, String> {

    Optional<FineTransaction> findByTransactionId(String transactionId);

    Optional<FineTransaction> findByLoanId(String loanId);

    List<FineTransaction> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<FineTransaction> findByStatusOrderByCreatedAtDesc(FineStatus status);

    long countByStatus(FineStatus status);
}
