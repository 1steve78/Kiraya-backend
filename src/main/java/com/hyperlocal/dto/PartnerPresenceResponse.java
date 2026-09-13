package com.hyperlocal.dto;

import com.hyperlocal.dto.PartnerPresence;
import com.hyperlocal.model.AvailabilityStatus;

import java.time.Instant;

public record PartnerPresenceResponse(
        Long partnerId,
        AvailabilityStatus status,
        Instant lastSeen,
        long secondsSinceLastSeen
) {
    public static PartnerPresenceResponse from(PartnerPresence presence) {
        long diff = Instant.now().getEpochSecond() - presence.lastSeen().getEpochSecond();
        return new PartnerPresenceResponse(
                presence.Id(),
                presence.status(),
                presence.lastSeen(),
                Math.max(0, diff)
        );
    }
}