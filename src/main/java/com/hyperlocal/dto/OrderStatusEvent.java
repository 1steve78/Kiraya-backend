package com.hyperlocal.dto;

import com.hyperlocal.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderStatusEvent {
    private Long orderId;
    private OrderStatus status;
}
