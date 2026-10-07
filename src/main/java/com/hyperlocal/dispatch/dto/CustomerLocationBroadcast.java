package com.hyperlocal.dispatch.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerLocationBroadcast(
        @JsonProperty("type")
        String type,

        @JsonProperty("deliveryId")
        @JsonAlias({"orderId"})
        Object deliveryId,

        @JsonProperty("lat")
        @JsonAlias({"latitude"})
        Double latitude,

        @JsonProperty("lng")
        @JsonAlias({"longitude"})
        Double longitude,

        Instant timestamp
) {
    public CustomerLocationBroadcast {
        if (type == null) {
            type = "DELIVERY_LOCATION_UPDATED";
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public CustomerLocationBroadcast(Long deliveryId, Double latitude, Double longitude, Instant timestamp) {
        this("DELIVERY_LOCATION_UPDATED", (Object) deliveryId, latitude, longitude, timestamp);
    }

    public CustomerLocationBroadcast(String type, Long deliveryId, Double latitude, Double longitude, Instant timestamp) {
        this(type, (Object) deliveryId, latitude, longitude, timestamp);
    }

    public Double lat() {
        return latitude;
    }

    public Double lng() {
        return longitude;
    }

    public Long getDeliveryIdAsLong() {
        if (deliveryId instanceof Number num) {
            return num.longValue();
        }
        if (deliveryId instanceof String str) {
            try {
                return Long.parseLong(str.replace("delivery-", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
