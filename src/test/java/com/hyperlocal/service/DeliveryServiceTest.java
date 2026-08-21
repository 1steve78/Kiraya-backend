package com.hyperlocal.service;

import com.hyperlocal.dto.AssignDeliveryRequest;
import com.hyperlocal.dto.AvailabilityRequest;
import com.hyperlocal.dto.OrderResponse;
import com.hyperlocal.entity.*;
import com.hyperlocal.exception.AccessDeniedException;
import com.hyperlocal.exception.DeliveryPartnerNotFoundException;
import com.hyperlocal.exception.InvalidOrderStateException;
import com.hyperlocal.exception.OrderNotFoundException;
import com.hyperlocal.repository.DeliveryPartnerRepository;
import com.hyperlocal.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeliveryServiceTest {

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStateMachine orderStateMachine;

    @InjectMocks
    private DeliveryService deliveryService;

    private User partnerUser;
    private DeliveryPartner partner;
    private Order order;
    private Shop shop;

    @BeforeEach
    void setUp() {
        partnerUser = new User();
        partnerUser.setId(10L);
        partnerUser.setName("Rider Dave");
        partnerUser.setEmail("dave@example.com");
        partnerUser.setRole(Role.DELIVERY_PARTNER);

        partner = new DeliveryPartner();
        partner.setId(1L);
        partner.setUser(partnerUser);
        partner.setVehicleType("BIKE");
        partner.setVehicleNumber("KA01AB1234");
        partner.setAvailable(true);

        shop = new Shop(5L, "Local Supermart", "MG Road", "+919876543210");

        order = new Order();
        order.setId(100L);
        order.setShop(shop);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setTotalAmount(BigDecimal.valueOf(250.0));
    }

    @Test
    void testUpdateAvailability_Success() {
        AvailabilityRequest request = new AvailabilityRequest();
        request.setAvailable(false);

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(deliveryPartnerRepository.save(partner)).thenReturn(partner);

        deliveryService.updateAvailability(10L, request);

        assertFalse(partner.isAvailable());
        verify(deliveryPartnerRepository).save(partner);
    }

    @Test
    void testUpdateAvailability_NotFound() {
        AvailabilityRequest request = new AvailabilityRequest();
        request.setAvailable(true);

        when(deliveryPartnerRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThrows(DeliveryPartnerNotFoundException.class,
                () -> deliveryService.updateAvailability(99L, request));
    }

    @Test
    void testAssignDelivery_Success() {
        AssignDeliveryRequest request = new AssignDeliveryRequest();
        request.setDeliveryPartnerId(1L);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partner));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = deliveryService.assignDelivery(100L, request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(partner, order.getDeliveryPartner());
        assertFalse(partner.isAvailable());
        verify(deliveryPartnerRepository).save(partner);
        verify(orderRepository).save(order);
    }

    @Test
    void testAssignDelivery_OrderNotFound() {
        AssignDeliveryRequest request = new AssignDeliveryRequest();
        request.setDeliveryPartnerId(1L);

        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class,
                () -> deliveryService.assignDelivery(999L, request));
    }

    @Test
    void testAssignDelivery_InvalidOrderStatus() {
        order.setStatus(OrderStatus.PENDING);
        AssignDeliveryRequest request = new AssignDeliveryRequest();
        request.setDeliveryPartnerId(1L);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class,
                () -> deliveryService.assignDelivery(100L, request));
    }

    @Test
    void testAssignDelivery_AlreadyAssigned() {
        order.setDeliveryPartner(partner);
        AssignDeliveryRequest request = new AssignDeliveryRequest();
        request.setDeliveryPartnerId(1L);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class,
                () -> deliveryService.assignDelivery(100L, request));
    }

    @Test
    void testAssignDelivery_PartnerNotFound() {
        AssignDeliveryRequest request = new AssignDeliveryRequest();
        request.setDeliveryPartnerId(999L);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(DeliveryPartnerNotFoundException.class,
                () -> deliveryService.assignDelivery(100L, request));
    }

    @Test
    void testAssignDelivery_PartnerUnavailable() {
        partner.setAvailable(false);
        AssignDeliveryRequest request = new AssignDeliveryRequest();
        request.setDeliveryPartnerId(1L);

        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findById(1L)).thenReturn(Optional.of(partner));

        assertThrows(InvalidOrderStateException.class,
                () -> deliveryService.assignDelivery(100L, request));
    }

    @Test
    void testGetMyAssignedOrders_WithoutStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findByDeliveryPartnerId(1L, pageable)).thenReturn(page);

        Page<OrderResponse> responses = deliveryService.getMyAssignedOrders(10L, null, pageable);

        assertNotNull(responses);
        assertEquals(1, responses.getTotalElements());
        assertEquals(100L, responses.getContent().get(0).getId());
    }

    @Test
    void testGetMyAssignedOrders_WithStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findByDeliveryPartnerIdAndStatus(1L, OrderStatus.READY_FOR_PICKUP, pageable)).thenReturn(page);

        Page<OrderResponse> responses = deliveryService.getMyAssignedOrders(10L, OrderStatus.READY_FOR_PICKUP, pageable);

        assertNotNull(responses);
        assertEquals(1, responses.getTotalElements());
    }

    @Test
    void testGetMyAssignedOrders_PartnerNotFound() {
        Pageable pageable = PageRequest.of(0, 10);
        when(deliveryPartnerRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThrows(DeliveryPartnerNotFoundException.class,
                () -> deliveryService.getMyAssignedOrders(99L, null, pageable));
    }

    @Test
    void testUpdateDeliveryStatus_Success_Delivered() {
        order.setDeliveryPartner(partner);
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        partner.setAvailable(false);

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderStateMachine.canTransition(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse response = deliveryService.updateDeliveryStatus(10L, 100L, OrderStatus.DELIVERED);

        assertNotNull(response);
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertTrue(partner.isAvailable());
        verify(deliveryPartnerRepository).save(partner);
        verify(orderRepository).save(order);
    }

    @Test
    void testUpdateDeliveryStatus_AccessDenied_NotAssigned() {
        Order otherOrder = new Order();
        otherOrder.setId(200L);
        otherOrder.setDeliveryPartner(null); // Not assigned to dave

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(200L)).thenReturn(Optional.of(otherOrder));

        assertThrows(AccessDeniedException.class,
                () -> deliveryService.updateDeliveryStatus(10L, 200L, OrderStatus.OUT_FOR_DELIVERY));
    }

    @Test
    void testUpdateDeliveryStatus_InvalidTransition() {
        order.setDeliveryPartner(partner);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);

        when(deliveryPartnerRepository.findByUserId(10L)).thenReturn(Optional.of(partner));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderStateMachine.canTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERED)).thenReturn(false);

        assertThrows(InvalidOrderStateException.class,
                () -> deliveryService.updateDeliveryStatus(10L, 100L, OrderStatus.DELIVERED));
    }
}
