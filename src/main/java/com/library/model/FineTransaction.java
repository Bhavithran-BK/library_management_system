package com.library.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "fines")
public class FineTransaction {

    @Id
    private String id;

    @Indexed(unique = true)
    private String transactionId;

    @Indexed
    private String loanId;

    @Indexed
    private String memberId;

    private BigDecimal amount;

    private FineStatus status = FineStatus.PENDING;

    private Instant createdAt = Instant.now();

    private Instant paidAt;

    private String paymentReference;

    private boolean simulated = true;

    public FineTransaction() {
    }

    public FineTransaction(String transactionId, String loanId, String memberId, BigDecimal amount) {
        this.transactionId = transactionId;
        this.loanId = loanId;
        this.memberId = memberId;
        this.amount = amount;
        this.status = FineStatus.PENDING;
        this.createdAt = Instant.now();
        this.simulated = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public FineStatus getStatus() {
        return status;
    }

    public void setStatus(FineStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public void setSimulated(boolean simulated) {
        this.simulated = simulated;
    }
}
