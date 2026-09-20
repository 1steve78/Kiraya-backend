package com.hyperlocal.dispatch.dto;

import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;

import java.time.Instant;

public record DeliveryOfferResponse(
        Long id,
        Long deliveryId,
        Long partnerId,
        DeliveryOfferStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant respondedAt
) {
    public static DeliveryOfferResponse from(DeliveryOffer offer) {
        return new DeliveryOfferResponse(
                offer.getId(),
                offer.getDeliveryId(),
                offer.getPartnerId(),
                offer.getStatus(),
                offer.getCreatedAt(),
                offer.getExpiresAt(),
                offer.getRespondedAt()
        );
    }
}
