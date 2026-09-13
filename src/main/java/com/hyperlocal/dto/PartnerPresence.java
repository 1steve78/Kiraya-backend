package com.hyperlocal.dto;

import com.hyperlocal.model.AvailabilityStatus;

import java.time.Instant;

public record PartnerPresence(
        Long Id,
        AvailabilityStatus status,
        Instant lastSeen
) {
}
