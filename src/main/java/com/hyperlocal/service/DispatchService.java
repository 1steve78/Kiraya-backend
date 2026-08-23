package com.hyperlocal.service;

import com.hyperlocal.entity.DeliveryPartner;
import com.hyperlocal.entity.Order;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.exception.DistanceServiceException;
import com.hyperlocal.model.DistanceResult;
import com.hyperlocal.model.Location;
import com.hyperlocal.model.OrderStatus;
import com.hyperlocal.repository.DeliveryPartnerRepository;
import com.hyperlocal.repository.OrderRepository;
import com.hyperlocal.util.GeoUtils;
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
                           DistanceService distanceService) {
        this.orderRepository = orderRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.distanceService = distanceService;
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

        DeliveryPartner bestPartner = findBestPartner(order)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No suitable delivery partner available"));

        order.setDeliveryPartner(bestPartner);
        orderRepository.save(order);
    }

    public Optional<DeliveryPartner> findBestPartner(Order order) {
        Shop shop = order.getShop();
        Location shopLocation = (shop != null) ? shop.getLocation() : null;

        // 1. Fetch available partners
        List<DeliveryPartner> availablePartners = deliveryPartnerRepository.findByIsAvailableTrue();
        if (availablePartners.isEmpty()) {
            return Optional.empty();
        }

        // 2. Stage 1: Haversine Filtering (when locations are available)
        List<DeliveryPartner> candidates;
        if (shopLocation != null) {
            List<DeliveryPartner> nearbyCandidates = availablePartners.stream()
                    .filter(p -> p.getLocation() != null)
                    .map(p -> new CandidateTemp(p, GeoUtils.calculateHaversineDistanceKm(p.getLocation(), shopLocation)))
                    .filter(c -> c.straightLineDistance <= maxRadiusKm)
                    .sorted(Comparator.comparingDouble(c -> c.straightLineDistance))
                    .limit(maxRoutingCandidates)
                    .map(c -> c.partner)
                    .collect(Collectors.toList());

            candidates = !nearbyCandidates.isEmpty() ? nearbyCandidates : availablePartners;
        } else {
            candidates = availablePartners;
        }

        // 3. Stage 2: Workload Filtering & Routing Scoring
        List<OrderStatus> activeStatuses = List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY);
        DeliveryPartner bestPartner = null;
        double lowestScore = Double.POSITIVE_INFINITY;

        for (DeliveryPartner partner : candidates) {
            int activeOrders = orderRepository.countActiveOrdersForPartner(partner.getId(), activeStatuses);
            if (activeOrders > MAX_ACTIVE_ORDERS) {
                continue; // Skip overloaded partners
            }

            try {
                DistanceResult route = distanceService.calculateDistance(partner, shop);
                double score = calculateScore(route, activeOrders);

                if (score < lowestScore) {
                    lowestScore = score;
                    bestPartner = partner;
                }
            } catch (DistanceServiceException e) {
                log.warn("Routing calculation failed for partner {}: {}", partner.getId(), e.getMessage());
            }
        }

        return Optional.ofNullable(bestPartner);
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