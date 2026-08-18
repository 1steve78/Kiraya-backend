package com.hyperlocal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestSecurityController {

    @GetMapping("/customer")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<String> customerAccess(){
        return ResponseEntity.ok("Customer access granted");
    }

    @GetMapping("/shop-owner")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<String> shopOwnerAccess() {
        return ResponseEntity.ok("Shop Owner access granted.");
    }

    @GetMapping("/delivery")
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    public ResponseEntity<String> deliveryAccess() {
        return ResponseEntity.ok("Delivery Partner access granted.");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> adminAccess() {
        return ResponseEntity.ok("Admin access granted.");
    }
}
