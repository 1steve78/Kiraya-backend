package com.hyperlocal.dispatch.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.dispatch.entity.DeliveryAssignment;
import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.enums.DeliveryAssignmentStatus;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import com.hyperlocal.dispatch.repository.DeliveryAssignmentRepository;
import com.hyperlocal.dispatch.repository.DeliveryOfferRepository;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.notification.service.OrderEventPublisher;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferExpirationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryOfferRepository deliveryOfferRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Mock
    private EligiblePartnerService eligiblePartnerService;

    @Mock
    private DispatchService dispatchService;

    @Mock
    private PresenceService presenceService;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private DeliveryAssignmentService assignmentService;
    private OfferExpirationService expirationService;

    private Order order;
    private DeliveryPartner partnerA;
    private DeliveryPartner partnerB;
    private DeliveryPartner partnerC;

    @BeforeEach
    void setUp() {
        assignmentService = new DeliveryAssignmentService(
                orderRepository,
                deliveryOfferRepository,
                deliveryPartnerRepository,
                deliveryAssignmentRepository,
                eligiblePartnerService,
                dispatchService,
                presenceService,
                orderEventPublisher
        );
        assignmentService.setOfferTimeoutSeconds(15);
        assignmentService.setMaxAttempts(5);

        expirationService = new OfferExpirationService(deliveryOfferRepository, assignmentService);

        Shop shop = new Shop(1L, "Test Shop", "Address", "1234567890");
        shop.setLatitude(22.0);
        shop.setLongitude(88.0);

        order = new Order();
        order.setId(100L);
        order.setShop(shop);
        order.setStatus(OrderStatus.OFFERED);

        User userA = new User();
        userA.setId(1L);
        userA.setEmail("partnerA@example.com");
        partnerA = new DeliveryPartner();
        partnerA.setId(1L);
        partnerA.setUser(userA);

        User userB = new User();
        userB.setId(2L);
        userB.setEmail("partnerB@example.com");
        partnerB = new DeliveryPartner();
        partnerB.setId(2L);
        partnerB.setUser(userB);

        User userC = new User();
        userC.setId(3L);
        userC.setEmail("partnerC@example.com");
        partnerC = new DeliveryPartner();
        partnerC.setId(3L);
        partnerC.setUser(userC);

        lenient().when(deliveryOfferRepository.updateOfferStatusConditionally(any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("Test 1: Offer expires via scheduler (expiresAt < now -> EXPIRED)")
    void test1_OfferExpiresViaScheduler() {
        Instant now = Instant.now();
        DeliveryOffer offerA = new DeliveryOffer(100L, 1L, now.minusSeconds(1));
        offerA.setId(10L);
        offerA.setStatus(DeliveryOfferStatus.PENDING);

        when(deliveryOfferRepository.findByStatusAndExpiresAtBefore(eq(DeliveryOfferStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(offerA));
        when(deliveryOfferRepository.findById(10L)).thenReturn(Optional.of(offerA));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partnerA));
        when(deliveryOfferRepository.save(offerA)).thenReturn(offerA);

        List<DeliveryOffer> expiredList = expirationService.processExpiredOffersAt(now);

        assertEquals(1, expiredList.size());
        assertEquals(DeliveryOfferStatus.EXPIRED, offerA.getStatus());
        assertNotNull(offerA.getRespondedAt());
        verify(orderEventPublisher).publishDeliveryOfferExpired(eq(offerA), eq(order), eq(partnerA));
    }

    @Test
    @DisplayName("Test 2: Expired offer triggers next candidate (A -> EXPIRED -> B gets offer)")
    void test2_ExpiredOfferTriggersNextCandidate() {
        DeliveryOffer offerA = new DeliveryOffer(100L, 1L, Instant.now().minusSeconds(1));
        offerA.setId(10L);
        offerA.setStatus(DeliveryOfferStatus.PENDING);

        DeliveryAssignment assignment = new DeliveryAssignment(100L, List.of(1L, 2L));
        assignment.setCurrentCandidateIndex(0);
        assignment.setStatus(DeliveryAssignmentStatus.IN_PROGRESS);

        when(deliveryOfferRepository.findById(10L)).thenReturn(Optional.of(offerA));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partnerA));
        when(deliveryPartnerRepository.findById(2L)).thenReturn(Optional.of(partnerB));
        when(deliveryOfferRepository.save(offerA)).thenReturn(offerA);

        when(deliveryAssignmentRepository.findFirstByDeliveryIdOrderByCreatedAtDesc(100L))
                .thenReturn(Optional.of(assignment));
        when(deliveryOfferRepository.save(argThat(o -> o.getPartnerId().equals(2L))))
                .thenAnswer(inv -> {
                    DeliveryOffer o = inv.getArgument(0);
                    o.setId(20L);
                    return o;
                });

        Optional<DeliveryOffer> expiredResult = assignmentService.expireOffer(10L);

        assertTrue(expiredResult.isPresent());
        assertEquals(DeliveryOfferStatus.EXPIRED, expiredResult.get().getStatus());
        assertEquals(1, assignment.getCurrentCandidateIndex());

        // Partner B received the offer
        verify(orderEventPublisher).publishDeliveryOfferCreated(
                argThat(o -> o.getPartnerId().equals(2L)),
                eq(order),
                eq(partnerB)
        );
    }

    @Test
    @DisplayName("Test 3: Rejection triggers next candidate (A -> REJECTED -> B gets offer)")
    void test3_RejectionTriggersNextCandidate() {
        DeliveryOffer offerA = new DeliveryOffer(100L, 1L, Instant.now().plusSeconds(15));
        offerA.setId(10L);
        offerA.setStatus(DeliveryOfferStatus.PENDING);

        DeliveryAssignment assignment = new DeliveryAssignment(100L, List.of(1L, 2L));
        assignment.setCurrentCandidateIndex(0);
        assignment.setStatus(DeliveryAssignmentStatus.IN_PROGRESS);

        when(deliveryOfferRepository.findById(10L)).thenReturn(Optional.of(offerA));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partnerA));
        when(deliveryPartnerRepository.findById(2L)).thenReturn(Optional.of(partnerB));
        when(deliveryOfferRepository.save(offerA)).thenReturn(offerA);

        when(deliveryAssignmentRepository.findFirstByDeliveryIdOrderByCreatedAtDesc(100L))
                .thenReturn(Optional.of(assignment));
        when(deliveryOfferRepository.save(argThat(o -> o.getPartnerId().equals(2L))))
                .thenAnswer(inv -> {
                    DeliveryOffer o = inv.getArgument(0);
                    o.setId(20L);
                    return o;
                });

        DeliveryOffer rejectedOffer = assignmentService.rejectOffer(10L, 1L);

        assertEquals(DeliveryOfferStatus.REJECTED, rejectedOffer.getStatus());
        assertEquals(1, assignment.getCurrentCandidateIndex());

        verify(orderEventPublisher).publishDeliveryOfferRejected(eq(offerA), eq(order), eq(partnerA));
        verify(orderEventPublisher).publishDeliveryOfferCreated(
                argThat(o -> o.getPartnerId().equals(2L)),
                eq(order),
                eq(partnerB)
        );
    }

    @Test
    @DisplayName("Test 4: Acceptance prevents expiration (A -> ACCEPTED, scheduler runs -> remains ACCEPTED, ASSIGNED)")
    void test4_AcceptancePreventsExpiration() {
        DeliveryOffer offerA = new DeliveryOffer(100L, 1L, Instant.now().minusSeconds(1));
        offerA.setId(10L);
        offerA.setStatus(DeliveryOfferStatus.ACCEPTED); // already accepted

        order.setStatus(OrderStatus.ASSIGNED);
        order.setDeliveryPartner(partnerA);

        when(deliveryOfferRepository.findById(10L)).thenReturn(Optional.of(offerA));

        Optional<DeliveryOffer> result = assignmentService.expireOffer(10L);

        assertTrue(result.isEmpty());
        assertEquals(DeliveryOfferStatus.ACCEPTED, offerA.getStatus());
        assertEquals(OrderStatus.ASSIGNED, order.getStatus());
        verify(orderEventPublisher, never()).publishDeliveryOfferExpired(any(), any(), any());
    }

    @Test
    @DisplayName("Test 5: All candidates fail (A -> EXPIRED, B -> REJECTED, C -> EXPIRED -> NO_PARTNER_AVAILABLE / ASSIGNMENT_FAILED)")
    void test5_AllCandidatesFail_NoPartnerAvailable() {
        DeliveryAssignment assignment = new DeliveryAssignment(100L, List.of(1L, 2L, 3L));
        assignment.setCurrentCandidateIndex(2); // C is candidate index 2 (last candidate)
        assignment.setStatus(DeliveryAssignmentStatus.IN_PROGRESS);

        DeliveryOffer offerC = new DeliveryOffer(100L, 3L, Instant.now().minusSeconds(1));
        offerC.setId(30L);
        offerC.setStatus(DeliveryOfferStatus.PENDING);

        when(deliveryOfferRepository.findById(30L)).thenReturn(Optional.of(offerC));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(3L)).thenReturn(Optional.of(partnerC));
        when(deliveryOfferRepository.save(offerC)).thenReturn(offerC);
        when(deliveryAssignmentRepository.findFirstByDeliveryIdOrderByCreatedAtDesc(100L))
                .thenReturn(Optional.of(assignment));

        Optional<DeliveryOffer> result = assignmentService.expireOffer(30L);

        assertTrue(result.isPresent());
        assertEquals(DeliveryOfferStatus.EXPIRED, result.get().getStatus());
        assertEquals(DeliveryAssignmentStatus.FAILED, assignment.getStatus());
        assertEquals(OrderStatus.ASSIGNMENT_FAILED, order.getStatus());

        verify(orderRepository).save(order);
        verify(deliveryAssignmentRepository).save(assignment);
    }

    @Test
    @DisplayName("Test 6: Maximum attempts (5 attempts -> stop)")
    void test6_MaximumAttempts_StopsAtLimit() {
        // maxAttempts is 5. Candidate list has 6 candidates: [1L, 2L, 3L, 4L, 5L, 6L]
        DeliveryAssignment assignment = new DeliveryAssignment(100L, List.of(1L, 2L, 3L, 4L, 5L));
        assignment.setCurrentCandidateIndex(4); // index 4 is the 5th attempt
        assignment.setStatus(DeliveryAssignmentStatus.IN_PROGRESS);

        DeliveryOffer offer5 = new DeliveryOffer(100L, 5L, Instant.now().minusSeconds(1));
        offer5.setId(50L);
        offer5.setStatus(DeliveryOfferStatus.PENDING);

        when(deliveryOfferRepository.findById(50L)).thenReturn(Optional.of(offer5));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        DeliveryPartner partner5 = new DeliveryPartner();
        partner5.setId(5L);
        when(deliveryPartnerRepository.findById(5L)).thenReturn(Optional.of(partner5));
        when(deliveryOfferRepository.save(offer5)).thenReturn(offer5);
        when(deliveryAssignmentRepository.findFirstByDeliveryIdOrderByCreatedAtDesc(100L))
                .thenReturn(Optional.of(assignment));

        Optional<DeliveryOffer> result = assignmentService.expireOffer(50L);

        assertTrue(result.isPresent());
        assertEquals(DeliveryAssignmentStatus.FAILED, assignment.getStatus());
        assertEquals(OrderStatus.ASSIGNMENT_FAILED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Test 7: Duplicate scheduler execution (Scheduler #1 expires A, Scheduler #2 sees A already expired -> no duplicate next offer)")
    void test7_DuplicateSchedulerExecution_NoDuplicateNextOffer() {
        DeliveryOffer offerA = new DeliveryOffer(100L, 1L, Instant.now().minusSeconds(1));
        offerA.setId(10L);
        offerA.setStatus(DeliveryOfferStatus.PENDING);

        DeliveryAssignment assignment = new DeliveryAssignment(100L, List.of(1L, 2L));
        assignment.setCurrentCandidateIndex(0);
        assignment.setStatus(DeliveryAssignmentStatus.IN_PROGRESS);

        when(deliveryOfferRepository.findById(10L)).thenReturn(Optional.of(offerA));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partnerA));
        when(deliveryPartnerRepository.findById(2L)).thenReturn(Optional.of(partnerB));
        when(deliveryOfferRepository.save(any(DeliveryOffer.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(deliveryAssignmentRepository.findFirstByDeliveryIdOrderByCreatedAtDesc(100L))
                .thenReturn(Optional.of(assignment));

        // Scheduler #1 runs
        Optional<DeliveryOffer> run1 = assignmentService.expireOffer(10L);
        assertTrue(run1.isPresent());
        assertEquals(DeliveryOfferStatus.EXPIRED, offerA.getStatus());

        // Scheduler #2 runs on the same offer (offerA is now EXPIRED)
        Optional<DeliveryOffer> run2 = assignmentService.expireOffer(10L);
        assertTrue(run2.isEmpty());

        // Verify offer for Partner B was only created once!
        verify(orderEventPublisher, times(1)).publishDeliveryOfferCreated(
                argThat(o -> o.getPartnerId().equals(2L)),
                eq(order),
                eq(partnerB)
        );
    }
}
