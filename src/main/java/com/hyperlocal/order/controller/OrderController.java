package com.hyperlocal.order.controller;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.dispatch.dto.CustomerLocationBroadcast;
import com.hyperlocal.dispatch.service.DeliveryTrackingService;
import com.hyperlocal.dispatch.service.DispatchService;
import com.hyperlocal.order.dto.CreateOrderRequest;
import com.hyperlocal.order.dto.OrderResponse;
import com.hyperlocal.order.dto.OrderStatusUpdateRequest;
import com.hyperlocal.order.service.OrderService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final DispatchService dispatchService;
    private final DeliveryTrackingService deliveryTrackingService;

    @Autowired
    public OrderController(OrderService orderService, DispatchService dispatchService, DeliveryTrackingService deliveryTrackingService) {
        this.orderService = orderService;
        this.dispatchService = dispatchService;
        this.deliveryTrackingService = deliveryTrackingService;
    }

    public OrderController(OrderService orderService, DispatchService dispatchService) {
        this(orderService, dispatchService, null);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User currentUser) {

        OrderResponse response = orderService.createOrder(request, currentUser.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal User currentUser) {
        List<OrderResponse> responses = orderService.getCustomerOrders(currentUser.getEmail());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User currentUser) {

        OrderResponse response = orderService.getOrderById(orderId, currentUser.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User currentUser) {

        OrderResponse response = orderService.cancelOrder(orderId, currentUser.getEmail());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request,
            @AuthenticationPrincipal User currentUser) {

        OrderResponse response = orderService.updateOrderStatus(orderId, request, currentUser.getEmail());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}/location")
    public ResponseEntity<CustomerLocationBroadcast> getOrderLocation(
            @PathVariable Long orderId,
            @AuthenticationPrincipal User currentUser) {

        orderService.getOrderById(orderId, currentUser.getEmail());
        if (deliveryTrackingService == null) {
            return ResponseEntity.noContent().build();
        }
        return deliveryTrackingService.getLatestDeliveryLocation(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
