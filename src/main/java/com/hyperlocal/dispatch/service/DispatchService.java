package com.hyperlocal.dispatch.service;

import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;
import com.hyperlocal.dispatch.dto.DispatchCandidateScore;
import com.hyperlocal.dispatch.dto.DispatchDecision;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.dispatch.strategy.DispatchScoringStrategy;
import com.hyperlocal.notification.service.OrderEventPublisher;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final OrderRepository orderRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final EligiblePartnerService eligiblePartnerService;
    private final DispatchScoringStrategy scoringStrategy;

    public DispatchService(OrderRepository orderRepository,
                           DeliveryPartnerRepository deliveryPartnerRepository,
                           OrderEventPublisher orderEventPublisher,
                           EligiblePartnerService eligiblePartnerService,
                           DispatchScoringStrategy scoringStrategy) {
        this.orderRepository = orderRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.eligiblePartnerService = eligiblePartnerService;
        this.scoringStrategy = scoringStrategy;
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

        Location pickup = order.getShop() != null ? order.getShop().getLocation() : null;
        if (pickup == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no valid pickup location");
        }

        // 1. Discovery - Who can do this?
        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(
                pickup.getLatitude(), pickup.getLongitude());

        // 2. Optimization - Who should we try first?
        DispatchDecision decision = findBestPartner(pickup, candidates);

        if (decision.getSelectedPartnerId() == null) {
            log.warn("No suitable delivery partners available for order {}", order.getId());
            return;
        }

        // For today, we just publish the event to the single best candidate.
        // Tomorrow this becomes full Assignment logic.
        DeliveryPartner bestPartner = deliveryPartnerRepository.findById(decision.getSelectedPartnerId())
                .orElseThrow();
        orderEventPublisher.publishNewDelivery(order, List.of(bestPartner));
    }

    public List<DispatchCandidateScore> rankCandidates(List<DeliveryPartnerCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<DispatchCandidateScore> scoredCandidates = candidates.stream()
                .filter(DeliveryPartnerCandidate::isEligible)
                .map(this::scoreCandidate)
                .collect(Collectors.toList());

        if (scoredCandidates.isEmpty()) {
            return List.of();
        }

        // Tie-breaker rules:
        // 1. Score (from strategy)
        // 2. Freshness (lowest age)
        // 3. Active workload
        // 4. PartnerId (deterministic fallback)
        scoredCandidates.sort(Comparator
                .comparingDouble(DispatchCandidateScore::getScore)
                .thenComparingLong(c -> c.getLocationFreshness() == null ? Long.MAX_VALUE : c.getLocationFreshness())
                .thenComparingInt(c -> c.getActiveWorkload() == null ? Integer.MAX_VALUE : c.getActiveWorkload())
                .thenComparingLong(DispatchCandidateScore::getPartnerId));

        return scoredCandidates;
    }

    public DispatchDecision findBestPartner(Location pickup, List<DeliveryPartnerCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new DispatchDecision(null, List.of(), "No candidates available");
        }

        List<DispatchCandidateScore> scoredCandidates = rankCandidates(candidates);

        if (scoredCandidates.isEmpty()) {
            return new DispatchDecision(null, List.of(), "No eligible candidates");
        }

        DispatchCandidateScore best = scoredCandidates.get(0);

        return new DispatchDecision(
                best.getPartnerId(),
                scoredCandidates,
                "Selected partner " + best.getPartnerId() + " based on configured scoring strategy and tie-breakers"
        );
    }

    private DispatchCandidateScore scoreCandidate(DeliveryPartnerCandidate candidate) {
        // Fetch workload
        List<OrderStatus> activeStatuses = List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY);
        int activeWorkload = orderRepository.countActiveOrdersForPartner(candidate.getPartnerId(), activeStatuses);

        // Calculate freshness (seconds ago)
        Long freshness = null;
        if (candidate.getLastSeen() != null) {
            freshness = ChronoUnit.SECONDS.between(candidate.getLastSeen(), Instant.now());
            if (freshness < 0) freshness = 0L;
        }

        // Apply Strategy
        double score = scoringStrategy.calculateScore(candidate, activeWorkload);

        return new DispatchCandidateScore(
                candidate.getPartnerId(),
                candidate.getDistanceKm(),
                freshness,
                null, // ETA not calculated yet
                score,
                activeWorkload
        );
    }
}
