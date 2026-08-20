package com.hyperlocal.service;

import com.hyperlocal.dto.CreateOrderRequest;
import com.hyperlocal.dto.OrderItemRequest;
import com.hyperlocal.dto.OrderResponse;
import com.hyperlocal.dto.OrderStatusUpdateRequest;
import com.hyperlocal.entity.*;
import com.hyperlocal.exception.*;
import com.hyperlocal.repository.OrderRepository;
import com.hyperlocal.repository.ProductRepository;
import com.hyperlocal.repository.ShopRepository;
import com.hyperlocal.repository.UserRepository;
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
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderStateMachine stateMachine;

    @InjectMocks
    private OrderService orderService;

    private User customer;
    private User shopOwner;
    private Shop shop;
    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(1L);
        customer.setName("John Customer");
        customer.setEmail("john@example.com");
        customer.setRole(Role.CUSTOMER);

        shopOwner = new User();
        shopOwner.setId(2L);
        shopOwner.setName("Owner Bob");
        shopOwner.setEmail("bob@example.com");
        shopOwner.setRole(Role.SHOP_OWNER);

        shop = new Shop(10L, "Fresh Mart", "123 Main St", "+919876543210");
        shop.setOwner(shopOwner);

        category = new Category("Dairy", "Milk products", shop);
        category.setId(100L);

        product = new Product("Amul Milk", "1L Pouch", BigDecimal.valueOf(65.0), 20, shop, category);
        product.setId(200L);
    }

    @Test
    void testCreateOrder_Success() {
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(200L);
        itemRequest.setQuantity(2);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShopId(10L);
        request.setItems(List.of(itemRequest));

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(customer));
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(productRepository.findById(200L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(500L);
            return saved;
        });

        OrderResponse response = orderService.createOrder(request, "john@example.com");

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(10L, response.getShopId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
        assertEquals(0, BigDecimal.valueOf(130.0).compareTo(response.getTotalAmount()));
        assertEquals(18, product.getStockQuantity());
        assertEquals(1, response.getItems().size());
        assertEquals("Amul Milk", response.getItems().get(0).getProductName());
    }

    @Test
    void testCreateOrder_ProductNotFound() {
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(999L);
        itemRequest.setQuantity(1);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShopId(10L);
        request.setItems(List.of(itemRequest));

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(customer));
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> orderService.createOrder(request, "john@example.com"));
    }

    @Test
    void testCreateOrder_ShopMismatch() {
        Shop otherShop = new Shop(20L, "Other Mart", "456 Other St", "+919876543211");
        Product otherProduct = new Product("Other Milk", "1L", BigDecimal.valueOf(50.0), 10, otherShop, category);
        otherProduct.setId(300L);

        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(300L);
        itemRequest.setQuantity(1);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShopId(10L);
        request.setItems(List.of(itemRequest));

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(customer));
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(productRepository.findById(300L)).thenReturn(Optional.of(otherProduct));

        assertThrows(InvalidProductException.class, () -> orderService.createOrder(request, "john@example.com"));
    }

    @Test
    void testCreateOrder_InsufficientStock() {
        OrderItemRequest itemRequest = new OrderItemRequest();
        itemRequest.setProductId(200L);
        itemRequest.setQuantity(50); // product only has 20 in stock

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShopId(10L);
        request.setItems(List.of(itemRequest));

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(customer));
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(productRepository.findById(200L)).thenReturn(Optional.of(product));

        assertThrows(InsufficientStockException.class, () -> orderService.createOrder(request, "john@example.com"));
    }

    @Test
    void testGetOrderById_Success() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(BigDecimal.valueOf(130.0));

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrderById(500L, "john@example.com");

        assertNotNull(response);
        assertEquals(500L, response.getId());
    }

    @Test
    void testGetOrderById_NotOwner() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class, () -> orderService.getOrderById(500L, "hacker@example.com"));
    }

    @Test
    void testCancelOrder_Success() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(BigDecimal.valueOf(130.0));

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.valueOf(65.0));
        item.setSubtotal(BigDecimal.valueOf(130.0));
        order.addItem(item);

        product.setStockQuantity(18);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse response = orderService.cancelOrder(500L, "john@example.com");

        assertNotNull(response);
        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertEquals(20, product.getStockQuantity()); // Stock restored from 18 to 20
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void testCancelOrder_InvalidState() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.DELIVERED);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder(500L, "john@example.com"));
    }

    @Test
    void testGetShopOrders_ShopOwnerSuccess() {
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(shopOwner));
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(orderRepository.findByShopId(10L)).thenReturn(List.of());

        List<OrderResponse> responses = orderService.getShopOrders(10L, "bob@example.com");

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void testGetShopOrders_NotOwnerAccessDenied() {
        User otherOwner = new User();
        otherOwner.setId(3L);
        otherOwner.setEmail("other@example.com");
        otherOwner.setRole(Role.SHOP_OWNER);

        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherOwner));
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));

        assertThrows(AccessDeniedException.class, () -> orderService.getShopOrders(10L, "other@example.com"));
    }

    @Test
    void testUpdateOrderStatus_ShopOwnerSuccess() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(shopOwner));
        when(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse response = orderService.updateOrderStatus(500L, request, "bob@example.com");

        assertNotNull(response);
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void testUpdateOrderStatus_InvalidTransitionThrowsException() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.DELIVERED);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(shopOwner));
        when(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.DELIVERED)).thenReturn(false);

        assertThrows(InvalidOrderStateException.class,
                () -> orderService.updateOrderStatus(500L, request, "bob@example.com"));
    }

    @Test
    void testUpdateOrderStatus_NotShopOwnerThrowsAccessDenied() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);

        User otherOwner = new User();
        otherOwner.setId(99L);
        otherOwner.setEmail("other@example.com");
        otherOwner.setRole(Role.SHOP_OWNER);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherOwner));

        assertThrows(AccessDeniedException.class,
                () -> orderService.updateOrderStatus(500L, request, "other@example.com"));
    }

    @Test
    void testUpdateOrderStatus_CancelledRestoresStock() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);

        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.valueOf(65.0));
        item.setSubtotal(BigDecimal.valueOf(130.0));
        order.addItem(item);

        product.setStockQuantity(18);

        OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
        request.setOrderStatus(OrderStatus.CANCELLED);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(order));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(shopOwner));
        when(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse response = orderService.updateOrderStatus(500L, request, "bob@example.com");

        assertNotNull(response);
        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertEquals(20, product.getStockQuantity());
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void testGetShopOrdersPaginated_Success() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(shopOwner));
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(orderRepository.findByShopId(10L, pageable)).thenReturn(page);

        Page<OrderResponse> response = orderService.getShopOrdersPaginated(10L, null, pageable, "bob@example.com");

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void testGetShopOrdersPaginated_WithStatusFilter() {
        Order order = new Order();
        order.setId(500L);
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(shopOwner));
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(orderRepository.findByShopIdAndStatus(10L, OrderStatus.PENDING, pageable)).thenReturn(page);

        Page<OrderResponse> response = orderService.getShopOrdersPaginated(10L, OrderStatus.PENDING, pageable, "bob@example.com");

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }
}
