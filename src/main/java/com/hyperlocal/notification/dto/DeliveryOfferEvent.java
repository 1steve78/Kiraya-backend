package com.hyperlocal.notification.dto;

import com.hyperlocal.dispatch.dto.Coordinates;

import java.time.Instant;

public record DeliveryOfferEvent(
        Long offerId,
        Long deliveryId,
        Coordinates pickup,
        Instant expiresAt
) {
}
