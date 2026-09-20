package com.hyperlocal.dispatch.service;

import com.hyperlocal.common.exception.AccessDeniedException;
import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;
import com.hyperlocal.dispatch.dto.DispatchCandidateScore;
import com.hyperlocal.dispatch.dto.PartnerPresence;
import com.hyperlocal.dispatch.entity.DeliveryAssignment;
import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;
import com.hyperlocal.dispatch.enums.DeliveryAssignmentStatus;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import com.hyperlocal.dispatch.exception.DeliveryPartnerNotFoundException;
import com.hyperlocal.dispatch.exception.InvalidAssignmentStateException;
import com.hyperlocal.dispatch.repository.DeliveryAssignmentRepository;
import com.hyperlocal.dispatch.repository.DeliveryOfferRepository;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeliveryAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAssignmentService.class);

    private final OrderRepository orderRepository;
    private final DeliveryOfferRepository deliveryOfferRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
    private final EligiblePartnerService eligiblePartnerService;
    private final DispatchService dispatchService;
    private final PresenceService presenceService;
    private final OrderEventPublisher orderEventPublisher;

    @Value("${dispatch.offer-timeout-seconds:15}")
    private int offerTimeoutSeconds = 15;

    @Value("${dispatch.max-attempts:5}")
    private int maxAttempts = 5;

    public DeliveryAssignmentService(OrderRepository orderRepository,
                                     DeliveryOfferRepository deliveryOfferRepository,
                                     DeliveryPartnerRepository deliveryPartnerRepository,
                                     DeliveryAssignmentRepository deliveryAssignmentRepository,
                                     EligiblePartnerService eligiblePartnerService,
                                     DispatchService dispatchService,
                                     PresenceService presenceService,
                                     OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.deliveryOfferRepository = deliveryOfferRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.deliveryAssignmentRepository = deliveryAssignmentRepository;
        this.eligiblePartnerService = eligiblePartnerService;
        this.dispatchService = dispatchService;
        this.presenceService = presenceService;
        this.orderEventPublisher = orderEventPublisher;
    }

    public void setOfferTimeoutSeconds(int offerTimeoutSeconds) {
        this.offerTimeoutSeconds = offerTimeoutSeconds;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getOfferTimeoutSeconds() {
        return offerTimeoutSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    @Transactional
    public Optional<DeliveryOffer> startAssignment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + orderId));
        return startAssignment(order);
    }

    @Transactional
    public Optional<DeliveryOffer> startAssignment(Order order) {
        if (order.getStatus() == OrderStatus.ASSIGNED || order.getDeliveryPartner() != null) {
            throw new InvalidAssignmentStateException("Order " + order.getId() + " is already assigned to a delivery partner");
        }

        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP && order.getStatus() != OrderStatus.OFFERED) {
            throw new InvalidAssignmentStateException("Order " + order.getId() + " must be READY_FOR_PICKUP to start assignment (current: " + order.getStatus() + ")");
        }

        Location pickup = order.getShop() != null ? order.getShop().getLocation() : null;
        if (pickup == null) {
            log.warn("Order {} has no valid pickup location", order.getId());
            return Optional.empty();
        }

        // 1. Get eligible partners
        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(
                pickup.getLatitude(), pickup.getLongitude());

        if (candidates == null || candidates.isEmpty()) {
            log.info("No candidates available for order {}", order.getId());
            return Optional.empty();
        }

        // 2. Rank candidates
        List<DispatchCandidateScore> ranked = dispatchService.rankCandidates(candidates);

        if (ranked == null || ranked.isEmpty()) {
            log.info("No eligible ranked candidates for order {}", order.getId());
            return Optional.empty();
        }

        // 3. Store candidate sequence in DeliveryAssignment
        List<Long> candidateIds = ranked.stream()
                .map(DispatchCandidateScore::getPartnerId)
                .limit(maxAttempts)
                .toList();

        DeliveryAssignment assignment = new DeliveryAssignment(order.getId(), candidateIds);
        assignment.setCurrentCandidateIndex(0);
        assignment.setStatus(DeliveryAssignmentStatus.IN_PROGRESS);
        deliveryAssignmentRepository.save(assignment);

        // 4. Offer sequentially to the first ranked candidate
        Long firstPartnerId = candidateIds.get(0);
        DeliveryPartner partner = deliveryPartnerRepository.findById(firstPartnerId)
                .orElseThrow(() -> new DeliveryPartnerNotFoundException("Delivery partner not found with id: " + firstPartnerId));

        order.setStatus(OrderStatus.OFFERED);
        orderRepository.save(order);

        DeliveryOffer offer = new DeliveryOffer(
                order.getId(),
                firstPartnerId,
                Instant.now().plusSeconds(offerTimeoutSeconds)
        );
        DeliveryOffer savedOffer = deliveryOfferRepository.save(offer);

        orderEventPublisher.publishDeliveryOfferCreated(savedOffer, order, partner);

        log.info("Created and dispatched initial delivery offer {} for order {} to partner {}",
                savedOffer.getId(), order.getId(), firstPartnerId);

        return Optional.of(savedOffer);
    }

    @Transactional
    public Optional<DeliveryOffer> advanceAssignment(Order order) {
        if (order.getStatus() == OrderStatus.ASSIGNED || order.getDeliveryPartner() != null) {
            throw new InvalidAssignmentStateException("Order " + order.getId() + " is already assigned to a delivery partner");
        }

        Optional<DeliveryAssignment> assignmentOpt = deliveryAssignmentRepository.findFirstByDeliveryIdOrderByCreatedAtDesc(order.getId());
        if (assignmentOpt.isPresent()) {
            DeliveryAssignment assignment = assignmentOpt.get();
            if (assignment.getStatus() != DeliveryAssignmentStatus.IN_PROGRESS) {
                return Optional.empty();
            }

            int nextIndex = assignment.getCurrentCandidateIndex() + 1;
            assignment.setCurrentCandidateIndex(nextIndex);
            assignment.setUpdatedAt(Instant.now());

            List<Long> candidateIds = assignment.getCandidateIds();
            if (nextIndex < candidateIds.size() && nextIndex < maxAttempts) {
                Long nextPartnerId = candidateIds.get(nextIndex);
                DeliveryPartner partner = deliveryPartnerRepository.findById(nextPartnerId)
                        .orElseThrow(() -> new DeliveryPartnerNotFoundException("Delivery partner not found with id: " + nextPartnerId));

                order.setStatus(OrderStatus.OFFERED);
                orderRepository.save(order);

                deliveryAssignmentRepository.save(assignment);

                DeliveryOffer offer = new DeliveryOffer(
                        order.getId(),
                        nextPartnerId,
                        Instant.now().plusSeconds(offerTimeoutSeconds)
                );
                DeliveryOffer savedOffer = deliveryOfferRepository.save(offer);
                if (savedOffer == null) {
                    savedOffer = offer;
                }

                orderEventPublisher.publishDeliveryOfferCreated(savedOffer, order, partner);

                log.info("Advanced assignment for order {} to candidate index {} (partner {})",
                        order.getId(), nextIndex, nextPartnerId);
                return Optional.of(savedOffer);
            } else {
                // All candidates exhausted or max attempts reached
                log.warn("All candidates failed or max attempts ({}) reached for order {}", maxAttempts, order.getId());
                assignment.setStatus(DeliveryAssignmentStatus.FAILED);
                deliveryAssignmentRepository.save(assignment);

                order.setStatus(OrderStatus.ASSIGNMENT_FAILED);
                orderRepository.save(order);

                return Optional.empty();
            }
        }

        // Fallback to dynamic candidate discovery if no DeliveryAssignment exists
        return offerNextCandidate(order);
    }

    @Transactional
    public Optional<DeliveryOffer> offerNextCandidate(Order order, List<DispatchCandidateScore> rankedCandidates) {
        if (order.getStatus() == OrderStatus.ASSIGNED || order.getDeliveryPartner() != null) {
            throw new InvalidAssignmentStateException("Order " + order.getId() + " is already assigned to a delivery partner");
        }

        List<DeliveryOffer> existingOffers = deliveryOfferRepository.findByDeliveryId(order.getId());
        Set<Long> alreadyOfferedPartnerIds = existingOffers.stream()
                .map(DeliveryOffer::getPartnerId)
                .collect(Collectors.toSet());

        List<DispatchCandidateScore> remainingCandidates = rankedCandidates.stream()
                .filter(c -> !alreadyOfferedPartnerIds.contains(c.getPartnerId()))
                .toList();

        if (remainingCandidates.isEmpty()) {
            log.warn("All candidate partners exhausted for order {}", order.getId());
            if (order.getStatus() == OrderStatus.OFFERED) {
                order.setStatus(OrderStatus.READY_FOR_PICKUP);
                orderRepository.save(order);
            }
            return Optional.empty();
        }

        DispatchCandidateScore nextCandidate = remainingCandidates.get(0);
        Long partnerId = nextCandidate.getPartnerId();

        DeliveryPartner partner = deliveryPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new DeliveryPartnerNotFoundException("Delivery partner not found with id: " + partnerId));

        order.setStatus(OrderStatus.OFFERED);
        orderRepository.save(order);

        DeliveryOffer offer = new DeliveryOffer(
                order.getId(),
                partnerId,
                Instant.now().plusSeconds(offerTimeoutSeconds)
        );
        DeliveryOffer savedOffer = deliveryOfferRepository.save(offer);

        orderEventPublisher.publishDeliveryOfferCreated(savedOffer, order, partner);

        log.info("Created and dispatched delivery offer {} for order {} to partner {}",
                savedOffer.getId(), order.getId(), partnerId);

        return Optional.of(savedOffer);
    }

    @Transactional
    public Optional<DeliveryOffer> offerNextCandidate(Order order) {
        if (order.getStatus() == OrderStatus.ASSIGNED || order.getDeliveryPartner() != null) {
            throw new InvalidAssignmentStateException("Order " + order.getId() + " is already assigned to a delivery partner");
        }

        Location pickup = order.getShop() != null ? order.getShop().getLocation() : null;
        if (pickup == null) {
            log.warn("Order {} has no valid pickup location for candidate discovery", order.getId());
            return Optional.empty();
        }

        List<DeliveryPartnerCandidate> candidates = eligiblePartnerService.findEligiblePartners(
                pickup.getLatitude(), pickup.getLongitude());

        if (candidates == null || candidates.isEmpty()) {
            log.info("No candidates available for order {} during next offer discovery", order.getId());
            if (order.getStatus() == OrderStatus.OFFERED) {
                order.setStatus(OrderStatus.READY_FOR_PICKUP);
                orderRepository.save(order);
            }
            return Optional.empty();
        }

        List<DispatchCandidateScore> ranked = dispatchService.rankCandidates(candidates);
        return offerNextCandidate(order, ranked);
    }

    @Transactional
    public DeliveryOffer acceptOffer(Long offerId, Long partnerId) {
        DeliveryOffer offer = deliveryOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery offer not found with id: " + offerId));

        // State validation: Offer must be PENDING (protect against duplicate acceptance)
        if (offer.getStatus() != DeliveryOfferStatus.PENDING) {
            throw new InvalidAssignmentStateException("Offer " + offerId + " is not in PENDING state (current: " + offer.getStatus() + ")");
        }

        // Authorization check if partnerId provided
        if (partnerId != null && !offer.getPartnerId().equals(partnerId)) {
            throw new AccessDeniedException("Partner " + partnerId + " is not authorized to accept offer " + offerId);
        }

        Order order = orderRepository.findById(offer.getDeliveryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + offer.getDeliveryId()));

        // Delivery state validation
        if (order.getStatus() != OrderStatus.OFFERED) {
            throw new InvalidAssignmentStateException("Order " + order.getId() + " is not in OFFERED state (current: " + order.getStatus() + ")");
        }
        if (order.getDeliveryPartner() != null) {
            throw new InvalidAssignmentStateException("Order " + order.getId() + " is already assigned to partner " + order.getDeliveryPartner().getId());
        }

        DeliveryPartner partner = deliveryPartnerRepository.findById(offer.getPartnerId())
                .orElseThrow(() -> new DeliveryPartnerNotFoundException("Delivery partner not found with id: " + offer.getPartnerId()));

        // Partner eligibility validation
        Optional<PartnerPresence> presenceOpt = presenceService.getPresence(partner.getId());
        if (presenceOpt.isPresent() && presenceOpt.get().status() == AvailabilityStatus.BUSY) {
            throw new InvalidAssignmentStateException("Partner " + partner.getId() + " is currently BUSY and cannot accept new deliveries");
        }

        // 1. Update Offer
        offer.setStatus(DeliveryOfferStatus.ACCEPTED);
        offer.setRespondedAt(Instant.now());
        DeliveryOffer savedOffer = deliveryOfferRepository.save(offer);

        // 2. Update Order
        order.setStatus(OrderStatus.ASSIGNED);
        order.setDeliveryPartner(partner);
        Order savedOrder = orderRepository.save(order);

        // 3. Update Assignment context if present
        deliveryAssignmentRepository.findFirstByDeliveryIdOrderByCreatedAtDesc(order.getId()).ifPresent(assignment -> {
            assignment.setStatus(DeliveryAssignmentStatus.ASSIGNED);
            assignment.setUpdatedAt(Instant.now());
            deliveryAssignmentRepository.save(assignment);
        });

        // 4. Update Partner DB state
        partner.setAvailable(false);
        deliveryPartnerRepository.save(partner);

        // 5. Update Partner Redis Presence to BUSY
        presenceService.setBusy(partner.getId());

        // 6. Publish events
        orderEventPublisher.publishDeliveryOfferAccepted(savedOffer, savedOrder, partner);
        orderEventPublisher.publishOrderAssigned(savedOrder);

        log.info("Partner {} accepted offer {} for order {}", partner.getId(), offerId, order.getId());

        return savedOffer;
    }

    @Transactional
    public DeliveryOffer rejectOffer(Long offerId, Long partnerId) {
        DeliveryOffer offer = deliveryOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery offer not found with id: " + offerId));

        if (offer.getStatus() != DeliveryOfferStatus.PENDING) {
            throw new InvalidAssignmentStateException("Offer " + offerId + " is not in PENDING state (current: " + offer.getStatus() + ")");
        }

        if (partnerId != null && !offer.getPartnerId().equals(partnerId)) {
            throw new AccessDeniedException("Partner " + partnerId + " is not authorized to reject offer " + offerId);
        }

        Order order = orderRepository.findById(offer.getDeliveryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found with id: " + offer.getDeliveryId()));

        DeliveryPartner partner = deliveryPartnerRepository.findById(offer.getPartnerId()).orElse(null);

        // 1. Update Offer
        offer.setStatus(DeliveryOfferStatus.REJECTED);
        offer.setRespondedAt(Instant.now());
        DeliveryOffer savedOffer = deliveryOfferRepository.save(offer);

        // 2. Publish Reject event
        orderEventPublisher.publishDeliveryOfferRejected(savedOffer, order, partner);

        log.info("Partner {} rejected offer {} for order {}. Moving to next candidate.",
                offer.getPartnerId(), offerId, order.getId());

        // 3. Sequential flow: Try next candidate
        if (order.getStatus() == OrderStatus.OFFERED) {
            advanceAssignment(order);
        }

        return savedOffer;
    }

    @Transactional
    public Optional<DeliveryOffer> expireOffer(Long offerId) {
        DeliveryOffer offer = deliveryOfferRepository.findById(offerId).orElse(null);
        if (offer == null) {
            return Optional.empty();
        }

        // Concurrency protection: only PENDING offers can be expired
        if (offer.getStatus() != DeliveryOfferStatus.PENDING) {
            log.info("Offer {} is no longer PENDING (current: {}), skipping expiration", offerId, offer.getStatus());
            return Optional.empty();
        }

        Order order = orderRepository.findById(offer.getDeliveryId()).orElse(null);
        if (order == null || order.getStatus() == OrderStatus.ASSIGNED || order.getDeliveryPartner() != null) {
            log.info("Order {} is already assigned or null, skipping offer expiration", offer.getDeliveryId());
            offer.setStatus(DeliveryOfferStatus.CANCELLED);
            offer.setRespondedAt(Instant.now());
            deliveryOfferRepository.save(offer);
            return Optional.empty();
        }

        offer.setStatus(DeliveryOfferStatus.EXPIRED);
        offer.setRespondedAt(Instant.now());
        DeliveryOffer savedOffer = deliveryOfferRepository.save(offer);

        DeliveryPartner partner = deliveryPartnerRepository.findById(offer.getPartnerId()).orElse(null);

        orderEventPublisher.publishDeliveryOfferExpired(savedOffer, order, partner);

        log.info("Offer {} for order {} EXPIRED. Advancing to next candidate.", offer.getId(), order.getId());

        advanceAssignment(order);

        return Optional.of(savedOffer);
    }

    public Optional<DeliveryOffer> getOffer(Long offerId) {
        return deliveryOfferRepository.findById(offerId);
    }

    public List<DeliveryOffer> getOffersForDelivery(Long deliveryId) {
        return deliveryOfferRepository.findByDeliveryIdOrderByCreatedAtDesc(deliveryId);
    }
}
