package com.hyperlocal.dispatch.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @Mock
    private DistanceService distanceService;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private DispatchService dispatchService;

    private Shop shop;
    private Order order;
    private DeliveryPartner partner1;
    private DeliveryPartner partner2;

    @BeforeEach
    void setUp() {
        shop = new Shop(1L, "Fresh Store", "123 Main St", "+919876543210");

        order = new Order();
        order.setId(100L);
        order.setShop(shop);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setTotalAmount(BigDecimal.valueOf(500.0));

        User user1 = new User();
        user1.setId(1L);
        user1.setName("Rider Alice");

        partner1 = new DeliveryPartner();
        partner1.setId(1L);
        partner1.setUser(user1);
        partner1.setAvailable(true);

        User user2 = new User();
        user2.setId(2L);
        user2.setName("Rider Bob");

        partner2 = new DeliveryPartner();
        partner2.setId(2L);
        partner2.setUser(user2);
        partner2.setAvailable(true);
    }

    @Test
    @DisplayName("Should successfully dispatch order to single available partner")
    void testDispatchOrder_Success_SinglePartner() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findByIsAvailableTrue()).thenReturn(List.of(partner1));
        when(orderRepository.countActiveOrdersForPartner(eq(1L), any())).thenReturn(1);
        
        dispatchService.dispatchOrder(100L);
        
        verify(orderEventPublisher).publishNewDelivery(eq(order), anyList());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should dispatch to candidate with best (lowest) score")
    void testDispatchOrder_Success_PicksLowestScoredPartner() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findByIsAvailableTrue()).thenReturn(List.of(partner1, partner2));

        when(orderRepository.countActiveOrdersForPartner(eq(1L), any())).thenReturn(2);
        when(orderRepository.countActiveOrdersForPartner(eq(2L), any())).thenReturn(0);

        dispatchService.dispatchOrder(100L);

        verify(orderEventPublisher).publishNewDelivery(eq(order), anyList());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when order does not exist")
    void testDispatchOrder_OrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dispatchService.dispatchOrder(999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Order not found"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CONFLICT when order is not in READY_FOR_PICKUP status")
    void testDispatchOrder_InvalidStatus() {
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dispatchService.dispatchOrder(100L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Order must be READY_FOR_PICKUP"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw CONFLICT when order is already assigned to a delivery partner")
    void testDispatchOrder_AlreadyAssigned() {
        order.setDeliveryPartner(partner1);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dispatchService.dispatchOrder(100L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Order is already assigned"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return silently when no delivery partners are available")
    void testDispatchOrder_NoAvailablePartners() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findByIsAvailableTrue()).thenReturn(List.of());

        dispatchService.dispatchOrder(100L);

        verify(orderEventPublisher, never()).publishNewDelivery(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return silently when all available partners exceed max active orders")
    void testDispatchOrder_AllPartnersOverloaded() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findByIsAvailableTrue()).thenReturn(List.of(partner1));
        when(orderRepository.countActiveOrdersForPartner(eq(1L), any())).thenReturn(4); // Exceeds MAX_ACTIVE_ORDERS = 3

        dispatchService.dispatchOrder(100L);

        verify(orderEventPublisher, never()).publishNewDelivery(any(), any());
        verify(distanceService, never()).calculateDistance(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should include partner with exactly max allowed active orders (3)")
    void testDispatchOrder_PartnerWithMaxActiveOrders() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(deliveryPartnerRepository.findByIsAvailableTrue()).thenReturn(List.of(partner1));
        when(orderRepository.countActiveOrdersForPartner(eq(1L), any())).thenReturn(3); // Exactly MAX_ACTIVE_ORDERS = 3
        
        dispatchService.dispatchOrder(100L);

        verify(orderEventPublisher).publishNewDelivery(eq(order), anyList());
        verify(orderRepository, never()).save(any());
    }
}
