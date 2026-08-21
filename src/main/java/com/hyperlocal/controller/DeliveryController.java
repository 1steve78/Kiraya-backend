package com.hyperlocal.controller;

import com.hyperlocal.dto.AssignDeliveryRequest;
import com.hyperlocal.dto.AvailabilityRequest;
import com.hyperlocal.dto.OrderResponse;
import com.hyperlocal.dto.OrderStatusUpdateRequest;
import com.hyperlocal.entity.OrderStatus;
import com.hyperlocal.entity.User;
import com.hyperlocal.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PatchMapping("/delivery/me/availability")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public ResponseEntity<Void> updateAvailability(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody AvailabilityRequest request) {

        deliveryService.updateAvailability(currentUser.getId(), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/delivery/me/orders")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {

        Page<OrderResponse> orders = deliveryService.getMyAssignedOrders(currentUser.getId(), status, pageable);
        return ResponseEntity.ok(orders);
    }

    @PatchMapping("/delivery/orders/{orderId}/status")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public ResponseEntity<OrderResponse> updateDeliveryStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {

        OrderResponse response = deliveryService.updateDeliveryStatus(currentUser.getId(), orderId, request.getOrderStatus());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{orderId}/assign-delivery")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    public ResponseEntity<OrderResponse> assignDelivery(
            @PathVariable Long orderId,
            @Valid @RequestBody AssignDeliveryRequest request) {

        OrderResponse response = deliveryService.assignDelivery(orderId, request);
        return ResponseEntity.ok(response);
    }
}
