package com.hyperlocal.order.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.Role;
import com.hyperlocal.auth.repository.UserRepository;
import com.hyperlocal.catalog.entity.Product;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.catalog.exception.InsufficientStockException;
import com.hyperlocal.catalog.exception.InvalidProductException;
import com.hyperlocal.catalog.exception.ProductNotFoundException;
import com.hyperlocal.catalog.exception.ShopNotFoundException;
import com.hyperlocal.catalog.repository.ProductRepository;
import com.hyperlocal.catalog.repository.ShopRepository;
import com.hyperlocal.common.exception.AccessDeniedException;
import com.hyperlocal.dispatch.service.DispatchService;
import com.hyperlocal.notification.service.OrderEventPublisher;
import com.hyperlocal.order.dto.CreateOrderRequest;
import com.hyperlocal.order.dto.OrderItemRequest;
import com.hyperlocal.order.dto.OrderItemResponse;
import com.hyperlocal.order.dto.OrderResponse;
import com.hyperlocal.order.dto.OrderStatusUpdateRequest;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.entity.OrderItem;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.exception.InvalidOrderStateException;
import com.hyperlocal.order.exception.OrderNotFoundException;
import com.hyperlocal.order.repository.OrderRepository;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher orderEventPublisher;
    private final DispatchService dispatchService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        ShopRepository shopRepository, UserRepository userRepository,
                        OrderStateMachine stateMachine, OrderEventPublisher orderEventPublisher,
                        DispatchService dispatchService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.stateMachine = stateMachine;
        this.orderEventPublisher = orderEventPublisher;
        this.dispatchService = dispatchService;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String customerEmail) {
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + customerEmail));

        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + request.getShopId()));

        Order order = new Order();
        order.setCustomer(customer);
        order.setShop(shop);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + itemRequest.getProductId()));

            if (!product.getShop().getId().equals(shop.getId())) {
                throw new InvalidProductException("Product '" + product.getName() + "' does not belong to this shop");
            }

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product '" + product.getName() + "'");
            }

            product.setStockQuantity(product.getStockQuantity() - itemRequest.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setUnitPrice(product.getPrice());
            BigDecimal itemSubtotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            orderItem.setSubtotal(itemSubtotal);
            order.addItem(orderItem);

            totalAmount = totalAmount.add(itemSubtotal);
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);
        orderEventPublisher.publishNewOrder(savedOrder);
        return mapToResponse(savedOrder);
    }

    public List<OrderResponse> getCustomerOrders(String customerEmail) {
        User customer = userRepository.findByEmail(customerEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + customerEmail));

        return orderRepository.findByCustomerId(customer.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Long orderId, String customerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (!order.getCustomer().getEmail().equals(customerEmail)) {
            throw new AccessDeniedException("You do not have permission to view this order.");
        }

        return mapToResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String customerEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (!order.getCustomer().getEmail().equals(customerEmail)) {
            throw new AccessDeniedException("You do not have permission to cancel this order.");
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException("Cannot cancel order in status " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);

        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        Order savedOrder = orderRepository.save(order);
        orderEventPublisher.publishOrderCancelled(savedOrder);
        return mapToResponse(savedOrder);
    }

    public List<OrderResponse> getShopOrders(Long shopId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        if (user.getRole() != Role.ADMIN) {
            Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + shopId));

            if (shop.getOwner() == null || !shop.getOwner().getId().equals(user.getId())) {
                throw new AccessDeniedException("You do not have permission to view this shop's orders.");
            }
        }

        return orderRepository.findByShopId(shopId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatusUpdateRequest request, String userEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isShopOwner = user.getRole() == Role.SHOP_OWNER;

        if (!isAdmin) {
            if (!isShopOwner) {
                throw new AccessDeniedException("Only shop owners and admins can update statuses.");
            }
            if (order.getShop().getOwner() == null || !order.getShop().getOwner().getId().equals(user.getId())) {
                throw new AccessDeniedException("You do not own the shop for this order.");
            }
        }

        if (!stateMachine.canTransition(order.getStatus(), request.getOrderStatus())) {
            throw new InvalidOrderStateException("Order cannot transition from "
                    + order.getStatus() + " to " + request.getOrderStatus());
        }

        if (request.getOrderStatus() == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                productRepository.save(product);
            }
        }

        order.setStatus(request.getOrderStatus());
        Order savedOrder = orderRepository.save(order);
        orderEventPublisher.publishOrderStatusChanged(savedOrder);
        
        if (request.getOrderStatus() == OrderStatus.READY_FOR_PICKUP) {
            try {
                dispatchService.dispatchOrder(savedOrder.getId());
                // Refresh order state since dispatchOrder updates it
                savedOrder = orderRepository.findById(savedOrder.getId()).orElse(savedOrder);
            } catch (Exception e) {
                log.warn("Auto-dispatch failed for order {}: {}", savedOrder.getId(), e.getMessage());
            }
        }
        
        return mapToResponse(savedOrder);
    }

    public Page<OrderResponse> getShopOrdersPaginated(
            Long shopId, OrderStatus status, Pageable pageable, String userEmail) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + userEmail));

        if (user.getRole() != Role.ADMIN) {
            Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + shopId));

            if (shop.getOwner() == null || !shop.getOwner().getId().equals(user.getId())) {
                throw new AccessDeniedException("You do not have permission to view this shop's orders.");
            }
        }

        Page<Order> ordersPage;
        if (status != null) {
            ordersPage = orderRepository.findByShopIdAndStatus(shopId, status, pageable);
        } else {
            ordersPage = orderRepository.findByShopId(shopId, pageable);
        }

        return ordersPage.map(this::mapToResponse);
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setShopId(order.getShop().getId());
        response.setShopName(order.getShop().getName());
        response.setCustomerId(order.getCustomer().getId());
        response.setCustomerName(order.getCustomer().getName());
        // Phone is not stored on User — leave null (can be added later if User entity gains phone field)
        response.setCustomerPhone(null);
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());

        if (order.getItems() != null) {
            response.setItems(order.getItems().stream().map(item -> {
                OrderItemResponse itemResponse = new OrderItemResponse();
                itemResponse.setId(item.getId());
                itemResponse.setProductId(item.getProduct().getId());
                itemResponse.setProductName(item.getProduct().getName());
                itemResponse.setQuantity(item.getQuantity());
                itemResponse.setUnitPrice(item.getUnitPrice());
                itemResponse.setSubtotal(item.getSubtotal());
                return itemResponse;
            }).toList());
        }

        return response;
    }
}
