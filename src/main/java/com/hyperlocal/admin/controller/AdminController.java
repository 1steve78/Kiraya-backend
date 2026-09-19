package com.hyperlocal.admin.controller;

import com.hyperlocal.admin.dto.DashboardSummaryResponse;
import com.hyperlocal.admin.service.AdminService;
import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.UserStatus;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.catalog.enums.ShopStatus;
import com.hyperlocal.order.entity.Order;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardSummaryResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardMetrics());
    }

    @GetMapping("/shops/pending")
    public ResponseEntity<Page<Shop>> getPendingShops(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return ResponseEntity.ok(adminService.getPendingShops(pageable));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<?> suspendUser(@PathVariable Long userId, @RequestBody Map<String, String> request) {
        UserStatus status = UserStatus.valueOf(request.get("status").toUpperCase());
        adminService.updateUserStatus(userId, status);
        return ResponseEntity.ok(Map.of("message", "User status updated to " + status));
    }

    @PatchMapping("/shops/{shopId}/status")
    public ResponseEntity<?> updateShopStatus(@PathVariable Long shopId, @RequestBody Map<String, String> request) {
        ShopStatus status = ShopStatus.valueOf(request.get("status").toUpperCase());
        adminService.updateShopStatus(shopId, status);
        return ResponseEntity.ok(Map.of("message", "Shop status updated to " + status));
    }

    @PatchMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        adminService.cancelOrderAsAdmin(orderId);
        return ResponseEntity.ok(Map.of("message", "Order cancelled by admin"));
    }
}
