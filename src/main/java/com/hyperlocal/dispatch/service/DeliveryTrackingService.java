package com.hyperlocal.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.repository.UserRepository;
import com.hyperlocal.common.exception.AccessDeniedException;
import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.dto.*;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.dispatch.util.GeoUtils;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeliveryTrackingService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackingService.class);

    private static final String DELIVERY_PARTNER_KEY_PREFIX = "delivery:partner:";
    private static final String PARTNER_LOCATION_KEY_PREFIX = "partner:";
    private static final String PARTNER_LOCATION_KEY_SUFFIX = ":location";
    private static final String DELIVERY_LOCATION_KEY_PREFIX = "delivery:";
    private static final String DELIVERY_LOCATION_KEY_SUFFIX = ":location";
    private static final Duration LOCATION_TTL = Duration.ofSeconds(30);

    public static final List<OrderStatus> ACTIVE_DELIVERY_STATUSES = List.of(
            OrderStatus.ASSIGNED,
            OrderStatus.ACCEPTED,
            OrderStatus.PICKED_UP,
            OrderStatus.OUT_FOR_DELIVERY
    );

    @Value("${dispatch.tracking.throttle-interval-ms:1000}")
    private long throttleIntervalMs = 1000;

    @Value("${dispatch.tracking.throttle-min-distance-meters:0.0}")
    private double throttleMinDistanceMeters = 0.0;

    @Value("${dispatch.tracking.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs = 30000;

    public record LastPublishedInfo(
            double lat,
            double lng,
            Instant timestamp
    ) {}

    private final Map<Long, LastPublishedInfo> lastPublishedEvents = new ConcurrentHashMap<>();

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final LocationService locationService;
    private final GeoLocationService geoLocationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public DeliveryTrackingService(
            DeliveryPartnerRepository deliveryPartnerRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            LocationService locationService,
            GeoLocationService geoLocationService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this(deliveryPartnerRepository, userRepository, orderRepository,
                locationService, geoLocationService, redisTemplate, objectMapper,
                messagingTemplate, 1000L, 0.0, 30000L);
    }

    public DeliveryTrackingService(
            DeliveryPartnerRepository deliveryPartnerRepository,
            UserRepository userRepository,
            OrderRepository orderRepository,
            LocationService locationService,
            GeoLocationService geoLocationService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate,
            long throttleIntervalMs,
            double throttleMinDistanceMeters,
            long heartbeatIntervalMs
    ) {
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.locationService = locationService;
        this.geoLocationService = geoLocationService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.throttleIntervalMs = throttleIntervalMs;
        this.throttleMinDistanceMeters = throttleMinDistanceMeters;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    private String buildDeliveryPartnerLocationKey(Long partnerId) {
        return DELIVERY_PARTNER_KEY_PREFIX + partnerId + PARTNER_LOCATION_KEY_SUFFIX;
    }

    private String buildPartnerLocationKey(Long partnerId) {
        return PARTNER_LOCATION_KEY_PREFIX + partnerId + PARTNER_LOCATION_KEY_SUFFIX;
    }

    private String buildDeliveryLocationKey(Long deliveryId) {
        return DELIVERY_LOCATION_KEY_PREFIX + deliveryId + DELIVERY_LOCATION_KEY_SUFFIX;
    }

    /**
     * Entrypoint for processing incoming location updates.
     * Flow:
     * 1. Validate coordinates
     * 2. Verify partner identity (from authenticated session, preventing impersonation)
     * 3. Check active delivery (verify authorization for that delivery)
     * 4. Check for out-of-order messages (drop delayed GPS pings)
     * 5. Update Redis location with TTL (delivery:partner:{partnerId}:location)
     * 6. Update Redis Geo index (delivery:partners:geo)
     * 7. Publish tracking event (/topic/delivery/{orderId}/location) (throttled)
     */
    public boolean receiveLocation(LocationUpdateRequest update, Authentication authentication) {
        // 1. Validate coordinates
        validateCoordinates(update);

        // 2. Verify partner identity from session
        DeliveryPartner partner = verifyPartnerIdentity(update, authentication);

        // 3. Check active delivery & partner authorization
        Order activeDelivery = checkActiveDelivery(update, partner);

        // 4. Check out-of-order GPS messages
        if (isOutOfOrder(partner.getId(), activeDelivery.getId(), update.timestamp())) {
            log.warn("Ignored delayed/out-of-order GPS update for partner {} (delivery {}). Timestamp {} is older than latest accepted update.",
                    partner.getId(), activeDelivery.getId(), update.timestamp());
            return false;
        }

        // 5. Update Redis location (frequent, as needed)
        updateRedisLocation(partner.getId(), activeDelivery.getId(), update);

        // 6. Update Redis Geo index (frequent, as needed)
        updateRedisGeoIndex(partner.getId(), update.latitude(), update.longitude());

        // 7. Publish tracking event (throttled for customers)
        if (shouldPublishCustomerEvent(activeDelivery.getId(), update.latitude(), update.longitude(), update.timestamp())) {
            publishTrackingEvent(activeDelivery, update);
            recordPublishedEvent(activeDelivery.getId(), update.latitude(), update.longitude(), update.timestamp());
            log.debug("Successfully tracked and published location for delivery {} by partner {}: lat={}, lng={}",
                    activeDelivery.getId(), partner.getId(), update.latitude(), update.longitude());
        } else {
            log.debug("Throttled customer tracking event for delivery {} by partner {}: lat={}, lng={}",
                    activeDelivery.getId(), partner.getId(), update.latitude(), update.longitude());
        }

        return true;
    }

    public boolean receiveLocation(LocationUpdateRequest update) {
        return receiveLocation(update, SecurityContextHolder.getContext().getAuthentication());
    }

    public boolean receiveLocation(LocationDto dto, Authentication authentication) {
        if (dto == null) {
            throw new IllegalArgumentException("LocationDto cannot be null");
        }
        return receiveLocation(new LocationUpdateRequest(dto.partnerId(), dto.lat(), dto.lng(), dto.timestamp(), dto.deliveryId()), authentication);
    }

    public boolean receiveLocation(LocationDto dto) {
        return receiveLocation(dto, SecurityContextHolder.getContext().getAuthentication());
    }

    private void validateCoordinates(LocationUpdateRequest update) {
        if (update == null) {
            throw new IllegalArgumentException("Location update cannot be null");
        }
        if (update.latitude() == null || update.longitude() == null) {
            throw new IllegalArgumentException("Coordinates (latitude and longitude) must not be null");
        }
        if (Double.isNaN(update.latitude()) || Double.isNaN(update.longitude())
                || Double.isInfinite(update.latitude()) || Double.isInfinite(update.longitude())) {
            throw new IllegalArgumentException(String.format(
                    "Invalid coordinates: latitude=%s, longitude=%s cannot be NaN or Infinite",
                    update.latitude(), update.longitude()
            ));
        }
        Coordinates coords = new Coordinates(update.latitude(), update.longitude());
        if (!coords.isValid()) {
            throw new IllegalArgumentException(String.format(
                    "Invalid coordinates: latitude=%s, longitude=%s. Latitude must be in [-90, 90] and longitude in [-180, 180].",
                    update.latitude(), update.longitude()
            ));
        }
    }

    private DeliveryPartner verifyPartnerIdentity(LocationUpdateRequest update, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Unauthenticated sender: valid authentication session required");
        }

        User user = extractUser(authentication);
        if (user == null) {
            throw new AccessDeniedException("Authenticated user principal could not be resolved");
        }

        DeliveryPartner partner = deliveryPartnerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("User " + user.getEmail() + " is not a registered delivery partner"));

        // Guard against impersonation: verify client-supplied partnerId matches authenticated partner
        if (update.partnerId() != null && !update.partnerId().equals(partner.getId())) {
            log.warn("Impersonation attempt detected! Authenticated partner ID {} tried to report location as partner ID {}",
                    partner.getId(), update.partnerId());
            throw new AccessDeniedException("Partner identity mismatch: cannot report location for another partner");
        }

        return partner;
    }

    private User extractUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        if (principal instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        }
        if (principal instanceof String email) {
            return userRepository.findByEmail(email).orElse(null);
        }
        if (authentication.getName() != null) {
            return userRepository.findByEmail(authentication.getName()).orElse(null);
        }
        return null;
    }

    private Order checkActiveDelivery(LocationUpdateRequest update, DeliveryPartner partner) {
        if (update.deliveryId() != null) {
            Order order = orderRepository.findById(update.deliveryId())
                    .orElseThrow(() -> new AccessDeniedException("Delivery order not found with id: " + update.deliveryId()));

            if (order.getDeliveryPartner() == null || !order.getDeliveryPartner().getId().equals(partner.getId())) {
                log.warn("Partner {} is not assigned to delivery {}", partner.getId(), update.deliveryId());
                throw new AccessDeniedException("Partner " + partner.getId() + " is not authorized to report location for delivery " + update.deliveryId());
            }

            if (!ACTIVE_DELIVERY_STATUSES.contains(order.getStatus())) {
                log.warn("Delivery {} is not in an active status (current: {})", update.deliveryId(), order.getStatus());
                throw new AccessDeniedException("Delivery " + update.deliveryId() + " is not currently active (status: " + order.getStatus() + ")");
            }

            return order;
        }

        // Auto-discover active delivery assigned to partner
        List<Order> activeOrders = orderRepository.findActiveOrdersForPartner(partner.getId(), ACTIVE_DELIVERY_STATUSES);
        if (activeOrders.isEmpty()) {
            log.warn("No active delivery found for partner {}", partner.getId());
            throw new AccessDeniedException("No active delivery found for partner " + partner.getId());
        }

        return activeOrders.get(0);
    }

    private boolean isOutOfOrder(Long partnerId, Long deliveryId, Instant incomingTimestamp) {
        if (incomingTimestamp == null) {
            return false;
        }

        // Check latest partner location via locationService (reads Redis partner:{partnerId}:location)
        Optional<PartnerLocationResponse> latestPartnerOpt = locationService.getLatestLocation(partnerId);
        if (latestPartnerOpt.isPresent()) {
            Instant latestPartnerTs = latestPartnerOpt.get().timestamp();
            if (latestPartnerTs != null && incomingTimestamp.isBefore(latestPartnerTs)) {
                return true;
            }
        }

        // Check latest delivery location in Redis
        if (deliveryId != null) {
            Optional<CustomerLocationBroadcast> latestDeliveryOpt = getLatestDeliveryLocation(deliveryId);
            if (latestDeliveryOpt.isPresent()) {
                Instant latestDeliveryTs = latestDeliveryOpt.get().timestamp();
                if (latestDeliveryTs != null && incomingTimestamp.isBefore(latestDeliveryTs)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void updateRedisLocation(Long partnerId, Long deliveryId, LocationUpdateRequest update) {
        try {
            PartnerLocationRecord record = new PartnerLocationRecord(
                    update.latitude(),
                    update.longitude(),
                    update.timestamp()
            );
            String json = objectMapper.writeValueAsString(record);

            // Store using delivery:partner:{partnerId}:location and partner:{partnerId}:location with 30s TTL
            redisTemplate.opsForValue().set(buildDeliveryPartnerLocationKey(partnerId), json, LOCATION_TTL);
            redisTemplate.opsForValue().set(buildPartnerLocationKey(partnerId), json, LOCATION_TTL);

            if (deliveryId != null) {
                redisTemplate.opsForValue().set(buildDeliveryLocationKey(deliveryId), json, LOCATION_TTL);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize location for partner {}: {}", partnerId, e.getMessage());
            throw new IllegalStateException("Failed to serialize location update", e);
        }
    }

    private void updateRedisGeoIndex(Long partnerId, double latitude, double longitude) {
        geoLocationService.updatePartnerLocation(partnerId, latitude, longitude);
    }

    private void publishTrackingEvent(Order order, LocationUpdateRequest update) {
        CustomerLocationBroadcast broadcast = new CustomerLocationBroadcast(
                "DELIVERY_LOCATION_UPDATED",
                "delivery-" + order.getId(),
                update.latitude(),
                update.longitude(),
                update.timestamp()
        );

        Long orderId = order.getId();
        // 1. Scoped STOMP topic for this specific order/delivery
        messagingTemplate.convertAndSend("/topic/delivery/" + orderId + "/location", broadcast);

        // 2. Also send to authorized customer's private queue if customer email is available
        if (order.getCustomer() != null && order.getCustomer().getEmail() != null) {
            messagingTemplate.convertAndSendToUser(
                    order.getCustomer().getEmail(),
                    "/queue/delivery-location",
                    broadcast
            );
        }
    }

    public Optional<CustomerLocationBroadcast> getLatestDeliveryLocation(Long deliveryId) {
        if (deliveryId == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(buildDeliveryLocationKey(deliveryId));
            if (json == null) {
                return Optional.empty();
            }
            PartnerLocationRecord req = objectMapper.readValue(json, PartnerLocationRecord.class);
            return Optional.of(new CustomerLocationBroadcast(
                    "DELIVERY_LOCATION_UPDATED",
                    "delivery-" + deliveryId,
                    req.lat(),
                    req.lng(),
                    req.timestamp()
            ));
        } catch (Exception e) {
            log.error("Failed to deserialize location for delivery {}: {}", deliveryId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Determine if an update should be published to the customer based on:
     * 1. Elapsed time since last broadcast (throttleIntervalMs)
     * 2. Movement distance since last broadcast (throttleMinDistanceMeters)
     * 3. Heartbeat interval (heartbeatIntervalMs)
     */
    public boolean shouldPublishCustomerEvent(Long deliveryId, double lat, double lng, Instant timestamp) {
        if (deliveryId == null) {
            return true;
        }

        LastPublishedInfo last = lastPublishedEvents.get(deliveryId);
        if (last == null) {
            // First update for this delivery is always published
            return true;
        }

        Instant eventTime = timestamp != null ? timestamp : Instant.now();
        long elapsedMs = Duration.between(last.timestamp(), eventTime).toMillis();

        // Rate limit: If elapsed time is less than throttle interval, throttle the broadcast
        if (elapsedMs < throttleIntervalMs) {
            return false;
        }

        // If distance threshold is configured, enforce movement distance or heartbeat expiration
        if (throttleMinDistanceMeters > 0) {
            double distanceMeters = GeoUtils.calculateHaversineDistanceKm(
                    new Location(last.lat(), last.lng()),
                    new Location(lat, lng)
            ) * 1000.0;

            if (distanceMeters < throttleMinDistanceMeters && elapsedMs < heartbeatIntervalMs) {
                return false;
            }
        }

        return true;
    }

    public void recordPublishedEvent(Long deliveryId, double lat, double lng, Instant timestamp) {
        if (deliveryId != null) {
            Instant eventTime = timestamp != null ? timestamp : Instant.now();
            lastPublishedEvents.put(deliveryId, new LastPublishedInfo(lat, lng, eventTime));
        }
    }

    public void clearThrottleCache(Long deliveryId) {
        if (deliveryId != null) {
            lastPublishedEvents.remove(deliveryId);
        }
    }

    public void clearAllThrottleCache() {
        lastPublishedEvents.clear();
    }

    public Optional<LastPublishedInfo> getLastPublishedInfo(Long deliveryId) {
        if (deliveryId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(lastPublishedEvents.get(deliveryId));
    }

    public long getThrottleIntervalMs() {
        return throttleIntervalMs;
    }

    public void setThrottleIntervalMs(long throttleIntervalMs) {
        this.throttleIntervalMs = throttleIntervalMs;
    }

    public double getThrottleMinDistanceMeters() {
        return throttleMinDistanceMeters;
    }

    public void setThrottleMinDistanceMeters(double throttleMinDistanceMeters) {
        this.throttleMinDistanceMeters = throttleMinDistanceMeters;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }
}
