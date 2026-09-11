package com.hyperlocal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewDeliveryEvent {
    private Long deliveryId;
    private Long orderId;
    private Long shopId;
    private String pickupLocation;
    private String dropoffArea;
    private Double estimatedDistanceKm;
    private BigDecimal deliveryFee;
}
