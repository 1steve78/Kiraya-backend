package com.hyperlocal.controller;

import com.hyperlocal.dto.CreateOrderRequest;
import com.hyperlocal.dto.OrderResponse;
import com.hyperlocal.dto.OrderStatusUpdateRequest;
import com.hyperlocal.entity.User;
import com.hyperlocal.service.OrderService;
import jakarta.validation.Valid;
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

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
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
}
