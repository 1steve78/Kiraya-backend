package com.hyperlocal.notification.dto;

import com.hyperlocal.order.enums.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderStatusEvent {
    private Long orderId;
    private OrderStatus status;
}
