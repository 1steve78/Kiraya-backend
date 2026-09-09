package com.hyperlocal.dto;

import com.hyperlocal.model.OrderStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public class OrderStatusUpdateRequest {

    // Accept both "status" (frontend) and the legacy "orderStatus" field name
    @NotNull(message = "Order status is required")
    @JsonAlias("orderStatus")
    private OrderStatus status;

    // Optional reason for cancellation / rejection
    private String reason;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    /** Alias kept so existing service code that calls getOrderStatus() still compiles. */
    public OrderStatus getOrderStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
