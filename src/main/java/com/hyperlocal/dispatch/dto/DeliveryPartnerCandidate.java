package com.hyperlocal.dispatch.dto;

import com.hyperlocal.dispatch.enums.AvailabilityStatus;
import com.hyperlocal.dispatch.enums.RejectionReason;

import java.time.Instant;

public class DeliveryPartnerCandidate {
    private Long partnerId;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    private Instant lastSeen;
    private AvailabilityStatus availability;
    private boolean eligible;
    private RejectionReason reason;

    public DeliveryPartnerCandidate() {
    }

    public DeliveryPartnerCandidate(Long partnerId, Double latitude, Double longitude, Double distanceKm, Instant lastSeen, AvailabilityStatus availability, boolean eligible, RejectionReason reason) {
        this.partnerId = partnerId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
        this.lastSeen = lastSeen;
        this.availability = availability;
        this.eligible = eligible;
        this.reason = reason;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public AvailabilityStatus getAvailability() {
        return availability;
    }

    public void setAvailability(AvailabilityStatus availability) {
        this.availability = availability;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public RejectionReason getReason() {
        return reason;
    }

    public void setReason(RejectionReason reason) {
        this.reason = reason;
    }
}
