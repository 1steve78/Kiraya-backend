package com.hyperlocal.dispatch.service;

import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.dto.Coordinates;
import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;
import com.hyperlocal.dispatch.dto.NearbyPartnerResponse;
import com.hyperlocal.dispatch.dto.PartnerLocationResponse;
import com.hyperlocal.dispatch.dto.PartnerPresence;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;
import com.hyperlocal.dispatch.enums.RejectionReason;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EligiblePartnerService {
    private static final Logger log = LoggerFactory.getLogger(EligiblePartnerService.class);

    private final GeoLocationService geoLocationService;
    private final PresenceService presenceService;
    private final LocationService locationService;

    @Value("${dispatch.search-radius-km:3.0}")
    private double searchRadiusKm;

    @Value("${dispatch.max-location-age-seconds:30}")
    private long maxLocationAgeSeconds;

    public EligiblePartnerService(GeoLocationService geoLocationService,
                                  PresenceService presenceService,
                                  LocationService locationService) {
        this.geoLocationService = geoLocationService;
        this.presenceService = presenceService;
        this.locationService = locationService;
    }

    public List<DeliveryPartnerCandidate> findEligiblePartners(double pickupLat, double pickupLng) {
        return findEligiblePartners(pickupLat, pickupLng, searchRadiusKm);
    }

    public List<DeliveryPartnerCandidate> findEligiblePartners(double pickupLat, double pickupLng, double radiusKm) {
        List<DeliveryPartnerCandidate> candidates = new ArrayList<>();

        // 1. Get Nearby Partners
        List<NearbyPartnerResponse> nearby = geoLocationService.findNearbyPartners(pickupLat, pickupLng, radiusKm);

        for (NearbyPartnerResponse partner : nearby) {
            DeliveryPartnerCandidate candidate = createCandidate(partner.partnerId(), partner.distanceKm());
            
            // If distance is somehow outside radius (e.g. edge cases in geo hash)
            if (partner.distanceKm() > radiusKm) {
                candidate.setEligible(false);
                candidate.setReason(RejectionReason.OUTSIDE_RADIUS);
                candidates.add(candidate);
                continue;
            }

            // 2. Check Presence
            Optional<PartnerPresence> presenceOpt = presenceService.getPresence(partner.partnerId());
            if (presenceOpt.isEmpty() || presenceOpt.get().status() == AvailabilityStatus.OFFLINE) {
                candidate.setEligible(false);
                candidate.setReason(RejectionReason.OFFLINE);
                candidates.add(candidate);
                continue;
            }

            // 3. Check BUSY
            AvailabilityStatus status = presenceOpt.get().status();
            candidate.setAvailability(status);
            if (status == AvailabilityStatus.BUSY) {
                candidate.setEligible(false);
                candidate.setReason(RejectionReason.BUSY);
                candidates.add(candidate);
                continue;
            }

            // 4. Check Location Freshness & Validation
            Optional<PartnerLocationResponse> locationOpt = locationService.getLatestLocation(partner.partnerId());
            if (locationOpt.isEmpty()) {
                candidate.setEligible(false);
                candidate.setReason(RejectionReason.STALE_LOCATION); // Fallback if no location exists, treat as stale
                candidates.add(candidate);
                continue;
            }

            PartnerLocationResponse location = locationOpt.get();
            candidate.setLatitude(location.latitude());
            candidate.setLongitude(location.longitude());
            candidate.setLastSeen(location.timestamp());

            if (!isValidCoordinate(location.latitude(), location.longitude())) {
                candidate.setEligible(false);
                candidate.setReason(RejectionReason.INVALID_COORDINATES);
                candidates.add(candidate);
                continue;
            }

            if (isLocationStale(location.timestamp())) {
                candidate.setEligible(false);
                candidate.setReason(RejectionReason.STALE_LOCATION);
                candidates.add(candidate);
                continue;
            }

            // Valid Candidate
            candidate.setEligible(true);
            candidates.add(candidate);
        }

        return candidates;
    }

    private DeliveryPartnerCandidate createCandidate(Long partnerId, Double distanceKm) {
        DeliveryPartnerCandidate candidate = new DeliveryPartnerCandidate();
        candidate.setPartnerId(partnerId);
        candidate.setDistanceKm(distanceKm);
        return candidate;
    }

    private boolean isLocationStale(Instant timestamp) {
        if (timestamp == null) return true;
        Instant threshold = Instant.now().minus(maxLocationAgeSeconds, ChronoUnit.SECONDS);
        return timestamp.isBefore(threshold);
    }

    private boolean isValidCoordinate(Double lat, Double lng) {
        if (lat == null || lng == null) return false;
        return new Coordinates(lat, lng).isValid();
    }
}
