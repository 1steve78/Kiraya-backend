package com.hyperlocal.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationDto(
        Long partnerId,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90.0")
        @DecimalMax(value = "90.0", message = "Latitude must be <= 90.0")
        @JsonAlias({"latitude"})
        Double lat,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180.0")
        @DecimalMax(value = "180.0", message = "Longitude must be <= 180.0")
        @JsonAlias({"longitude"})
        Double lng,

        Instant timestamp,

        @JsonAlias({"orderId"})
        Long deliveryId
) {
    public LocationDto {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public LocationDto(Long partnerId, Double lat, Double lng, Instant timestamp) {
        this(partnerId, lat, lng, timestamp, null);
    }

    public LocationDto(Double lat, Double lng, Instant timestamp) {
        this(null, lat, lng, timestamp, null);
    }

    public LocationDto(Double lat, Double lng) {
        this(null, lat, lng, Instant.now(), null);
    }

    public Double latitude() {
        return lat;
    }

    public Double longitude() {
        return lng;
    }
}
