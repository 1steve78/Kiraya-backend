package com.hyperlocal.catalog.controller;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.catalog.dto.ShopCreatedResponse;
import com.hyperlocal.catalog.dto.ShopRequest;
import com.hyperlocal.catalog.dto.ShopResponse;
import com.hyperlocal.catalog.service.ShopService;
import com.hyperlocal.order.dto.OrderResponse;
import com.hyperlocal.order.service.OrderService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/shops")
public class ShopController {

    private final ShopService shopService;
    private final OrderService orderService;

    public ShopController(ShopService shopService, OrderService orderService) {
        this.shopService = shopService;
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<ShopCreatedResponse> createShop(
            @Valid @RequestBody ShopRequest request,
            @AuthenticationPrincipal User currentUser
    ){
        ShopCreatedResponse response = shopService.createShop(request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ShopResponse>> getAllShops() {
        return ResponseEntity.ok(shopService.getAllShops());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShopResponse> getShopById(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.getShopById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShopResponse> updateShop(@PathVariable Long id, @Valid @RequestBody ShopRequest request ,@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(shopService.updateShop(id, request,currentUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    public ResponseEntity<Void> deleteShop(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        shopService.deleteShop(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{shopId}/orders")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    public ResponseEntity<List<OrderResponse>> getShopOrders(
            @PathVariable Long shopId,
            @AuthenticationPrincipal User currentUser
    ){
        List<OrderResponse> responses = orderService.getShopOrders(shopId, currentUser.getEmail());
        return ResponseEntity.ok(responses);
    }
}
