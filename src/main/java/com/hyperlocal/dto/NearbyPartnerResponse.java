package com.hyperlocal.dto;

public record NearbyPartnerResponse(
        Long partnerId,
        Double distanceKm
) {
}
