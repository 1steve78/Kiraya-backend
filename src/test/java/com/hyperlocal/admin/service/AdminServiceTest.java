package com.hyperlocal.admin.service;

import com.hyperlocal.admin.dto.DashboardSummaryResponse;
import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.UserStatus;
import com.hyperlocal.auth.repository.UserRepository;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.catalog.enums.ShopStatus;
import com.hyperlocal.catalog.exception.ShopNotFoundException;
import com.hyperlocal.catalog.repository.ShopRepository;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.exception.InvalidOrderStateException;
import com.hyperlocal.order.exception.OrderNotFoundException;
import com.hyperlocal.order.repository.OrderRepository;

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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @InjectMocks
    private AdminService adminService;

    private User user;
    private Shop shop;
    private Order order;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setName("Test User");
        user.setEmail("user@example.com");
        user.setStatus(UserStatus.ACTIVE);

        shop = new Shop(10L, "Test Shop", "Address", "+919876543210");
        shop.setStatus(ShopStatus.PENDING_APPROVAL);

        order = new Order();
        order.setId(100L);
        order.setStatus(OrderStatus.PREPARING);
    }

    @Test
    void testGetDashboardMetrics() {
        when(userRepository.count()).thenReturn(50L);
        when(shopRepository.count()).thenReturn(10L);
        when(shopRepository.countByStatus(ShopStatus.PENDING_APPROVAL)).thenReturn(3L);
        when(orderRepository.countByStatusIn(any())).thenReturn(5L);
        DeliveryPartner dp = new DeliveryPartner();
        dp.setAvailable(true);
        when(deliveryPartnerRepository.findByIsAvailableTrue()).thenReturn(List.of(dp));

        DashboardSummaryResponse metrics = adminService.getDashboardMetrics();

        assertNotNull(metrics);
        assertEquals(50L, metrics.totalUsers());
        assertEquals(10L, metrics.totalShops());
        assertEquals(3L, metrics.pendingShopApprovals());
        assertEquals(5L, metrics.activeOrders());
        assertEquals(1L, metrics.availableDeliveryPartners());
    }

    @Test
    void testUpdateUserStatus_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User updated = adminService.updateUserStatus(1L, UserStatus.SUSPENDED);

        assertNotNull(updated);
        assertEquals(UserStatus.SUSPENDED, updated.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void testUpdateUserStatus_UserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> adminService.updateUserStatus(99L, UserStatus.SUSPENDED));
    }

    @Test
    void testUpdateShopStatus_Success() {
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(shopRepository.save(any(Shop.class))).thenAnswer(i -> i.getArgument(0));

        Shop updated = adminService.updateShopStatus(10L, ShopStatus.APPROVED);

        assertNotNull(updated);
        assertEquals(ShopStatus.APPROVED, updated.getStatus());
        verify(shopRepository).save(shop);
    }

    @Test
    void testUpdateShopStatus_ShopNotFound() {
        when(shopRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ShopNotFoundException.class, () -> adminService.updateShopStatus(99L, ShopStatus.APPROVED));
    }

    @Test
    void testGetPendingShops() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Shop> page = new PageImpl<>(List.of(shop));
        when(shopRepository.findByStatus(ShopStatus.PENDING_APPROVAL, pageable)).thenReturn(page);

        Page<Shop> result = adminService.getPendingShops(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void testCancelOrderAsAdmin_Success() {
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order cancelled = adminService.cancelOrderAsAdmin(100L);

        assertNotNull(cancelled);
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void testCancelOrderAsAdmin_AlreadyDelivered_ThrowsInvalidOrderStateException() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> adminService.cancelOrderAsAdmin(100L));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testCancelOrderAsAdmin_OrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> adminService.cancelOrderAsAdmin(999L));
    }
}
