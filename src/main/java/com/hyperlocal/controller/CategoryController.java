package com.hyperlocal.controller;

import com.hyperlocal.dto.CategoryRequest;
import com.hyperlocal.dto.CategoryResponse;
import com.hyperlocal.entity.User;
import com.hyperlocal.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/shops/{shopId}/categories")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(
            @PathVariable Long shopId,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal User currentUser) {
        return categoryService.createCategory(shopId, request, currentUser);
    }

    @GetMapping("/shops/{shopId}/categories")
    public List<CategoryResponse> getCategoriesByShopId(@PathVariable Long shopId) {
        return categoryService.getCategoriesByShopId(shopId);
    }

    @GetMapping("/categories/{categoryId}")
    public CategoryResponse getCategoryById(@PathVariable Long categoryId) {
        return categoryService.getCategoryById(categoryId);
    }

    @PutMapping("/categories/{categoryId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    public CategoryResponse updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal User currentUser) {
        return categoryService.updateCategory(categoryId, request, currentUser);
    }

    @DeleteMapping("/categories/{categoryId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable Long categoryId,
            @AuthenticationPrincipal User currentUser) {
        categoryService.deleteCategory(categoryId, currentUser);
    }
}
