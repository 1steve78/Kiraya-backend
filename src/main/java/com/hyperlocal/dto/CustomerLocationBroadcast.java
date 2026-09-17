package com.hyperlocal.dto;

import java.time.Instant;

public record CustomerLocationBroadcast(
        Long deliveryId,
        Double latitude,
        Double longitude,
        Instant timestamp
) {}