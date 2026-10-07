package com.hyperlocal.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerLocationRecord(
        @JsonProperty("lat")
        @JsonAlias({"latitude"})
        Double lat,

        @JsonProperty("lng")
        @JsonAlias({"longitude"})
        Double lng,

        Instant timestamp
) {
    public PartnerLocationRecord {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public Double latitude() {
        return lat;
    }

    public Double longitude() {
        return lng;
    }
}
