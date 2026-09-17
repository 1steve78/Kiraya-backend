package com.hyperlocal.controller;

import com.hyperlocal.dto.PartnerPresence;
import com.hyperlocal.dto.PartnerPresenceResponse;
import com.hyperlocal.service.DeliveryPartnerService;
import com.hyperlocal.service.PresenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/delivery-partners")
public class DeliveryPartnerController {

    private final DeliveryPartnerService partnerService;
    private final PresenceService presenceService;
    private final com.hyperlocal.service.LocationService locationService;
    private final com.hyperlocal.service.GeoLocationService geoLocationService;

    public DeliveryPartnerController(DeliveryPartnerService partnerService, PresenceService presenceService, com.hyperlocal.service.LocationService locationService, com.hyperlocal.service.GeoLocationService geoLocationService){
        this.partnerService = partnerService;
        this.presenceService = presenceService;
        this.locationService = locationService;
        this.geoLocationService = geoLocationService;
    }

    @PostMapping("/me/online")
    public ResponseEntity<PartnerPresenceResponse> goOnline(@RequestHeader("X-Partner-Id") Long partnerId){
        PartnerPresence presence = partnerService.goOnline(partnerId);
        return ResponseEntity.ok(PartnerPresenceResponse.from(presence));
    }

    @PostMapping("/me/offline")
    public ResponseEntity<PartnerPresenceResponse> goOffline(@RequestHeader("X-Partner-Id") Long partnerId){
        PartnerPresence presence = partnerService.goOffline(partnerId);
        return ResponseEntity.ok(PartnerPresenceResponse.from(presence));
    }

    @PostMapping("/me/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestHeader("X-Partner-Id") Long partnerId) {
        boolean refreshed = partnerService.recordHeartbeat(partnerId);
        if (!refreshed) {
            // Heartbeat failed because partner is expired/offline in Redis
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/presence")
    public ResponseEntity<PartnerPresenceResponse> getMyPresence(@RequestHeader("X-Partner-Id") Long partnerId) {
        PartnerPresence presence = partnerService.getCurrentPresence(partnerId);
        return ResponseEntity.ok(PartnerPresenceResponse.from(presence));
    }

    @GetMapping("/available")
    public ResponseEntity<List<PartnerPresenceResponse>> getAvailablePartners() {
        List<PartnerPresenceResponse> responses = presenceService.getAvailablePartners()
                .stream()
                .map(PartnerPresenceResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<com.hyperlocal.dto.NearbyPartnerResponse>> getNearbyPartners(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double radius
    ) {
        return ResponseEntity.ok(geoLocationService.findNearbyPartners(latitude, longitude, radius));
    }

    @GetMapping("/{id}/location")
    public ResponseEntity<?> getPartnerLocation(@PathVariable Long id) {
        return locationService.getLatestLocation(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
