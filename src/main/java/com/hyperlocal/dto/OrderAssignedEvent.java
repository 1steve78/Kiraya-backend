package com.hyperlocal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderAssignedEvent {
    private Long orderId;
    private Long deliveryPartnerId;
}
