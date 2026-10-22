package com.library.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "loans")
public class Loan {

    @Id
    private String id;

    @Indexed(unique = true)
    private String loanId;

    @Indexed
    private String memberId;

    @Indexed
    private String bookId;

    private Instant borrowedAt;

    private Instant dueAt;

    private Instant returnedAt;

    private LoanStatus status = LoanStatus.ACTIVE;

    public Loan() {
    }

    public Loan(String loanId, String memberId, String bookId, Instant borrowedAt, Instant dueAt) {
        this.loanId = loanId;
        this.memberId = memberId;
        this.bookId = bookId;
        this.borrowedAt = borrowedAt;
        this.dueAt = dueAt;
        this.status = LoanStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public void setBorrowedAt(Instant borrowedAt) {
        this.borrowedAt = borrowedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(Instant returnedAt) {
        this.returnedAt = returnedAt;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }
}
