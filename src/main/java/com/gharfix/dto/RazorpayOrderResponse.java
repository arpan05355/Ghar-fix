package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RazorpayOrderResponse {

    @JsonProperty("order_id")
    private String orderId;

    private Long amount; // in paise
    private String currency;

    public RazorpayOrderResponse() {
    }

    public RazorpayOrderResponse(String orderId, Long amount, String currency) {
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
