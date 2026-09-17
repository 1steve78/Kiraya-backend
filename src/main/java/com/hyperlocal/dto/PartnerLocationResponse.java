package com.hyperlocal.dto;

import java.time.Instant;

public record PartnerLocationResponse(
        Long partnerId,
        Double latitude,
        Double longitude,
        Instant timestamp
) {
}
