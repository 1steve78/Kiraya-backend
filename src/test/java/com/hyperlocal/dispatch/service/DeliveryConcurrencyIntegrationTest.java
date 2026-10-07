package com.hyperlocal.dispatch.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.Role;
import com.hyperlocal.auth.enums.UserStatus;
import com.hyperlocal.auth.repository.UserRepository;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.catalog.enums.ShopStatus;
import com.hyperlocal.catalog.repository.ShopRepository;
import com.hyperlocal.dispatch.dto.PartnerPresence;
import com.hyperlocal.dispatch.entity.DeliveryAssignment;
import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.enums.AvailabilityStatus;
import com.hyperlocal.dispatch.enums.DeliveryAssignmentStatus;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import com.hyperlocal.dispatch.exception.InvalidAssignmentStateException;
import com.hyperlocal.dispatch.repository.DeliveryAssignmentRepository;
import com.hyperlocal.dispatch.repository.DeliveryOfferRepository;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.notification.service.OrderEventPublisher;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.exception.InvalidOrderStateException;
import com.hyperlocal.order.repository.OrderRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class DeliveryConcurrencyIntegrationTest {

    @Autowired
    private DeliveryAssignmentService deliveryAssignmentService;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryOfferRepository deliveryOfferRepository;

    @Autowired
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Autowired
    private DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private PresenceService presenceService;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    @MockBean
    private OfferExpirationService offerExpirationService;

    private User customer;
    private User userA;
    private User userB;
    private DeliveryPartner partnerA;
    private DeliveryPartner partnerB;
    private Shop shop;
    private Order order;
    private DeliveryOffer offerA;
    private DeliveryOffer offerB;

    @BeforeEach
    void setUp() {
        deliveryOfferRepository.deleteAll();
        deliveryAssignmentRepository.deleteAll();
        orderRepository.deleteAll();
        deliveryPartnerRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();

        // Setup users
        customer = new User();
        customer.setName("Alice Customer");
        customer.setEmail("alice@test.com");
        customer.setPassword("encoded_pwd");
        customer.setRole(Role.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        customer = userRepository.save(customer);

        userA = new User();
        userA.setName("Partner A");
        userA.setEmail("partnerA@test.com");
        userA.setPassword("encoded_pwd");
        userA.setRole(Role.DELIVERY_PARTNER);
        userA.setStatus(UserStatus.ACTIVE);
        userA = userRepository.save(userA);

        userB = new User();
        userB.setName("Partner B");
        userB.setEmail("partnerB@test.com");
        userB.setPassword("encoded_pwd");
        userB.setRole(Role.DELIVERY_PARTNER);
        userB.setStatus(UserStatus.ACTIVE);
        userB = userRepository.save(userB);

        // Setup Shop
        shop = new Shop();
        shop.setName("Test Bakery");
        shop.setAddress("123 Test St");
        shop.setPhone("+919876543210");
        shop.setOwner(customer);
        shop.setStatus(ShopStatus.APPROVED);
        shop.setLatitude(12.9716);
        shop.setLongitude(77.5946);
        shop = shopRepository.save(shop);

        // Setup Partners
        partnerA = new DeliveryPartner();
        partnerA.setUser(userA);
        partnerA.setAvailable(true);
        partnerA.setVehicleType("BIKE");
        partnerA.setVehicleNumber("KA01AB1111");
        partnerA.setLatitude(12.9720);
        partnerA.setLongitude(77.5950);
        partnerA = deliveryPartnerRepository.save(partnerA);

        partnerB = new DeliveryPartner();
        partnerB.setUser(userB);
        partnerB.setAvailable(true);
        partnerB.setVehicleType("SCOOTER");
        partnerB.setVehicleNumber("KA01AB2222");
        partnerB.setLatitude(12.9730);
        partnerB.setLongitude(77.5960);
        partnerB = deliveryPartnerRepository.save(partnerB);

        // Setup Order in OFFERED state
        order = new Order();
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.OFFERED);
        order.setTotalAmount(BigDecimal.valueOf(350.00));
        order = orderRepository.save(order);

        // Setup Offers for both Partner A and Partner B
        offerA = new DeliveryOffer(order.getId(), partnerA.getId(), Instant.now().plusSeconds(30));
        offerA.setStatus(DeliveryOfferStatus.PENDING);
        offerA = deliveryOfferRepository.save(offerA);

        offerB = new DeliveryOffer(order.getId(), partnerB.getId(), Instant.now().plusSeconds(30));
        offerB.setStatus(DeliveryOfferStatus.PENDING);
        offerB = deliveryOfferRepository.save(offerB);

        // Setup Assignment context
        DeliveryAssignment assignment = new DeliveryAssignment(order.getId(), List.of(partnerA.getId(), partnerB.getId()));
        assignment.setCurrentCandidateIndex(0);
        assignment.setStatus(DeliveryAssignmentStatus.IN_PROGRESS);
        deliveryAssignmentRepository.save(assignment);

        // Mock Presence to ONLINE
        when(presenceService.getPresence(anyLong())).thenReturn(
                Optional.of(new PartnerPresence(1L, AvailabilityStatus.ONLINE, Instant.now()))
        );
    }

    @Test
    @DisplayName("Day 29 Concurrency: Partner A & Partner B accept simultaneously -> Exactly ONE wins (successCount == 1)")
    void testConcurrentAccept_TwoPartners_OnlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        Long offerAId = offerA.getId();
        Long partnerAId = partnerA.getId();
        Long offerBId = offerB.getId();
        Long partnerBId = partnerB.getId();

        Future<?> futureA = executor.submit(() -> {
            try {
                barrier.await();
                deliveryAssignmentService.acceptOffer(offerAId, partnerAId);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | InvalidAssignmentStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
        });

        Future<?> futureB = executor.submit(() -> {
            try {
                barrier.await();
                deliveryAssignmentService.acceptOffer(offerBId, partnerBId);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | InvalidAssignmentStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
        });

        futureA.get(5, TimeUnit.SECONDS);
        futureB.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Unexpected exceptions occurred: " + unexpectedExceptions);
        assertEquals(1, successCount.get(), "Only one partner should successfully accept the delivery");
        assertEquals(1, conflictCount.get(), "Conflicting partner must be rejected with concurrency failure");

        // Verify Database State
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.ASSIGNED, updatedOrder.getStatus());
        assertNotNull(updatedOrder.getDeliveryPartner());
        assertTrue(updatedOrder.getDeliveryPartner().getId().equals(partnerA.getId()) ||
                   updatedOrder.getDeliveryPartner().getId().equals(partnerB.getId()));
        assertNotNull(updatedOrder.getVersion(), "Order version must not be null");
        assertTrue(updatedOrder.getVersion() >= 1L, "Order version must be incremented upon assignment");

        // Exactly one offer is ACCEPTED
        List<DeliveryOffer> offers = deliveryOfferRepository.findByDeliveryId(order.getId());
        long acceptedOffersCount = offers.stream().filter(o -> o.getStatus() == DeliveryOfferStatus.ACCEPTED).count();
        assertEquals(1, acceptedOffersCount, "Exactly 1 offer should be in ACCEPTED status in DB");
    }

    @Test
    @DisplayName("Day 29 Concurrency: Same offer accepted twice simultaneously -> Exactly ONE wins (successCount == 1)")
    void testConcurrentAccept_DuplicateAcceptSameOffer_OnlyOneSucceeds() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        Long offerAId = offerA.getId();
        Long partnerAId = partnerA.getId();

        Callable<Void> acceptTask = () -> {
            try {
                barrier.await();
                deliveryAssignmentService.acceptOffer(offerAId, partnerAId);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | InvalidAssignmentStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
            return null;
        };

        Future<?> f1 = executor.submit(acceptTask);
        Future<?> f2 = executor.submit(acceptTask);

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Unexpected exceptions occurred: " + unexpectedExceptions);
        assertEquals(1, successCount.get(), "Exactly one duplicate accept request should succeed");
        assertEquals(1, conflictCount.get(), "Second duplicate accept must be rejected with concurrency conflict");

        DeliveryOffer finalOffer = deliveryOfferRepository.findById(offerAId).orElseThrow();
        assertEquals(DeliveryOfferStatus.ACCEPTED, finalOffer.getStatus());
    }

    @Test
    @DisplayName("Day 29 Concurrency: Direct pool claim via DeliveryService -> Exactly ONE partner wins")
    void testConcurrentAccept_DirectPoolClaim_OnlyOneSucceeds() throws Exception {
        Order poolOrder = new Order();
        poolOrder.setCustomer(customer);
        poolOrder.setShop(shop);
        poolOrder.setStatus(OrderStatus.READY_FOR_PICKUP);
        poolOrder.setTotalAmount(BigDecimal.valueOf(180.00));
        poolOrder = orderRepository.save(poolOrder);

        final Long poolOrderId = poolOrder.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        Long userAId = userA.getId();
        Long userBId = userB.getId();

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                deliveryService.updateDeliveryStatus(userAId, poolOrderId, OrderStatus.ACCEPTED);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | InvalidOrderStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                deliveryService.updateDeliveryStatus(userBId, poolOrderId, OrderStatus.ACCEPTED);
                successCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | InvalidOrderStateException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
        });

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Unexpected exceptions occurred: " + unexpectedExceptions);
        assertEquals(1, successCount.get(), "Only one partner can claim the unassigned order");
        assertEquals(1, conflictCount.get(), "Second partner must fail with conflict");

        Order finalOrder = orderRepository.findById(poolOrderId).orElseThrow();
        assertEquals(OrderStatus.ACCEPTED, finalOrder.getStatus());
        assertNotNull(finalOrder.getDeliveryPartner());
    }

    @Test
    @DisplayName("Day 29 Concurrency: Accept vs Expire simultaneously -> Consistent state preserved")
    void testConcurrentRace_AcceptVsExpire_ConsistencyPreserved() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger completedCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        Long offerAId = offerA.getId();
        Long partnerAId = partnerA.getId();

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                deliveryAssignmentService.acceptOffer(offerAId, partnerAId);
                completedCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | InvalidAssignmentStateException e) {
                // Expected if expire won
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                deliveryAssignmentService.expireOffer(offerAId);
                completedCount.incrementAndGet();
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException | InvalidAssignmentStateException e) {
                // Expected if accept won
            } catch (Throwable t) {
                unexpectedExceptions.add(t);
            }
        });

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(unexpectedExceptions.isEmpty(), "Unexpected exceptions occurred: " + unexpectedExceptions);

        // Verification: The offer must be either ACCEPTED or EXPIRED, never inconsistent
        DeliveryOffer finalOffer = deliveryOfferRepository.findById(offerAId).orElseThrow();
        assertTrue(finalOffer.getStatus() == DeliveryOfferStatus.ACCEPTED ||
                   finalOffer.getStatus() == DeliveryOfferStatus.EXPIRED);
    }

    @Test
    @DisplayName("Day 29 - Test 4: Reject after accept -> Second request must fail")
    void testRejectAfterAccept_MustFail() {
        // Partner A accepts the offer
        DeliveryOffer acceptedOffer = deliveryAssignmentService.acceptOffer(offerA.getId(), partnerA.getId());
        assertEquals(DeliveryOfferStatus.ACCEPTED, acceptedOffer.getStatus());

        // Partner A attempts to reject the already-accepted offer
        InvalidAssignmentStateException ex = assertThrows(InvalidAssignmentStateException.class, () -> {
            deliveryAssignmentService.rejectOffer(offerA.getId(), partnerA.getId());
        });

        assertEquals("DELIVERY_ALREADY_ASSIGNED", ex.getCode());
        assertEquals("This delivery is no longer available.", ex.getMessage());

        // Offer in DB remains ACCEPTED
        DeliveryOffer dbOffer = deliveryOfferRepository.findById(offerA.getId()).orElseThrow();
        assertEquals(DeliveryOfferStatus.ACCEPTED, dbOffer.getStatus());

        // Order in DB remains ASSIGNED to Partner A
        Order dbOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.ASSIGNED, dbOrder.getStatus());
        assertEquals(partnerA.getId(), dbOrder.getDeliveryPartner().getId());
    }

    @Test
    @DisplayName("Day 29 - Test 5: Expire after accept -> Scheduler runs, offer remains ACCEPTED")
    void testExpireAfterAccept_SchedulerRuns_OfferRemainsAccepted() {
        // Partner A accepts the offer
        DeliveryOffer acceptedOffer = deliveryAssignmentService.acceptOffer(offerA.getId(), partnerA.getId());
        assertEquals(DeliveryOfferStatus.ACCEPTED, acceptedOffer.getStatus());

        // Expiration runs for this offer (scheduler invocation)
        Optional<DeliveryOffer> expireResult = deliveryAssignmentService.expireOffer(offerA.getId());

        // Must be safely ignored/skipped
        assertTrue(expireResult.isEmpty(), "expireOffer must skip already-accepted offer");

        // Offer in DB remains ACCEPTED
        DeliveryOffer dbOffer = deliveryOfferRepository.findById(offerA.getId()).orElseThrow();
        assertEquals(DeliveryOfferStatus.ACCEPTED, dbOffer.getStatus());

        // Order in DB remains ASSIGNED to Partner A
        Order dbOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.ASSIGNED, dbOrder.getStatus());
        assertEquals(partnerA.getId(), dbOrder.getDeliveryPartner().getId());
    }
}

