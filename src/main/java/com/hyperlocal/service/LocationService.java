package com.hyperlocal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperlocal.dto.CustomerLocationBroadcast;
import com.hyperlocal.dto.LocationUpdateRequest;
import com.hyperlocal.dto.PartnerLocationResponse;
import com.hyperlocal.dto.PartnerPresence;
import com.hyperlocal.entity.Order;
import com.hyperlocal.model.AvailabilityStatus;
import com.hyperlocal.model.OrderStatus;
import com.hyperlocal.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class LocationService {
    private static final Logger log = LoggerFactory.getLogger(LocationService.class);
    private static final String LOCATION_KEY_PREFIX = "partner:";
    private static final String LOCATION_KEY_SUFFIX = ":location";
    private static final Duration LOCATION_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final OrderRepository orderRepository;
    private final GeoLocationService geoLocationService;

    public LocationService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate,
            PresenceService presenceService,
            OrderRepository orderRepository,
            GeoLocationService geoLocationService
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
        this.orderRepository = orderRepository;
        this.geoLocationService = geoLocationService;
    }

    private String buildKey(Long partnerId) {
        return LOCATION_KEY_PREFIX + partnerId + LOCATION_KEY_SUFFIX;
    }

    public void processLocation(Long partnerId, LocationUpdateRequest req) {
        // 1. Validate presence (Must be ONLINE or BUSY)
        Optional<PartnerPresence> presence = presenceService.getPresence(partnerId);
        if (presence.isEmpty() || presence.get().status() == AvailabilityStatus.OFFLINE) {
            log.debug("Ignored location update for offline/unknown partner: {}", partnerId);
            return;
        }

        // 2. Save location to Redis with TTL
        try {
            String json = objectMapper.writeValueAsString(req);
            redisTemplate.opsForValue().set(buildKey(partnerId), json, LOCATION_TTL);
            
            // Update Geo index
            geoLocationService.updatePartnerLocation(partnerId, req.latitude(), req.longitude());
            
            log.debug("Updated location for partner: {} lat={} lng={}", partnerId, req.latitude(), req.longitude());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize location for partner: {}", partnerId, e);
            return;
        }

        // 3. Find active delivery to broadcast to customer
        List<OrderStatus> activeStatuses = List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY);
        for (OrderStatus status : activeStatuses) {
            Page<Order> activeOrders = orderRepository.findByDeliveryPartnerIdAndStatus(
                    partnerId, status, PageRequest.of(0, 1)
            );
            
            if (activeOrders.hasContent()) {
                Order order = activeOrders.getContent().get(0);
                CustomerLocationBroadcast broadcast = new CustomerLocationBroadcast(
                        order.getId(),
                        req.latitude(),
                        req.longitude(),
                        req.timestamp()
                );
                
                // Broadcast to the specific topic for this order
                messagingTemplate.convertAndSend("/topic/delivery/" + order.getId() + "/location", broadcast);
                break; // Just broadcast for the first active order we find
            }
        }
    }

    public Optional<PartnerLocationResponse> getLatestLocation(Long partnerId) {
        String json = redisTemplate.opsForValue().get(buildKey(partnerId));
        if (json == null) {
            return Optional.empty();
        }

        try {
            LocationUpdateRequest req = objectMapper.readValue(json, LocationUpdateRequest.class);
            return Optional.of(new PartnerLocationResponse(
                    partnerId,
                    req.latitude(),
                    req.longitude(),
                    req.timestamp()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize location for partner: {}", partnerId, e);
            return Optional.empty();
        }
    }
}

