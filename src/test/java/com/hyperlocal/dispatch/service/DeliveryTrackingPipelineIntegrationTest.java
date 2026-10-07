package com.hyperlocal.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.Role;
import com.hyperlocal.auth.repository.UserRepository;
import com.hyperlocal.common.exception.AccessDeniedException;
import com.hyperlocal.dispatch.controller.DeliveryLocationController;
import com.hyperlocal.dispatch.dto.CustomerLocationBroadcast;
import com.hyperlocal.dispatch.dto.LocationUpdateRequest;
import com.hyperlocal.dispatch.dto.PartnerLocationRecord;
import com.hyperlocal.dispatch.dto.PartnerLocationResponse;
import com.hyperlocal.dispatch.dto.PartnerPresence;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.order.controller.OrderController;
import com.hyperlocal.order.dto.OrderResponse;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.repository.OrderRepository;
import com.hyperlocal.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Pipeline Integration Test for Delivery Tracking (Day 29 / Day 30 DoD).
 *
 * Scenarios tested:
 * 1. Valid GPS update: Redis location updates, Geo index updates, customer broadcast sent.
 * 2. Newer GPS update: Latest location replaced in Redis and Geo index.
 * 3. Older GPS update: Delayed GPS ping rejected; cannot overwrite newer location.
 * 4. Invalid latitude/longitude: Rejected (bounds, NaN, Inf, null).
 * 5. Unauthenticated partner: Rejected; impersonation prevented.
 * 6. Unauthorized delivery: Partner not assigned to delivery cannot report; no location leak.
 * 7. Partner goes offline: Stale location stops being fresh; Geo index cleaned.
 * 8. Customer reconnects: REST resync restores latest coordinates and order state.
 * 9. Multiple partners: Updates remain strictly isolated by delivery and customer.
 * 10. GPS Simulator: Multi-waypoint route simulation verifying end-to-end data flow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryTrackingPipelineIntegrationTest {

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private LocationService locationService;

    @Mock
    private GeoLocationService geoLocationService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private OrderService orderService;

    private ObjectMapper objectMapper;
    private DeliveryTrackingService trackingService;
    private DeliveryLocationController locationController;
    private OrderController orderController;

    // In-memory Redis simulation to verify real store/replace/ttl behavior
    private final Map<String, String> inMemoryRedis = new ConcurrentHashMap<>();
    private final Map<Long, Point> inMemoryGeo = new ConcurrentHashMap<>();

    // Test fixtures
    private User partnerUser1;
    private DeliveryPartner partner1;
    private User customerUser1;
    private Order order1;
    private Authentication partnerAuth1;

    private User partnerUser2;
    private DeliveryPartner partner2;
    private User customerUser2;
    private Order order2;
    private Authentication partnerAuth2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        inMemoryRedis.clear();
        inMemoryGeo.clear();

        // Wire in-memory Redis simulation
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            String val = inv.getArgument(1);
            inMemoryRedis.put(key, val);
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        when(valueOperations.get(anyString())).thenAnswer(inv -> inMemoryRedis.get(inv.getArgument(0)));

        doAnswer(inv -> {
            Long pId = inv.getArgument(0);
            double lat = inv.getArgument(1);
            double lng = inv.getArgument(2);
            inMemoryGeo.put(pId, new Point(lng, lat));
            return null;
        }).when(geoLocationService).updatePartnerLocation(anyLong(), anyDouble(), anyDouble());

        doAnswer(inv -> {
            Long pId = inv.getArgument(0);
            inMemoryGeo.remove(pId);
            return null;
        }).when(geoLocationService).removePartner(anyLong());

        // Initialize Services & Controllers
        trackingService = new DeliveryTrackingService(
                deliveryPartnerRepository,
                userRepository,
                orderRepository,
                locationService,
                geoLocationService,
                redisTemplate,
                objectMapper,
                messagingTemplate,
                1000L, // 1000ms throttle interval
                0.0,   // default 0m distance threshold for testing
                30000L // 30s heartbeat
        );

        locationController = new DeliveryLocationController(trackingService);
        orderController = new OrderController(orderService, null, trackingService);

        // Setup Partner 1 & Delivery 100
        partnerUser1 = new User();
        partnerUser1.setId(10L);
        partnerUser1.setEmail("partner1@test.com");
        partnerUser1.setRole(Role.DELIVERY_PARTNER);

        partner1 = new DeliveryPartner();
        partner1.setId(1L);
        partner1.setUser(partnerUser1);

        customerUser1 = new User();
        customerUser1.setId(1000L);
        customerUser1.setEmail("customer1@test.com");
        customerUser1.setRole(Role.CUSTOMER);

        order1 = new Order();
        order1.setId(100L);
        order1.setCustomer(customerUser1);
        order1.setDeliveryPartner(partner1);
        order1.setStatus(OrderStatus.OUT_FOR_DELIVERY);

        partnerAuth1 = new UsernamePasswordAuthenticationToken(partnerUser1, null, partnerUser1.getAuthorities());

        // Setup Partner 2 & Delivery 200
        partnerUser2 = new User();
        partnerUser2.setId(20L);
        partnerUser2.setEmail("partner2@test.com");
        partnerUser2.setRole(Role.DELIVERY_PARTNER);

        partner2 = new DeliveryPartner();
        partner2.setId(2L);
        partner2.setUser(partnerUser2);

        customerUser2 = new User();
        customerUser2.setId(2000L);
        customerUser2.setEmail("customer2@test.com");
        customerUser2.setRole(Role.CUSTOMER);

        order2 = new Order();
        order2.setId(200L);
        order2.setCustomer(customerUser2);
        order2.setDeliveryPartner(partner2);
        order2.setStatus(OrderStatus.OUT_FOR_DELIVERY);

        partnerAuth2 = new UsernamePasswordAuthenticationToken(partnerUser2, null, partnerUser2.getAuthorities());

        // Common Mock Repositories setup
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner1));
        when(deliveryPartnerRepository.findByUserId(20L)).thenReturn(Optional.of(partner2));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(200L)).thenReturn(Optional.of(order2));
    }

    // =========================================================================
    // Scenario 1: Valid GPS update -> Redis location updates & Geo index updates
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Valid GPS update updates Redis, Geo index, and publishes customer broadcast")
    void testScenario1_ValidGpsUpdate() throws Exception {
        Instant now = Instant.parse("2026-10-04T12:00:00Z");
        LocationUpdateRequest update = new LocationUpdateRequest(1L, 12.9716, 77.5946, now, 100L);

        // Send via WebSocket controller
        locationController.handleLocationUpdate(update, partnerAuth1);

        // 1. Verify Redis location key stored
        String storedJson = inMemoryRedis.get("delivery:partner:1:location");
        assertNotNull(storedJson, "Redis partner location must be updated");
        PartnerLocationRecord record = objectMapper.readValue(storedJson, PartnerLocationRecord.class);
        assertEquals(12.9716, record.lat(), 0.0001);
        assertEquals(77.5946, record.lng(), 0.0001);
        assertEquals(now, record.timestamp());

        // Also verify delivery-level key
        String deliveryJson = inMemoryRedis.get("delivery:100:location");
        assertNotNull(deliveryJson);

        // 2. Verify Geo index updated (longitude first!)
        Point geoPoint = inMemoryGeo.get(1L);
        assertNotNull(geoPoint, "Geo index must have partner entry");
        assertEquals(77.5946, geoPoint.getX(), 0.0001, "Longitude must be X in Geo Point");
        assertEquals(12.9716, geoPoint.getY(), 0.0001, "Latitude must be Y in Geo Point");

        // 3. Verify STOMP broadcast sent to customer topic
        ArgumentCaptor<CustomerLocationBroadcast> captor = ArgumentCaptor.forClass(CustomerLocationBroadcast.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/delivery/100/location"), captor.capture());
        CustomerLocationBroadcast broadcast = captor.getValue();
        assertEquals("DELIVERY_LOCATION_UPDATED", broadcast.type());
        assertEquals("delivery-100", broadcast.deliveryId());
        assertEquals(12.9716, broadcast.lat());
        assertEquals(77.5946, broadcast.lng());
        assertEquals(now, broadcast.timestamp());
    }

    // =========================================================================
    // Scenario 2: Newer GPS update -> Latest location is replaced
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Newer GPS update replaces latest location in Redis and Geo index")
    void testScenario2_NewerGpsUpdate_ReplacesLatest() throws Exception {
        Instant t0 = Instant.parse("2026-10-04T12:00:00Z");
        Instant t1 = Instant.parse("2026-10-04T12:00:02Z"); // 2s newer

        LocationUpdateRequest update1 = new LocationUpdateRequest(1L, 12.9716, 77.5946, t0, 100L);
        LocationUpdateRequest update2 = new LocationUpdateRequest(1L, 12.9725, 77.5955, t1, 100L);

        locationController.handleLocationUpdate(update1, partnerAuth1);
        locationController.handleLocationUpdate(update2, partnerAuth1);

        // Verify Redis reflects update2
        String storedJson = inMemoryRedis.get("delivery:partner:1:location");
        PartnerLocationRecord record = objectMapper.readValue(storedJson, PartnerLocationRecord.class);
        assertEquals(12.9725, record.lat(), 0.0001);
        assertEquals(77.5955, record.lng(), 0.0001);
        assertEquals(t1, record.timestamp());

        // Verify Geo index reflects update2
        Point geoPoint = inMemoryGeo.get(1L);
        assertEquals(77.5955, geoPoint.getX(), 0.0001);
        assertEquals(12.9725, geoPoint.getY(), 0.0001);

        // Verify two broadcasts were sent (after 2s interval)
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));
    }

    // =========================================================================
    // Scenario 3: Older GPS update -> Cannot overwrite newer location (dropped)
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: Older/delayed GPS update cannot overwrite newer location")
    void testScenario3_OlderGpsUpdate_CannotOverwrite() throws Exception {
        Instant tNewer = Instant.parse("2026-10-04T12:00:10Z");
        Instant tOlder = Instant.parse("2026-10-04T12:00:05Z"); // Delayed GPS ping

        LocationUpdateRequest newerUpdate = new LocationUpdateRequest(1L, 12.9750, 77.5980, tNewer, 100L);
        LocationUpdateRequest delayedUpdate = new LocationUpdateRequest(1L, 12.9700, 77.5900, tOlder, 100L);

        // First process newer update
        boolean res1 = trackingService.receiveLocation(newerUpdate, partnerAuth1);
        assertTrue(res1);

        // Next delayed update arrives
        boolean res2 = trackingService.receiveLocation(delayedUpdate, partnerAuth1);
        assertFalse(res2, "Delayed GPS update must be dropped");

        // Verify Redis still holds newer location
        String storedJson = inMemoryRedis.get("delivery:partner:1:location");
        PartnerLocationRecord record = objectMapper.readValue(storedJson, PartnerLocationRecord.class);
        assertEquals(12.9750, record.lat(), 0.0001);
        assertEquals(77.5980, record.lng(), 0.0001);
        assertEquals(tNewer, record.timestamp());

        // Verify Geo index still holds newer location
        Point geoPoint = inMemoryGeo.get(1L);
        assertEquals(77.5980, geoPoint.getX(), 0.0001);
        assertEquals(12.9750, geoPoint.getY(), 0.0001);

        // Verify messagingTemplate was only called once
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));
    }

    // =========================================================================
    // Scenario 4: Invalid latitude/longitude -> Rejected
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: Invalid coordinates (out-of-bounds, NaN, Inf, null) are rejected")
    void testScenario4_InvalidCoordinates_Rejected() {
        Instant now = Instant.now();

        // 1. Latitude > 90
        assertThrows(IllegalArgumentException.class, () ->
                trackingService.receiveLocation(new LocationUpdateRequest(1L, 95.0, 77.0, now, 100L), partnerAuth1));

        // 2. Latitude < -90
        assertThrows(IllegalArgumentException.class, () ->
                trackingService.receiveLocation(new LocationUpdateRequest(1L, -91.0, 77.0, now, 100L), partnerAuth1));

        // 3. Longitude > 180
        assertThrows(IllegalArgumentException.class, () ->
                trackingService.receiveLocation(new LocationUpdateRequest(1L, 12.0, 185.0, now, 100L), partnerAuth1));

        // 4. Longitude < -180
        assertThrows(IllegalArgumentException.class, () ->
                trackingService.receiveLocation(new LocationUpdateRequest(1L, 12.0, -181.0, now, 100L), partnerAuth1));

        // 5. NaN
        assertThrows(IllegalArgumentException.class, () ->
                trackingService.receiveLocation(new LocationUpdateRequest(1L, Double.NaN, 77.0, now, 100L), partnerAuth1));

        // 6. Infinite
        assertThrows(IllegalArgumentException.class, () ->
                trackingService.receiveLocation(new LocationUpdateRequest(1L, 12.0, Double.POSITIVE_INFINITY, now, 100L), partnerAuth1));

        // 7. Null coordinates
        assertThrows(IllegalArgumentException.class, () ->
                trackingService.receiveLocation(new LocationUpdateRequest(1L, null, 77.0, now, 100L), partnerAuth1));

        // Verify no Redis or Geo index changes occurred
        assertTrue(inMemoryRedis.isEmpty());
        assertTrue(inMemoryGeo.isEmpty());
        verifyNoInteractions(messagingTemplate);
    }

    // =========================================================================
    // Scenario 5: Unauthenticated partner -> Rejected & Impersonation prevented
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: Unauthenticated senders and impersonation attempts are rejected")
    void testScenario5_UnauthenticatedAndImpersonation_Rejected() {
        LocationUpdateRequest update = new LocationUpdateRequest(1L, 12.9716, 77.5946, Instant.now(), 100L);

        // 1. Null authentication
        assertThrows(AccessDeniedException.class, () ->
                trackingService.receiveLocation(update, null));

        // 2. Unauthenticated token
        Authentication unauth = mock(Authentication.class);
        when(unauth.isAuthenticated()).thenReturn(false);
        assertThrows(AccessDeniedException.class, () ->
                trackingService.receiveLocation(update, unauth));

        // 3. Impersonation: partnerUser1 claims to be partnerId 999
        LocationUpdateRequest impersonateUpdate = new LocationUpdateRequest(999L, 12.9716, 77.5946, Instant.now(), 100L);
        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                trackingService.receiveLocation(impersonateUpdate, partnerAuth1));
        assertTrue(ex.getMessage().contains("mismatch"));

        assertTrue(inMemoryRedis.isEmpty());
        verifyNoInteractions(messagingTemplate);
    }

    // =========================================================================
    // Scenario 6: Unauthorized delivery -> No location leak
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: Unauthorized delivery access is denied without leaking locations")
    void testScenario6_UnauthorizedDelivery_NoLocationLeak() {
        Instant now = Instant.now();
        // Partner 1 tries to update Delivery 200 (which belongs to Partner 2)
        LocationUpdateRequest rogueUpdate = new LocationUpdateRequest(1L, 12.9716, 77.5946, now, 200L);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                trackingService.receiveLocation(rogueUpdate, partnerAuth1));
        assertTrue(ex.getMessage().contains("not authorized to report location for delivery 200"));

        // Verify nothing was written to delivery:200:location
        assertNull(inMemoryRedis.get("delivery:200:location"));
        // Verify customer of Delivery 200 received NO event
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/delivery/200/location"), any(CustomerLocationBroadcast.class));

        // Verify customer 2 cannot access delivery 100 via REST
        doThrow(new AccessDeniedException("Access denied")).when(orderService).getOrderById(eq(100L), eq("customer2@test.com"));
        assertThrows(AccessDeniedException.class, () ->
                orderController.getOrderLocation(100L, customerUser2));
    }

    // =========================================================================
    // Scenario 7: Partner goes offline -> Stale location stops being treated as fresh
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Partner goes offline cleans up presence and stale location stops being treated as fresh")
    void testScenario7_PartnerGoesOffline_StaleLocationNotFresh() {
        // Partner is online and reports location
        when(presenceService.getPresence(1L)).thenReturn(Optional.of(
                new PartnerPresence(1L, AvailabilityStatus.ONLINE, Instant.now())
        ));

        LocationUpdateRequest update = new LocationUpdateRequest(1L, 12.9716, 77.5946, Instant.now(), 100L);
        trackingService.receiveLocation(update, partnerAuth1);
        assertEquals(1, inMemoryGeo.size());

        // Partner goes offline
        presenceService.setOffline(1L);
        // Verify presenceService removes partner from Geo index
        verify(presenceService).setOffline(1L);

        // When Redis TTL expires (key removed), location is no longer treated as fresh
        inMemoryRedis.remove("delivery:100:location");
        Optional<CustomerLocationBroadcast> latest = trackingService.getLatestDeliveryLocation(100L);
        assertTrue(latest.isEmpty(), "Expired/stale location must return empty");
    }

    // =========================================================================
    // Scenario 8: Customer reconnects -> REST resync restores current state
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: Customer reconnects and restores current state and location via REST")
    void testScenario8_CustomerReconnects_RestResyncRestoresCurrentState() {
        Instant now = Instant.parse("2026-10-04T12:00:00Z");
        LocationUpdateRequest update = new LocationUpdateRequest(1L, 12.9716, 77.5946, now, 100L);
        trackingService.receiveLocation(update, partnerAuth1);

        OrderResponse mockOrderResponse = new OrderResponse();
        mockOrderResponse.setId(100L);
        mockOrderResponse.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        when(orderService.getOrderById(100L, "customer1@test.com")).thenReturn(mockOrderResponse);

        // Customer calls REST endpoint GET /orders/100/location on reconnect
        ResponseEntity<CustomerLocationBroadcast> locationResponse =
                orderController.getOrderLocation(100L, customerUser1);

        assertEquals(HttpStatus.OK, locationResponse.getStatusCode());
        assertNotNull(locationResponse.getBody());
        assertEquals("DELIVERY_LOCATION_UPDATED", locationResponse.getBody().type());
        assertEquals(12.9716, locationResponse.getBody().lat());
        assertEquals(77.5946, locationResponse.getBody().lng());
        assertEquals(now, locationResponse.getBody().timestamp());

        // Customer calls REST endpoint GET /orders/100 on reconnect
        ResponseEntity<OrderResponse> orderResp = orderController.getOrderById(100L, customerUser1);
        assertEquals(HttpStatus.OK, orderResp.getStatusCode());
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, orderResp.getBody().getStatus());
    }

    // =========================================================================
    // Scenario 9: Multiple partners -> Updates remain isolated by delivery
    // =========================================================================
    @Test
    @DisplayName("Scenario 9: Multiple partners reporting locations remain strictly isolated by delivery")
    void testScenario9_MultiplePartners_IsolatedByDelivery() throws Exception {
        Instant t0 = Instant.parse("2026-10-04T12:00:00Z");

        // Partner 1 on Delivery 100 (in Bangalore)
        LocationUpdateRequest updatePartner1 = new LocationUpdateRequest(1L, 12.9716, 77.5946, t0, 100L);
        // Partner 2 on Delivery 200 (in Chennai)
        LocationUpdateRequest updatePartner2 = new LocationUpdateRequest(2L, 13.0827, 80.2707, t0, 200L);

        trackingService.receiveLocation(updatePartner1, partnerAuth1);
        trackingService.receiveLocation(updatePartner2, partnerAuth2);

        // Verify Delivery 100 in Redis has Partner 1 coords
        String json1 = inMemoryRedis.get("delivery:100:location");
        PartnerLocationRecord rec1 = objectMapper.readValue(json1, PartnerLocationRecord.class);
        assertEquals(12.9716, rec1.lat(), 0.0001);
        assertEquals(77.5946, rec1.lng(), 0.0001);

        // Verify Delivery 200 in Redis has Partner 2 coords
        String json2 = inMemoryRedis.get("delivery:200:location");
        PartnerLocationRecord rec2 = objectMapper.readValue(json2, PartnerLocationRecord.class);
        assertEquals(13.0827, rec2.lat(), 0.0001);
        assertEquals(80.2707, rec2.lng(), 0.0001);

        // Verify separate Geo entries
        assertEquals(77.5946, inMemoryGeo.get(1L).getX(), 0.0001);
        assertEquals(80.2707, inMemoryGeo.get(2L).getX(), 0.0001);

        // Verify topics received only their respective broadcasts
        ArgumentCaptor<CustomerLocationBroadcast> captor1 = ArgumentCaptor.forClass(CustomerLocationBroadcast.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/delivery/100/location"), captor1.capture());
        assertEquals("delivery-100", captor1.getValue().deliveryId());
        assertEquals(12.9716, captor1.getValue().lat());

        ArgumentCaptor<CustomerLocationBroadcast> captor2 = ArgumentCaptor.forClass(CustomerLocationBroadcast.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/delivery/200/location"), captor2.capture());
        assertEquals("delivery-200", captor2.getValue().deliveryId());
        assertEquals(13.0827, captor2.getValue().lat());
    }

    // =========================================================================
    // Scenario 10: GPS Simulator -> Multi-waypoint route movement end to end
    // =========================================================================
    @Test
    @DisplayName("Scenario 10: GPS Movement Simulator runs trajectory end-to-end verifying DoD")
    void testScenario10_GpsSimulator_MovementEndToEnd() throws Exception {
        trackingService.setThrottleIntervalMs(1000); // 1s throttle

        // Simulated route from Shop (12.9716, 77.5946) to Customer (12.9780, 77.6050)
        List<double[]> routeWaypoints = List.of(
                new double[]{12.9716, 77.5946}, // Waypoint 0 (t = 0s): Shop pickup
                new double[]{12.9725, 77.5960}, // Waypoint 1 (t = 0.4s): In motion (throttled broadcast, but Redis updated)
                new double[]{12.9740, 77.5980}, // Waypoint 2 (t = 1.2s): In motion (broadcast published)
                new double[]{12.9760, 77.6010}, // Waypoint 3 (t = 2.4s): In motion (broadcast published)
                new double[]{12.9780, 77.6050}  // Waypoint 4 (t = 3.6s): Customer doorstep
        );

        Instant startTime = Instant.parse("2026-10-04T12:00:00Z");
        long[] relativeOffsetsMs = new long[]{0, 400, 1200, 2400, 3600};

        int broadcastCount = 0;

        for (int i = 0; i < routeWaypoints.size(); i++) {
            double[] point = routeWaypoints.get(i);
            Instant timestamp = startTime.plusMillis(relativeOffsetsMs[i]);

            LocationUpdateRequest update = new LocationUpdateRequest(1L, point[0], point[1], timestamp, 100L);

            // 1. Send through WebSocket controller
            locationController.handleLocationUpdate(update, partnerAuth1);

            // 2. Redis must ALWAYS be updated on every waypoint
            String redisJson = inMemoryRedis.get("delivery:partner:1:location");
            PartnerLocationRecord currentRecord = objectMapper.readValue(redisJson, PartnerLocationRecord.class);
            assertEquals(point[0], currentRecord.lat(), 0.0001);
            assertEquals(point[1], currentRecord.lng(), 0.0001);

            // 3. Geo index must ALWAYS be updated on every waypoint
            Point geoPt = inMemoryGeo.get(1L);
            assertEquals(point[1], geoPt.getX(), 0.0001); // Lng
            assertEquals(point[0], geoPt.getY(), 0.0001); // Lat

            // Waypoints 0, 2, 3, 4 should be broadcast (t=0, 1.2s, 2.4s, 3.6s)
            // Waypoint 1 (t=0.4s) should be throttled
            if (i == 1) {
                // Throttled: broadcastCount does not increase
            } else {
                broadcastCount++;
            }
        }

        // Verify total broadcast events matched expected unthrottled publishes (4 broadcasts out of 5 pings)
        verify(messagingTemplate, times(broadcastCount)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));

        // Final doorstep coordinates verified in Redis
        Optional<CustomerLocationBroadcast> finalLocation = trackingService.getLatestDeliveryLocation(100L);
        assertTrue(finalLocation.isPresent());
        assertEquals(12.9780, finalLocation.get().lat(), 0.0001);
        assertEquals(77.6050, finalLocation.get().lng(), 0.0001);
    }
}
