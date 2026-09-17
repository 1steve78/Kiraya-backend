package com.hyperlocal.service;

import com.hyperlocal.dto.NearbyPartnerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.GeoShape;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoLocationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    @Captor
    private ArgumentCaptor<Point> pointCaptor;

    @Captor
    private ArgumentCaptor<GeoReference<String>> geoReferenceCaptor;

    @Captor
    private ArgumentCaptor<GeoShape> geoShapeCaptor;

    @Captor
    private ArgumentCaptor<RedisGeoCommands.GeoSearchCommandArgs> argsCaptor;

    private GeoLocationService geoLocationService;

    private static final String GEO_KEY = "delivery:partners:geo";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        geoLocationService = new GeoLocationService(redisTemplate);
    }

    @Test
    @DisplayName("Should add partner to Geo index")
    void testAddPartnerLocation() {
        Long partnerId = 101L;
        double lat = 22.5726;
        double lng = 88.3639;

        geoLocationService.addPartnerLocation(partnerId, lat, lng);

        verify(geoOperations).add(eq(GEO_KEY), pointCaptor.capture(), eq("partner:101"));
        Point capturedPoint = pointCaptor.getValue();
        assertEquals(lng, capturedPoint.getX());
        assertEquals(lat, capturedPoint.getY());
    }

    @Test
    @DisplayName("Should update partner in Geo index")
    void testUpdatePartnerLocation() {
        Long partnerId = 101L;
        double newLat = 22.5730;
        double newLng = 88.3644;

        geoLocationService.updatePartnerLocation(partnerId, newLat, newLng);

        verify(geoOperations).add(eq(GEO_KEY), pointCaptor.capture(), eq("partner:101"));
        Point capturedPoint = pointCaptor.getValue();
        assertEquals(newLng, capturedPoint.getX());
        assertEquals(newLat, capturedPoint.getY());
    }

    @Test
    @DisplayName("Should remove offline partner from Geo index")
    void testRemovePartner() {
        Long partnerId = 101L;

        geoLocationService.removePartner(partnerId);

        verify(geoOperations).remove(GEO_KEY, "partner:101");
    }

    @Test
    @DisplayName("Should remove multiple offline partners from Geo index")
    void testRemovePartners() {
        List<String> partnerIds = List.of("101", "102");

        geoLocationService.removePartners(partnerIds);

        verify(geoOperations).remove(GEO_KEY, "partner:101", "partner:102");
    }

    @Test
    @DisplayName("Should find nearby partners")
    void testFindNearbyPartners() {
        double searchLat = 22.5740;
        double searchLng = 88.3650;
        double radius = 3.0;

        GeoResult<RedisGeoCommands.GeoLocation<String>> result1 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("partner:101", new Point(88.3639, 22.5726)),
                new Distance(0.32)
        );
        GeoResult<RedisGeoCommands.GeoLocation<String>> result2 = new GeoResult<>(
                new RedisGeoCommands.GeoLocation<>("partner:102", new Point(88.3700, 22.5800)),
                new Distance(1.14)
        );
        
        GeoResults<RedisGeoCommands.GeoLocation<String>> mockResults = new GeoResults<>(List.of(result1, result2));

        when(geoOperations.search(
                eq(GEO_KEY),
                any(GeoReference.class),
                any(GeoShape.class),
                any(RedisGeoCommands.GeoSearchCommandArgs.class)
        )).thenReturn(mockResults);

        List<NearbyPartnerResponse> responses = geoLocationService.findNearbyPartners(searchLat, searchLng, radius);

        assertEquals(2, responses.size());
        assertEquals(101L, responses.get(0).partnerId());
        assertEquals(0.32, responses.get(0).distanceKm());
        assertEquals(102L, responses.get(1).partnerId());
        assertEquals(1.14, responses.get(1).distanceKm());

        verify(geoOperations).search(eq(GEO_KEY), geoReferenceCaptor.capture(), geoShapeCaptor.capture(), argsCaptor.capture());
    }

    @Test
    @DisplayName("Should handle empty results from search")
    void testFindNearbyPartnersEmpty() {
        double searchLat = 22.5740;
        double searchLng = 88.3650;
        double radius = 3.0;

        GeoResults<RedisGeoCommands.GeoLocation<String>> mockResults = new GeoResults<>(List.of());

        when(geoOperations.search(
                eq(GEO_KEY),
                any(GeoReference.class),
                any(GeoShape.class),
                any(RedisGeoCommands.GeoSearchCommandArgs.class)
        )).thenReturn(mockResults);

        List<NearbyPartnerResponse> responses = geoLocationService.findNearbyPartners(searchLat, searchLng, radius);

        assertTrue(responses.isEmpty());
    }
}
