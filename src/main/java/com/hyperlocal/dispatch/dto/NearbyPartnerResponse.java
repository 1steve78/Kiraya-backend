package com.hyperlocal.dispatch.dto;

public record NearbyPartnerResponse(
        Long partnerId,
        Double distanceKm
) {
}
