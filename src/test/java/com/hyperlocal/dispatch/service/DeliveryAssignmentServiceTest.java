package com.hyperlocal.dispatch.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;
import com.hyperlocal.dispatch.dto.DispatchCandidateScore;
import com.hyperlocal.dispatch.dto.PartnerPresence;
import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import com.hyperlocal.dispatch.exception.InvalidAssignmentStateException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceTest {

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

    @InjectMocks
    private DeliveryAssignmentService assignmentService;

    private Shop shop;
    private Order order;
    private DeliveryPartnerCandidate candidateA;
    private DeliveryPartnerCandidate candidateB;
    private DeliveryPartnerCandidate candidateC;
    private DispatchCandidateScore scoreA;
    private DispatchCandidateScore scoreB;
    private DispatchCandidateScore scoreC;
    private DeliveryPartner partnerA;
    private DeliveryPartner partnerB;

    @BeforeEach
    void setUp() {
        shop = new Shop(1L, "Fresh Store", "123 Main St", "+919876543210");
        shop.setLatitude(22.0);
        shop.setLongitude(88.0);

        order = new Order();
        order.setId(100L);
        order.setShop(shop);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);

        candidateA = new DeliveryPartnerCandidate(1L, 22.01, 88.01, 0.4, Instant.now(), AvailabilityStatus.ONLINE, true, null);
        candidateB = new DeliveryPartnerCandidate(2L, 22.02, 88.02, 0.7, Instant.now(), AvailabilityStatus.ONLINE, true, null);
        candidateC = new DeliveryPartnerCandidate(3L, 22.03, 88.03, 1.1, Instant.now(), AvailabilityStatus.ONLINE, true, null);

        scoreA = new DispatchCandidateScore(1L, 0.4, 5L, null, 0.4, 0);
        scoreB = new DispatchCandidateScore(2L, 0.7, 10L, null, 0.7, 0);
        scoreC = new DispatchCandidateScore(3L, 1.1, 15L, null, 1.1, 0);

        User userA = new User();
        userA.setId(101L);
        userA.setEmail("partnerA@example.com");

        partnerA = new DeliveryPartner();
        partnerA.setId(1L);
        partnerA.setUser(userA);
        partnerA.setAvailable(true);

        User userB = new User();
        userB.setId(102L);
        userB.setEmail("partnerB@example.com");

        partnerB = new DeliveryPartner();
        partnerB.setId(2L);
        partnerB.setUser(userB);
        partnerB.setAvailable(true);
    }

    @Test
    @DisplayName("Test 1: First candidate gets offer ([A, B, C] -> A receives offer)")
    void test1_FirstCandidateGetsOffer() {
        when(eligiblePartnerService.findEligiblePartners(22.0, 88.0))
                .thenReturn(List.of(candidateA, candidateB, candidateC));
        when(dispatchService.rankCandidates(any()))
                .thenReturn(List.of(scoreA, scoreB, scoreC));
        when(deliveryPartnerRepository.findById(1L))
                .thenReturn(Optional.of(partnerA));
        when(deliveryOfferRepository.save(any(DeliveryOffer.class)))
                .thenAnswer(invocation -> {
                    DeliveryOffer offer = invocation.getArgument(0);
                    offer.setId(10L);
                    return offer;
                });

        Optional<DeliveryOffer> offerOpt = assignmentService.startAssignment(order);

        assertTrue(offerOpt.isPresent());
        DeliveryOffer offer = offerOpt.get();
        assertEquals(10L, offer.getId());
        assertEquals(100L, offer.getDeliveryId());
        assertEquals(1L, offer.getPartnerId());
        assertEquals(DeliveryOfferStatus.PENDING, offer.getStatus());
        assertEquals(OrderStatus.OFFERED, order.getStatus());

        verify(orderRepository).save(order);
        verify(orderEventPublisher).publishDeliveryOfferCreated(eq(offer), eq(order), eq(partnerA));
        verify(deliveryPartnerRepository, never()).findById(2L);
    }

    @Test
    @DisplayName("Test 2: A rejects -> B receives offer sequentially")
    void test2_PartnerARejects_PartnerBReceivesOffer() {
        DeliveryOffer offerA = new DeliveryOffer(100L, 1L, Instant.now().plusSeconds(30));
        offerA.setId(10L);
        offerA.setStatus(DeliveryOfferStatus.PENDING);

        order.setStatus(OrderStatus.OFFERED);

        when(deliveryOfferRepository.findById(10L)).thenReturn(Optional.of(offerA));
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partnerA));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryOfferRepository.save(offerA)).thenReturn(offerA);

        // Discovery for next candidate
        when(eligiblePartnerService.findEligiblePartners(22.0, 88.0))
                .thenReturn(List.of(candidateA, candidateB, candidateC));
        when(dispatchService.rankCandidates(any()))
                .thenReturn(List.of(scoreA, scoreB, scoreC));
        // A has already been offered
        when(deliveryOfferRepository.findByDeliveryId(100L)).thenReturn(List.of(offerA));
        when(deliveryPartnerRepository.findById(2L)).thenReturn(Optional.of(partnerB));
        when(deliveryOfferRepository.save(argThat(o -> o.getPartnerId().equals(2L))))
                .thenAnswer(invocation -> {
                    DeliveryOffer o = invocation.getArgument(0);
                    o.setId(20L);
                    return o;
                });

        DeliveryOffer rejectedOffer = assignmentService.rejectOffer(10L, 1L);

        assertEquals(DeliveryOfferStatus.REJECTED, rejectedOffer.getStatus());
        assertNotNull(rejectedOffer.getRespondedAt());
        verify(orderEventPublisher).publishDeliveryOfferRejected(eq(offerA), eq(order), eq(partnerA));

        // Partner B received the next offer
        verify(orderEventPublisher).publishDeliveryOfferCreated(
                argThat(o -> o.getId().equals(20L) && o.getPartnerId().equals(2L)),
                eq(order),
                eq(partnerB)
        );
    }

    @Test
    @DisplayName("Test 3: B accepts -> Delivery = ASSIGNED, B = BUSY")
    void test3_PartnerBAccepts_AssignedAndBusy() {
        DeliveryOffer offerB = new DeliveryOffer(100L, 2L, Instant.now().plusSeconds(30));
        offerB.setId(20L);
        offerB.setStatus(DeliveryOfferStatus.PENDING);

        order.setStatus(OrderStatus.OFFERED);

        when(deliveryOfferRepository.findById(20L)).thenReturn(Optional.of(offerB));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(2L)).thenReturn(Optional.of(partnerB));
        when(presenceService.getPresence(2L)).thenReturn(Optional.of(new PartnerPresence(2L, AvailabilityStatus.ONLINE, Instant.now())));
        when(deliveryOfferRepository.save(offerB)).thenReturn(offerB);
        when(orderRepository.save(order)).thenReturn(order);

        DeliveryOffer acceptedOffer = assignmentService.acceptOffer(20L, 2L);

        assertEquals(DeliveryOfferStatus.ACCEPTED, acceptedOffer.getStatus());
        assertNotNull(acceptedOffer.getRespondedAt());
        assertEquals(OrderStatus.ASSIGNED, order.getStatus());
        assertEquals(partnerB, order.getDeliveryPartner());
        assertFalse(partnerB.isAvailable());

        verify(deliveryPartnerRepository).save(partnerB);
        verify(presenceService).setBusy(2L);
        verify(orderEventPublisher).publishDeliveryOfferAccepted(eq(offerB), eq(order), eq(partnerB));
        verify(orderEventPublisher).publishOrderAssigned(order);
    }

    @Test
    @DisplayName("Test 4: No candidates -> assignment fails gracefully")
    void test4_NoCandidates_FailsGracefully() {
        when(eligiblePartnerService.findEligiblePartners(22.0, 88.0)).thenReturn(List.of());

        Optional<DeliveryOffer> result = assignmentService.startAssignment(order);

        assertTrue(result.isEmpty());
        assertEquals(OrderStatus.READY_FOR_PICKUP, order.getStatus());
        verify(deliveryOfferRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    @DisplayName("Test 5: Already assigned -> cannot create another assignment")
    void test5_AlreadyAssigned_ThrowsException() {
        order.setStatus(OrderStatus.ASSIGNED);
        order.setDeliveryPartner(partnerA);

        assertThrows(InvalidAssignmentStateException.class, () -> assignmentService.startAssignment(order));
        verify(deliveryOfferRepository, never()).save(any());
    }

    @Test
    @DisplayName("Test 6: Already accepted offer -> second accept request rejected")
    void test6_AlreadyAcceptedOffer_SecondAcceptRejected() {
        DeliveryOffer offer = new DeliveryOffer(100L, 1L, Instant.now().plusSeconds(30));
        offer.setId(10L);
        offer.setStatus(DeliveryOfferStatus.ACCEPTED); // already accepted

        when(deliveryOfferRepository.findById(10L)).thenReturn(Optional.of(offer));

        assertThrows(InvalidAssignmentStateException.class, () -> assignmentService.acceptOffer(10L, 1L));
        verify(presenceService, never()).setBusy(anyLong());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Test 7: Partner not eligible (Partner = BUSY) -> cannot accept new delivery")
    void test7_PartnerBusy_CannotAcceptNewDelivery() {
        DeliveryOffer offer = new DeliveryOffer(100L, 1L, Instant.now().plusSeconds(30));
        offer.setId(10L);
        offer.setStatus(DeliveryOfferStatus.PENDING);

        order.setStatus(OrderStatus.OFFERED);

        when(deliveryOfferRepository.findById(10L)).thenReturn(Optional.of(offer));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partnerA));
        // Partner is currently BUSY in Redis presence
        when(presenceService.getPresence(1L)).thenReturn(Optional.of(new PartnerPresence(1L, AvailabilityStatus.BUSY, Instant.now())));

        assertThrows(InvalidAssignmentStateException.class, () -> assignmentService.acceptOffer(10L, 1L));

        assertEquals(OrderStatus.OFFERED, order.getStatus());
        assertNull(order.getDeliveryPartner());
        assertEquals(DeliveryOfferStatus.PENDING, offer.getStatus());
        verify(orderRepository, never()).save(order);
    }
}
