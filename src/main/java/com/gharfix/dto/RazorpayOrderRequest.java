package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RazorpayOrderRequest {

    private Long amount; // in paise
    private String currency = "INR";
    private String receipt;

    @JsonProperty("booking_id")
    private Long bookingId;

    public RazorpayOrderRequest() {
    }

    public RazorpayOrderRequest(Long amount, String currency, String receipt) {
        this.amount = amount;
        this.currency = currency;
        this.receipt = receipt;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return (currency == null || currency.isBlank()) ? "INR" : currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }
}
