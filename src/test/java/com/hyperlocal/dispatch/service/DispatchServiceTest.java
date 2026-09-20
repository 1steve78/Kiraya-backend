package com.hyperlocal.dispatch.service;

import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;
import com.hyperlocal.dispatch.dto.DispatchCandidateScore;
import com.hyperlocal.dispatch.dto.DispatchDecision;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.dispatch.strategy.DispatchScoringStrategy;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private EligiblePartnerService eligiblePartnerService;

    @Mock
    private DispatchScoringStrategy scoringStrategy;

    @InjectMocks
    private DispatchService dispatchService;

    private Shop shop;
    private Order order;
    private DeliveryPartnerCandidate candidateA;
    private DeliveryPartnerCandidate candidateB;
    private DeliveryPartnerCandidate candidateC;
    private DeliveryPartner partner;

    @BeforeEach
    void setUp() {
        shop = new Shop(1L, "Fresh Store", "123 Main St", "+919876543210");
        shop.setLatitude(22.0);
        shop.setLongitude(88.0);

        order = new Order();
        order.setId(100L);
        order.setShop(shop);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setTotalAmount(BigDecimal.valueOf(500.0));

        candidateA = new DeliveryPartnerCandidate(1L, 22.01, 88.01, 0.4, Instant.now().minusSeconds(5), AvailabilityStatus.ONLINE, true, null);
        candidateB = new DeliveryPartnerCandidate(2L, 22.02, 88.02, 0.8, Instant.now().minusSeconds(10), AvailabilityStatus.ONLINE, true, null);
        candidateC = new DeliveryPartnerCandidate(3L, 22.03, 88.03, 1.2, Instant.now().minusSeconds(15), AvailabilityStatus.ONLINE, true, null);

        partner = new DeliveryPartner();
        partner.setId(1L);
    }

    @Test
    @DisplayName("Should successfully dispatch order to single available partner")
    void testDispatchOrder_Success_SinglePartner() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(eligiblePartnerService.findEligiblePartners(anyDouble(), anyDouble())).thenReturn(List.of(candidateA));
        when(orderRepository.countActiveOrdersForPartner(eq(1L), any())).thenReturn(1);
        when(scoringStrategy.calculateScore(eq(candidateA), eq(1))).thenReturn(0.4);
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partner));

        dispatchService.dispatchOrder(100L);
        
        verify(orderEventPublisher).publishNewDelivery(eq(order), argThat(list -> list.size() == 1 && list.get(0).getId().equals(1L)));
    }

    @Test
    @DisplayName("Test 1: No candidates")
    void testFindBestPartner_NoCandidates() {
        DispatchDecision decision = dispatchService.findBestPartner(shop.getLocation(), List.of());
        assertNull(decision.getSelectedPartnerId());
        assertTrue(decision.getCandidates().isEmpty());
    }

    @Test
    @DisplayName("Test 2: One candidate")
    void testFindBestPartner_OneCandidate() {
        when(orderRepository.countActiveOrdersForPartner(eq(1L), any())).thenReturn(0);
        when(scoringStrategy.calculateScore(eq(candidateA), eq(0))).thenReturn(0.4);

        DispatchDecision decision = dispatchService.findBestPartner(shop.getLocation(), List.of(candidateA));
        assertEquals(1L, decision.getSelectedPartnerId());
        assertEquals(1, decision.getCandidates().size());
    }

    @Test
    @DisplayName("Test 3: Multiple candidates")
    void testFindBestPartner_MultipleCandidates() {
        when(orderRepository.countActiveOrdersForPartner(anyLong(), any())).thenReturn(0);
        when(scoringStrategy.calculateScore(eq(candidateA), eq(0))).thenReturn(0.4);
        when(scoringStrategy.calculateScore(eq(candidateB), eq(0))).thenReturn(0.8);
        when(scoringStrategy.calculateScore(eq(candidateC), eq(0))).thenReturn(1.2);

        DispatchDecision decision = dispatchService.findBestPartner(shop.getLocation(), List.of(candidateB, candidateA, candidateC));
        assertEquals(1L, decision.getSelectedPartnerId(), "Candidate A should be selected because it has the lowest score");
        
        // Test 5: Ordering
        assertEquals(1L, decision.getCandidates().get(0).getPartnerId());
        assertEquals(2L, decision.getCandidates().get(1).getPartnerId());
        assertEquals(3L, decision.getCandidates().get(2).getPartnerId());
    }

    @Test
    @DisplayName("Test 4: Tie breaking rules")
    void testFindBestPartner_TieBreaker() {
        // Same score, let's say 1.0
        candidateA.setDistanceKm(1.0);
        candidateB.setDistanceKm(1.0);
        
        // A is older (less fresh)
        candidateA.setLastSeen(Instant.now().minusSeconds(20));
        // B is fresher
        candidateB.setLastSeen(Instant.now().minusSeconds(5));

        when(orderRepository.countActiveOrdersForPartner(anyLong(), any())).thenReturn(0);
        when(scoringStrategy.calculateScore(any(), anyInt())).thenReturn(1.0);

        DispatchDecision decision = dispatchService.findBestPartner(shop.getLocation(), List.of(candidateA, candidateB));
        
        // B should win due to freshness
        assertEquals(2L, decision.getSelectedPartnerId());

        // Now test workload tie breaker: same score, same freshness, different workload
        candidateA.setLastSeen(candidateB.getLastSeen());
        when(orderRepository.countActiveOrdersForPartner(eq(1L), any())).thenReturn(1);
        when(orderRepository.countActiveOrdersForPartner(eq(2L), any())).thenReturn(2);

        DispatchDecision decision2 = dispatchService.findBestPartner(shop.getLocation(), List.of(candidateA, candidateB));
        
        // A should win due to lower workload
        assertEquals(1L, decision2.getSelectedPartnerId());

        // Now test ID tie breaker: same score, same freshness, same workload
        when(orderRepository.countActiveOrdersForPartner(anyLong(), any())).thenReturn(1);
        
        DispatchDecision decision3 = dispatchService.findBestPartner(shop.getLocation(), List.of(candidateB, candidateA)); // shuffle input
        
        // A should win because 1L < 2L
        assertEquals(1L, decision3.getSelectedPartnerId());
    }
}
