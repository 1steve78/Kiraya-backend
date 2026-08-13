package com.hyperlocal.controller;

import com.hyperlocal.dto.ShopRequest;
import com.hyperlocal.dto.ShopResponse;
import com.hyperlocal.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shops")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    public ResponseEntity<List<ShopResponse>> getAllShops() {
        return ResponseEntity.ok(shopService.getAllShops());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShopResponse> getShopById(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.getShopById(id));
    }

    @PostMapping
    // Notice the @Valid annotation here!
    public ResponseEntity<ShopResponse> createShop(@Valid @RequestBody ShopRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shopService.createShop(request));
    }

    @PutMapping("/{id}")
    // And here!
    public ResponseEntity<ShopResponse> updateShop(@PathVariable Long id, @Valid @RequestBody ShopRequest request) {
        return ResponseEntity.ok(shopService.updateShop(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShop(@PathVariable Long id) {
        shopService.deleteShop(id);
        return ResponseEntity.noContent().build();
    }
}