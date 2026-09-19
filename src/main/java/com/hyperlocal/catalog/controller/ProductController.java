package com.hyperlocal.catalog.controller;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.catalog.dto.ProductRequest;
import com.hyperlocal.catalog.dto.ProductResponse;
import com.hyperlocal.catalog.service.ProductService;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/shops/{shopId}/products")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')") // Lock it down!
    public ResponseEntity<ProductResponse> createProduct(
            @PathVariable Long shopId,
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal User currentUser // Get the user from JWT
    ) {
        ProductResponse response = productService.createProduct(shopId, request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    public ProductResponse updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal User currentUser) {
        return productService.updateProduct(id, request, currentUser);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        productService.deleteProduct(id, currentUser);
    }

    @GetMapping("/shops/{shopId}/products")
    public ResponseEntity<Page<ProductResponse>> getProducts(
            @PathVariable Long shopId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort) {

        String sortField = "id";
        Sort.Direction sortDirection = Sort.Direction.DESC;

        if (sort != null && sort.length > 0) {
            if (sort.length == 1 && sort[0].contains(",")) {
                String[] parts = sort[0].split(",");
                sortField = parts[0];
                sortDirection = (parts.length > 1 && parts[1].equalsIgnoreCase("desc"))
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;
            } else {
                sortField = sort[0];
                sortDirection = (sort.length > 1 && sort[1].equalsIgnoreCase("desc"))
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortField));

        Page<ProductResponse> products = productService.getProducts(
                shopId, categoryId, search, pageable
        );

        return ResponseEntity.ok(products);
    }

    @GetMapping("/shops/{shopId}/products/low-stock")
    public ResponseEntity<List<ProductResponse>> getLowStockProducts(
            @PathVariable Long shopId,
            @RequestParam(defaultValue = "5") Integer threshold) {
        List<ProductResponse> products = productService.getLowStockProducts(shopId, threshold);
        return ResponseEntity.ok(products);
    }
}
