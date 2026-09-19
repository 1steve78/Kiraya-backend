package com.hyperlocal.dispatch.service;

import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.model.DistanceResult;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.dispatch.util.GeoUtils;
import com.hyperlocal.notification.service.OrderEventPublisher;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.repository.OrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final OrderRepository orderRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DistanceService distanceService;
    private final OrderEventPublisher orderEventPublisher;

    @Value("${dispatch.max-radius-km:5.0}")
    private double maxRadiusKm = 5.0;

    @Value("${dispatch.max-routing-candidates:10}")
    private int maxRoutingCandidates = 10;

    private static final int MAX_ACTIVE_ORDERS = 3;
    private static final double WEIGHT_DISTANCE = 0.5;
    private static final double WEIGHT_WORKLOAD = 2.0;
    private static final double WEIGHT_ETA = 0.3;

    public DispatchService(OrderRepository orderRepository,
                           DeliveryPartnerRepository deliveryPartnerRepository,
                           DistanceService distanceService,
                           OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.distanceService = distanceService;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Transactional
    public void dispatchOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order must be READY_FOR_PICKUP to be dispatched");
        }
        if (order.getDeliveryPartner() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already assigned to a delivery partner");
        }

        List<DeliveryPartner> candidates = findCandidates(order);
        if (candidates.isEmpty()) {
            log.warn("No suitable delivery partners available for order {}", order.getId());
            return;
        }

        // Broadcast to candidates instead of auto-assigning
        orderEventPublisher.publishNewDelivery(order, candidates);
       
    }

    public List<DeliveryPartner> findCandidates(Order order) {
        Shop shop = order.getShop();
        Location shopLocation = (shop != null) ? shop.getLocation() : null;

        // 1. Fetch available partners
        List<DeliveryPartner> availablePartners = deliveryPartnerRepository.findByIsAvailableTrue();
        if (availablePartners.isEmpty()) {
            return List.of();
        }

        // 2. Stage 1: Haversine Filtering
        List<DeliveryPartner> candidates;
        if (shopLocation != null) {
            candidates = availablePartners.stream()
                    .filter(p -> p.getLocation() != null)
                    .map(p -> new CandidateTemp(p, GeoUtils.calculateHaversineDistanceKm(p.getLocation(), shopLocation)))
                    .filter(c -> c.straightLineDistance <= maxRadiusKm)
                    .sorted(Comparator.comparingDouble(c -> c.straightLineDistance))
                    .limit(maxRoutingCandidates)
                    .map(c -> c.partner)
                    .collect(Collectors.toList());
        } else {
            candidates = availablePartners;
        }

        // 3. Stage 2: Workload Filtering
        List<OrderStatus> activeStatuses = List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY);
        return candidates.stream().filter(partner -> {
            int activeOrders = orderRepository.countActiveOrdersForPartner(partner.getId(), activeStatuses);
            return activeOrders <= MAX_ACTIVE_ORDERS;
        }).collect(Collectors.toList());
    }

    private double calculateScore(DistanceResult route, int activeOrders) {
        return (route.getDistanceKm() * WEIGHT_DISTANCE)
                + (activeOrders * WEIGHT_WORKLOAD)
                + (route.getDurationMinutes() * WEIGHT_ETA);
    }

    private static class CandidateTemp {
        DeliveryPartner partner;
        double straightLineDistance;

        CandidateTemp(DeliveryPartner partner, double straightLineDistance) {
            this.partner = partner;
            this.straightLineDistance = straightLineDistance;
        }
    }
}
