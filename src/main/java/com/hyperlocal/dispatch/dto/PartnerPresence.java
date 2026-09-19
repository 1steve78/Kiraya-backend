package com.hyperlocal.dispatch.dto;

import com.hyperlocal.dispatch.enums.AvailabilityStatus;

import java.time.Instant;

public record PartnerPresence(
        Long Id,
        AvailabilityStatus status,
        Instant lastSeen
) {
}
