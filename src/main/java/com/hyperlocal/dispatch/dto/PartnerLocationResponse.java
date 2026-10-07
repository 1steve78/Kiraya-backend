package com.hyperlocal.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerLocationResponse(
        Long partnerId,
        @JsonAlias({"lat"})
        Double latitude,
        @JsonAlias({"lng"})
        Double longitude,
        Instant timestamp
) {
    public Double lat() {
        return latitude;
    }

    public Double lng() {
        return longitude;
    }
}
