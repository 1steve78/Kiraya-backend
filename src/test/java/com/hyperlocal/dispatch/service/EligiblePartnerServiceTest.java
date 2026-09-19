package com.hyperlocal.dispatch.service;

import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;
import com.hyperlocal.dispatch.dto.NearbyPartnerResponse;
import com.hyperlocal.dispatch.dto.PartnerLocationResponse;
import com.hyperlocal.dispatch.dto.PartnerPresence;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;
import com.hyperlocal.dispatch.enums.RejectionReason;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EligiblePartnerServiceTest {

    @Mock
    private GeoLocationService geoLocationService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private LocationService locationService;

    @InjectMocks
    private EligiblePartnerService eligiblePartnerService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eligiblePartnerService, "searchRadiusKm", 3.0);
        ReflectionTestUtils.setField(eligiblePartnerService, "maxLocationAgeSeconds", 30L);
    }

    @Test
    void testEligiblePartner() {
        // ONLINE, fresh, nearby
        Long partnerId = 101L;
        double lat = 10.0, lng = 20.0;
        
        when(geoLocationService.findNearbyPartners(lat, lng, 3.0))
                .thenReturn(List.of(new NearbyPartnerResponse(partnerId, 1.5)));
                
        when(presenceService.getPresence(partnerId))
                .thenReturn(Optional.of(new PartnerPresence(partnerId, AvailabilityStatus.ONLINE, Instant.now())));
                
        when(locationService.getLatestLocation(partnerId))
                .thenReturn(Optional.of(new PartnerLocationResponse(partnerId, lat + 0.01, lng + 0.01, Instant.now())));

        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(lat, lng);
        
        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).isEligible());
        assertEquals(partnerId, candidates.get(0).getPartnerId());
    }

    @Test
    void testOfflinePartner() {
        // OFFLINE, fresh, nearby
        Long partnerId = 102L;
        double lat = 10.0, lng = 20.0;
        
        when(geoLocationService.findNearbyPartners(lat, lng, 3.0))
                .thenReturn(List.of(new NearbyPartnerResponse(partnerId, 1.5)));
                
        when(presenceService.getPresence(partnerId))
                .thenReturn(Optional.of(new PartnerPresence(partnerId, AvailabilityStatus.OFFLINE, Instant.now())));

        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(lat, lng);
        
        assertEquals(1, candidates.size());
        assertFalse(candidates.get(0).isEligible());
        assertEquals(RejectionReason.OFFLINE, candidates.get(0).getReason());
    }
    
    @Test
    void testBusyPartner() {
        // BUSY, fresh, nearby
        Long partnerId = 103L;
        double lat = 10.0, lng = 20.0;
        
        when(geoLocationService.findNearbyPartners(lat, lng, 3.0))
                .thenReturn(List.of(new NearbyPartnerResponse(partnerId, 1.5)));
                
        when(presenceService.getPresence(partnerId))
                .thenReturn(Optional.of(new PartnerPresence(partnerId, AvailabilityStatus.BUSY, Instant.now())));

        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(lat, lng);
        
        assertEquals(1, candidates.size());
        assertFalse(candidates.get(0).isEligible());
        assertEquals(RejectionReason.BUSY, candidates.get(0).getReason());
    }

    @Test
    void testStaleLocationPartner() {
        // ONLINE, stale location, nearby
        Long partnerId = 104L;
        double lat = 10.0, lng = 20.0;
        
        when(geoLocationService.findNearbyPartners(lat, lng, 3.0))
                .thenReturn(List.of(new NearbyPartnerResponse(partnerId, 1.5)));
                
        when(presenceService.getPresence(partnerId))
                .thenReturn(Optional.of(new PartnerPresence(partnerId, AvailabilityStatus.ONLINE, Instant.now())));
                
        Instant staleTime = Instant.now().minus(31, ChronoUnit.SECONDS);
        when(locationService.getLatestLocation(partnerId))
                .thenReturn(Optional.of(new PartnerLocationResponse(partnerId, lat + 0.01, lng + 0.01, staleTime)));

        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(lat, lng);
        
        assertEquals(1, candidates.size());
        assertFalse(candidates.get(0).isEligible());
        assertEquals(RejectionReason.STALE_LOCATION, candidates.get(0).getReason());
    }
    
    @Test
    void testOutsideRadius() {
        // ONLINE, fresh, outside radius (returned by geo by mistake or edge case)
        Long partnerId = 105L;
        double lat = 10.0, lng = 20.0;
        
        when(geoLocationService.findNearbyPartners(lat, lng, 3.0))
                .thenReturn(List.of(new NearbyPartnerResponse(partnerId, 3.5))); // 3.5 > 3.0

        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(lat, lng);
        
        assertEquals(1, candidates.size());
        assertFalse(candidates.get(0).isEligible());
        assertEquals(RejectionReason.OUTSIDE_RADIUS, candidates.get(0).getReason());
    }

    @Test
    void testMixedPool() {
        double lat = 10.0, lng = 20.0;
        
        // Partner A → ONLINE + fresh + nearby
        Long idA = 1L;
        NearbyPartnerResponse resA = new NearbyPartnerResponse(idA, 1.0);
        
        // Partner B → BUSY + fresh + nearby
        Long idB = 2L;
        NearbyPartnerResponse resB = new NearbyPartnerResponse(idB, 1.1);
        
        // Partner C → OFFLINE + fresh + nearby
        Long idC = 3L;
        NearbyPartnerResponse resC = new NearbyPartnerResponse(idC, 1.2);
        
        // Partner D → ONLINE + stale + nearby
        Long idD = 4L;
        NearbyPartnerResponse resD = new NearbyPartnerResponse(idD, 1.3);
        
        // Partner E → ONLINE + fresh + nearby
        Long idE = 5L;
        NearbyPartnerResponse resE = new NearbyPartnerResponse(idE, 1.4);
        
        // Partner F → ONLINE + fresh + far away
        Long idF = 6L;
        NearbyPartnerResponse resF = new NearbyPartnerResponse(idF, 4.0); // far

        when(geoLocationService.findNearbyPartners(lat, lng, 3.0))
                .thenReturn(Arrays.asList(resA, resB, resC, resD, resE, resF));

        // Presence
        when(presenceService.getPresence(idA)).thenReturn(Optional.of(new PartnerPresence(idA, AvailabilityStatus.ONLINE, Instant.now())));
        when(presenceService.getPresence(idB)).thenReturn(Optional.of(new PartnerPresence(idB, AvailabilityStatus.BUSY, Instant.now())));
        when(presenceService.getPresence(idC)).thenReturn(Optional.of(new PartnerPresence(idC, AvailabilityStatus.OFFLINE, Instant.now())));
        when(presenceService.getPresence(idD)).thenReturn(Optional.of(new PartnerPresence(idD, AvailabilityStatus.ONLINE, Instant.now())));
        when(presenceService.getPresence(idE)).thenReturn(Optional.of(new PartnerPresence(idE, AvailabilityStatus.ONLINE, Instant.now())));
        
        // Location
        when(locationService.getLatestLocation(idA)).thenReturn(Optional.of(new PartnerLocationResponse(idA, lat, lng, Instant.now())));
        when(locationService.getLatestLocation(idD)).thenReturn(Optional.of(new PartnerLocationResponse(idD, lat, lng, Instant.now().minus(40, ChronoUnit.SECONDS))));
        when(locationService.getLatestLocation(idE)).thenReturn(Optional.of(new PartnerLocationResponse(idE, lat, lng, Instant.now())));
        
        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(lat, lng);
        
        assertEquals(6, candidates.size());
        
        DeliveryPartnerCandidate cA = candidates.stream().filter(c -> c.getPartnerId().equals(idA)).findFirst().get();
        assertTrue(cA.isEligible());
        
        DeliveryPartnerCandidate cB = candidates.stream().filter(c -> c.getPartnerId().equals(idB)).findFirst().get();
        assertFalse(cB.isEligible());
        assertEquals(RejectionReason.BUSY, cB.getReason());
        
        DeliveryPartnerCandidate cC = candidates.stream().filter(c -> c.getPartnerId().equals(idC)).findFirst().get();
        assertFalse(cC.isEligible());
        assertEquals(RejectionReason.OFFLINE, cC.getReason());
        
        DeliveryPartnerCandidate cD = candidates.stream().filter(c -> c.getPartnerId().equals(idD)).findFirst().get();
        assertFalse(cD.isEligible());
        assertEquals(RejectionReason.STALE_LOCATION, cD.getReason());
        
        DeliveryPartnerCandidate cE = candidates.stream().filter(c -> c.getPartnerId().equals(idE)).findFirst().get();
        assertTrue(cE.isEligible());
        
        DeliveryPartnerCandidate cF = candidates.stream().filter(c -> c.getPartnerId().equals(idF)).findFirst().get();
        assertFalse(cF.isEligible());
        assertEquals(RejectionReason.OUTSIDE_RADIUS, cF.getReason());
        
        long eligibleCount = candidates.stream().filter(DeliveryPartnerCandidate::isEligible).count();
        assertEquals(2, eligibleCount);
    }
}
