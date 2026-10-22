package com.library.dto;

import jakarta.validation.constraints.NotBlank;

public class ReservationRequest {

    @NotBlank(message = "Member ID is required")
    private String memberId;

    @NotBlank(message = "Book ID is required")
    private String bookId;

    public ReservationRequest() {
    }

    public ReservationRequest(String memberId, String bookId) {
        this.memberId = memberId;
        this.bookId = bookId;
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
}
