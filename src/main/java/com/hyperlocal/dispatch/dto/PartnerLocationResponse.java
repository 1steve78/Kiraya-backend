package com.hyperlocal.dispatch.dto;

import java.time.Instant;

public record PartnerLocationResponse(
        Long partnerId,
        Double latitude,
        Double longitude,
        Instant timestamp
) {
}
