package com.library.dto;

import java.math.BigDecimal;

public class DashboardStatsResponse {

    private long totalBooks;
    private long availableBooks;
    private long activeLoans;
    private long totalReservations;
    private long totalMembers;
    private BigDecimal pendingFinesAmount;
    private BigDecimal collectedFinesAmount;

    public DashboardStatsResponse() {
    }

    public DashboardStatsResponse(long totalBooks, long availableBooks, long activeLoans,
                                  long totalReservations, long totalMembers,
                                  BigDecimal pendingFinesAmount, BigDecimal collectedFinesAmount) {
        this.totalBooks = totalBooks;
        this.availableBooks = availableBooks;
        this.activeLoans = activeLoans;
        this.totalReservations = totalReservations;
        this.totalMembers = totalMembers;
        this.pendingFinesAmount = pendingFinesAmount;
        this.collectedFinesAmount = collectedFinesAmount;
    }

    public long getTotalBooks() {
        return totalBooks;
    }

    public void setTotalBooks(long totalBooks) {
        this.totalBooks = totalBooks;
    }

    public long getAvailableBooks() {
        return availableBooks;
    }

    public void setAvailableBooks(long availableBooks) {
        this.availableBooks = availableBooks;
    }

    public long getActiveLoans() {
        return activeLoans;
    }

    public void setActiveLoans(long activeLoans) {
        this.activeLoans = activeLoans;
    }

    public long getTotalReservations() {
        return totalReservations;
    }

    public void setTotalReservations(long totalReservations) {
        this.totalReservations = totalReservations;
    }

    public long getTotalMembers() {
        return totalMembers;
    }

    public void setTotalMembers(long totalMembers) {
        this.totalMembers = totalMembers;
    }

    public BigDecimal getPendingFinesAmount() {
        return pendingFinesAmount;
    }

    public void setPendingFinesAmount(BigDecimal pendingFinesAmount) {
        this.pendingFinesAmount = pendingFinesAmount;
    }

    public BigDecimal getCollectedFinesAmount() {
        return collectedFinesAmount;
    }

    public void setCollectedFinesAmount(BigDecimal collectedFinesAmount) {
        this.collectedFinesAmount = collectedFinesAmount;
    }
}
