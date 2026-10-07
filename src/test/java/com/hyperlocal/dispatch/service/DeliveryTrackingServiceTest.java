package com.hyperlocal.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.Role;
import com.hyperlocal.auth.repository.UserRepository;
import com.hyperlocal.common.exception.AccessDeniedException;
import com.hyperlocal.dispatch.dto.CustomerLocationBroadcast;
import com.hyperlocal.dispatch.dto.LocationDto;
import com.hyperlocal.dispatch.dto.LocationUpdateRequest;
import com.hyperlocal.dispatch.dto.PartnerLocationResponse;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryTrackingServiceTest {

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
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ObjectMapper objectMapper;
    private DeliveryTrackingService trackingService;

    private User partnerUser;
    private DeliveryPartner partner;
    private Order activeOrder;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        trackingService = new DeliveryTrackingService(
                deliveryPartnerRepository,
                userRepository,
                orderRepository,
                locationService,
                geoLocationService,
                redisTemplate,
                objectMapper,
                messagingTemplate
        );

        partnerUser = new User();
        partnerUser.setId(10L);
        partnerUser.setEmail("partner@test.com");
        partnerUser.setRole(Role.DELIVERY_PARTNER);

        partner = new DeliveryPartner();
        partner.setId(1L);
        partner.setUser(partnerUser);
        partner.setAvailable(false);

        User customerUser = new User();
        customerUser.setId(20L);
        customerUser.setEmail("customer@test.com");

        activeOrder = new Order();
        activeOrder.setId(100L);
        activeOrder.setCustomer(customerUser);
        activeOrder.setDeliveryPartner(partner);
        activeOrder.setStatus(OrderStatus.OUT_FOR_DELIVERY);

        authentication = new UsernamePasswordAuthenticationToken(partnerUser, null, partnerUser.getAuthorities());
    }

    @Test
    @DisplayName("Successfully track location with explicit delivery ID")
    void testReceiveLocation_Success_WithDeliveryId() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Instant now = Instant.now();
        LocationUpdateRequest update = new LocationUpdateRequest(1L, 12.9716, 77.5946, now, 100L);

        boolean result = trackingService.receiveLocation(update, authentication);

        assertTrue(result);
        verify(valueOperations).set(eq("delivery:partner:1:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(valueOperations).set(eq("partner:1:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(valueOperations).set(eq("delivery:100:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(geoLocationService).updatePartnerLocation(1L, 12.9716, 77.5946);

        ArgumentCaptor<CustomerLocationBroadcast> captor = ArgumentCaptor.forClass(CustomerLocationBroadcast.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/delivery/100/location"), captor.capture());
        verify(messagingTemplate).convertAndSendToUser(eq("customer@test.com"), eq("/queue/delivery-location"), eq(captor.getValue()));

        CustomerLocationBroadcast broadcast = captor.getValue();
        assertEquals("DELIVERY_LOCATION_UPDATED", broadcast.type());
        assertEquals("delivery-100", broadcast.deliveryId());
        assertEquals(100L, broadcast.getDeliveryIdAsLong());
        assertEquals(12.9716, broadcast.lat());
        assertEquals(77.5946, broadcast.lng());
        assertEquals(now, broadcast.timestamp());
    }

    @Test
    @DisplayName("Successfully track location by auto-discovering active delivery")
    void testReceiveLocation_Success_WithoutDeliveryId() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findActiveOrdersForPartner(eq(1L), anyList())).thenReturn(List.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LocationUpdateRequest update = new LocationUpdateRequest(12.9716, 77.5946, Instant.now());

        boolean result = trackingService.receiveLocation(update, authentication);

        assertTrue(result);
        verify(valueOperations).set(eq("delivery:partner:1:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(valueOperations).set(eq("partner:1:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(valueOperations).set(eq("delivery:100:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(geoLocationService).updatePartnerLocation(1L, 12.9716, 77.5946);
        verify(messagingTemplate).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));
        verify(messagingTemplate).convertAndSendToUser(eq("customer@test.com"), eq("/queue/delivery-location"), any(CustomerLocationBroadcast.class));
    }

    @Test
    @DisplayName("Reject invalid coordinates: latitude > 90")
    void testReceiveLocation_InvalidCoordinates_LatTooHigh() {
        LocationUpdateRequest update = new LocationUpdateRequest(95.0, 77.5946, Instant.now());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> trackingService.receiveLocation(update, authentication));
        assertTrue(ex.getMessage().contains("Invalid coordinates"));

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(geoLocationService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Reject invalid coordinates: latitude < -90")
    void testReceiveLocation_InvalidCoordinates_LatTooLow() {
        LocationUpdateRequest update = new LocationUpdateRequest(-90.1, 77.5946, Instant.now());

        assertThrows(IllegalArgumentException.class,
                () -> trackingService.receiveLocation(update, authentication));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Reject invalid coordinates: longitude > 180")
    void testReceiveLocation_InvalidCoordinates_LngTooHigh() {
        LocationUpdateRequest update = new LocationUpdateRequest(12.0, 180.5, Instant.now());

        assertThrows(IllegalArgumentException.class,
                () -> trackingService.receiveLocation(update, authentication));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Reject invalid coordinates: longitude < -180")
    void testReceiveLocation_InvalidCoordinates_LngTooLow() {
        LocationUpdateRequest update = new LocationUpdateRequest(12.0, -181.0, Instant.now());

        assertThrows(IllegalArgumentException.class,
                () -> trackingService.receiveLocation(update, authentication));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Reject invalid coordinates: NaN values")
    void testReceiveLocation_InvalidCoordinates_NaN() {
        LocationUpdateRequest update = new LocationUpdateRequest(Double.NaN, 77.0, Instant.now());

        assertThrows(IllegalArgumentException.class,
                () -> trackingService.receiveLocation(update, authentication));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Reject invalid coordinates: Infinite values")
    void testReceiveLocation_InvalidCoordinates_Infinite() {
        LocationUpdateRequest update = new LocationUpdateRequest(Double.POSITIVE_INFINITY, 77.0, Instant.now());

        assertThrows(IllegalArgumentException.class,
                () -> trackingService.receiveLocation(update, authentication));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Reject null coordinates")
    void testReceiveLocation_InvalidCoordinates_Null() {
        LocationUpdateRequest update = new LocationUpdateRequest(null, 77.0, Instant.now());

        assertThrows(IllegalArgumentException.class,
                () -> trackingService.receiveLocation(update, authentication));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Reject unauthenticated sender when authentication is null")
    void testReceiveLocation_UnauthenticatedSender_NullAuth() {
        LocationUpdateRequest update = new LocationUpdateRequest(12.9716, 77.5946, Instant.now());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> trackingService.receiveLocation(update, null));
        assertTrue(ex.getMessage().contains("Unauthenticated"));
    }

    @Test
    @DisplayName("Reject sender when not authenticated")
    void testReceiveLocation_UnauthenticatedSender_NotAuth() {
        Authentication unauth = mock(Authentication.class);
        when(unauth.isAuthenticated()).thenReturn(false);

        LocationUpdateRequest update = new LocationUpdateRequest(12.9716, 77.5946, Instant.now());

        assertThrows(AccessDeniedException.class,
                () -> trackingService.receiveLocation(update, unauth));
    }

    @Test
    @DisplayName("Reject sender when user is not a delivery partner")
    void testReceiveLocation_SenderNotDeliveryPartner() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.empty());

        LocationUpdateRequest update = new LocationUpdateRequest(12.9716, 77.5946, Instant.now());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> trackingService.receiveLocation(update, authentication));
        assertTrue(ex.getMessage().contains("not a registered delivery partner"));
    }

    @Test
    @DisplayName("Reject impersonation attempt when client partnerId doesn't match session")
    void testReceiveLocation_ImpersonationAttempt() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));

        // Client claims to be partner 999, but session is partner 1
        LocationUpdateRequest update = new LocationUpdateRequest(999L, 12.9716, 77.5946, Instant.now());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> trackingService.receiveLocation(update, authentication));
        assertTrue(ex.getMessage().contains("mismatch"));
    }

    @Test
    @DisplayName("Reject update when partner is not assigned to the specified delivery")
    void testReceiveLocation_UnauthorizedForDelivery_WrongPartner() {
        DeliveryPartner otherPartner = new DeliveryPartner();
        otherPartner.setId(2L);

        Order otherOrder = new Order();
        otherOrder.setId(200L);
        otherOrder.setDeliveryPartner(otherPartner);
        otherOrder.setStatus(OrderStatus.OUT_FOR_DELIVERY);

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(200L)).thenReturn(Optional.of(otherOrder));

        LocationUpdateRequest update = new LocationUpdateRequest(null, 12.9716, 77.5946, Instant.now(), 200L);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> trackingService.receiveLocation(update, authentication));
        assertTrue(ex.getMessage().contains("not authorized to report location"));
    }

    @Test
    @DisplayName("Reject update when delivery is not currently active")
    void testReceiveLocation_DeliveryNotActive() {
        activeOrder.setStatus(OrderStatus.DELIVERED);

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));

        LocationUpdateRequest update = new LocationUpdateRequest(null, 12.9716, 77.5946, Instant.now(), 100L);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> trackingService.receiveLocation(update, authentication));
        assertTrue(ex.getMessage().contains("not currently active"));
    }

    @Test
    @DisplayName("Reject update when partner has no active deliveries and no deliveryId specified")
    void testReceiveLocation_NoActiveDeliveryFound() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findActiveOrdersForPartner(eq(1L), anyList())).thenReturn(List.of());

        LocationUpdateRequest update = new LocationUpdateRequest(12.9716, 77.5946, Instant.now());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> trackingService.receiveLocation(update, authentication));
        assertTrue(ex.getMessage().contains("No active delivery found"));
    }

    @Test
    @DisplayName("Drop out-of-order delayed GPS update without overwriting Redis or publishing")
    void testReceiveLocation_OutOfOrder_DelayedUpdateDropped() {
        Instant acceptedTimestamp = Instant.parse("2026-10-04T12:00:10Z");
        Instant olderDelayedTimestamp = Instant.parse("2026-10-04T12:00:05Z");

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.of(
                new PartnerLocationResponse(1L, 12.9720, 77.5950, acceptedTimestamp)
        ));

        LocationUpdateRequest delayedUpdate = new LocationUpdateRequest(1L, 12.9700, 77.5900, olderDelayedTimestamp, 100L);

        boolean result = trackingService.receiveLocation(delayedUpdate, authentication);

        assertFalse(result, "Out-of-order update must be rejected (return false)");
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(geoLocationService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Accept in-order newer GPS update and update Redis and Geo index")
    void testReceiveLocation_InOrder_NewerUpdateAccepted() {
        Instant olderTimestamp = Instant.parse("2026-10-04T12:00:05Z");
        Instant newerTimestamp = Instant.parse("2026-10-04T12:00:10Z");

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.of(
                new PartnerLocationResponse(1L, 12.9700, 77.5900, olderTimestamp)
        ));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        LocationUpdateRequest newerUpdate = new LocationUpdateRequest(1L, 12.9720, 77.5950, newerTimestamp, 100L);

        boolean result = trackingService.receiveLocation(newerUpdate, authentication);

        assertTrue(result, "In-order update must be accepted");
        verify(valueOperations).set(eq("delivery:partner:1:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(valueOperations).set(eq("partner:1:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(valueOperations).set(eq("delivery:100:location"), anyString(), eq(Duration.ofSeconds(30)));
        verify(geoLocationService).updatePartnerLocation(1L, 12.9720, 77.5950);
        verify(messagingTemplate).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));
    }

    @Test
    @DisplayName("Receive location using LocationDto wrapper")
    void testReceiveLocation_WithLocationDto() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        Instant now = Instant.now();
        LocationDto dto = new LocationDto(1L, 12.9716, 77.5946, now, 100L);

        boolean result = trackingService.receiveLocation(dto, authentication);

        assertTrue(result);
        verify(geoLocationService).updatePartnerLocation(1L, 12.9716, 77.5946);
    }

    @Test
    @DisplayName("Drop delayed GPS update when delivery-level location is newer")
    void testReceiveLocation_OutOfOrder_DeliveryLocationTimestampNewer() throws Exception {
        Instant newerDeliveryTs = Instant.parse("2026-10-04T12:00:15Z");
        Instant olderGpsTs = Instant.parse("2026-10-04T12:00:10Z");

        LocationUpdateRequest existingDeliveryLocation = new LocationUpdateRequest(1L, 12.9750, 77.5960, newerDeliveryTs, 100L);
        String deliveryJson = objectMapper.writeValueAsString(existingDeliveryLocation);

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("delivery:100:location")).thenReturn(deliveryJson);

        LocationUpdateRequest delayedUpdate = new LocationUpdateRequest(1L, 12.9700, 77.5900, olderGpsTs, 100L);

        boolean result = trackingService.receiveLocation(delayedUpdate, authentication);

        assertFalse(result, "Delayed update must be rejected when delivery location is newer");
        verify(valueOperations, never()).set(eq("partner:1:location"), anyString(), any());
        verify(valueOperations, never()).set(eq("delivery:100:location"), anyString(), any());
        verifyNoInteractions(geoLocationService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Throttling: Suppress customer broadcast within interval while still updating Redis and Geo")
    void testReceiveLocation_Throttling_WithinInterval() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        trackingService.setThrottleIntervalMs(1000); // 1 second interval
        trackingService.setThrottleMinDistanceMeters(0.0);

        Instant t0 = Instant.parse("2026-10-04T12:00:00.000Z");
        Instant t500ms = Instant.parse("2026-10-04T12:00:00.500Z");
        Instant t1200ms = Instant.parse("2026-10-04T12:00:01.200Z");

        LocationUpdateRequest update1 = new LocationUpdateRequest(1L, 12.9716, 77.5946, t0, 100L);
        LocationUpdateRequest update2 = new LocationUpdateRequest(1L, 12.9717, 77.5947, t500ms, 100L);
        LocationUpdateRequest update3 = new LocationUpdateRequest(1L, 12.9719, 77.5949, t1200ms, 100L);

        // First update at t0
        boolean res1 = trackingService.receiveLocation(update1, authentication);
        assertTrue(res1);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));

        // Second update at t+500ms (within 1s throttle interval)
        boolean res2 = trackingService.receiveLocation(update2, authentication);
        assertTrue(res2, "Valid location update must be accepted even when throttled");
        // Verify Redis and Geo are updated for update2
        verify(geoLocationService).updatePartnerLocation(1L, 12.9717, 77.5947);
        // Verify messagingTemplate was NOT called again (still 1 call)
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));

        // Third update at t+1200ms (after 1s throttle interval)
        boolean res3 = trackingService.receiveLocation(update3, authentication);
        assertTrue(res3);
        verify(geoLocationService).updatePartnerLocation(1L, 12.9719, 77.5949);
        // Verify messagingTemplate was called for update3 (total 2 calls)
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));
    }

    @Test
    @DisplayName("Throttling: Enforce distance threshold when configured")
    void testReceiveLocation_Throttling_DistanceBased() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        trackingService.setThrottleIntervalMs(1000); // 1 second
        trackingService.setThrottleMinDistanceMeters(20.0); // 20 meters required
        trackingService.setHeartbeatIntervalMs(30000); // 30s heartbeat

        Instant t0 = Instant.parse("2026-10-04T12:00:00.000Z");
        Instant t1500ms = Instant.parse("2026-10-04T12:00:01.500Z");
        Instant t2000ms = Instant.parse("2026-10-04T12:00:02.000Z");

        LocationUpdateRequest update1 = new LocationUpdateRequest(1L, 12.97160, 77.59460, t0, 100L);
        // Minimal movement (approx 1 meter)
        LocationUpdateRequest updateStationary = new LocationUpdateRequest(1L, 12.97161, 77.59461, t1500ms, 100L);
        // Significant movement (~150 meters)
        LocationUpdateRequest updateMoved = new LocationUpdateRequest(1L, 12.97300, 77.59500, t2000ms, 100L);

        // Update 1: First update, published
        assertTrue(trackingService.receiveLocation(update1, authentication));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));

        // Update 2: 1.5s elapsed (time exceeded), but only moved ~1m (< 20m) -> Throttled
        assertTrue(trackingService.receiveLocation(updateStationary, authentication));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));

        // Update 3: 2s elapsed and moved > 20m -> Published
        assertTrue(trackingService.receiveLocation(updateMoved, authentication));
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));
    }

    @Test
    @DisplayName("Throttling: Heartbeat triggers broadcast even if stationary")
    void testReceiveLocation_Throttling_HeartbeatTriggersBroadcast() {
        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(activeOrder));
        when(locationService.getLatestLocation(1L)).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        trackingService.setThrottleIntervalMs(1000);
        trackingService.setThrottleMinDistanceMeters(20.0);
        trackingService.setHeartbeatIntervalMs(10000); // 10s heartbeat

        Instant t0 = Instant.parse("2026-10-04T12:00:00.000Z");
        Instant t15s = Instant.parse("2026-10-04T12:00:15.000Z"); // 15s later, stationary

        LocationUpdateRequest update1 = new LocationUpdateRequest(1L, 12.9716, 77.5946, t0, 100L);
        LocationUpdateRequest updateStationary = new LocationUpdateRequest(1L, 12.9716, 77.5946, t15s, 100L);

        assertTrue(trackingService.receiveLocation(update1, authentication));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));

        // After heartbeat elapsed, publish even though 0 meters moved
        assertTrue(trackingService.receiveLocation(updateStationary, authentication));
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/delivery/100/location"), any(CustomerLocationBroadcast.class));
    }
}
