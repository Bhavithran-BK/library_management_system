package com.library.dto;

import java.math.BigDecimal;

public class PayFineRequest {

    private BigDecimal amount;
    private String paymentMethod = "SIMULATED_CARD"; // Simulated
    private String idempotencyKey;

    public PayFineRequest() {
    }

    public PayFineRequest(BigDecimal amount, String paymentMethod, String idempotencyKey) {
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.idempotencyKey = idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
