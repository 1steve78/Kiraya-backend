package com.hyperlocal.controller;

import com.hyperlocal.dto.CategoryRequest;
import com.hyperlocal.dto.CategoryResponse;
import com.hyperlocal.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/shops/{shopId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@PathVariable Long shopId, @RequestBody CategoryRequest request) {
        return categoryService.createCategory(shopId, request);
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
    public CategoryResponse updateCategory(@PathVariable Long categoryId, @RequestBody CategoryRequest request) {
        return categoryService.updateCategory(categoryId, request);
    }

    @DeleteMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
    }
}
