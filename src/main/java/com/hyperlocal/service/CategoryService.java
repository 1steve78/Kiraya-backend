package com.hyperlocal.service;

import com.hyperlocal.dto.CategoryRequest;
import com.hyperlocal.dto.CategoryResponse;
import com.hyperlocal.entity.Category;
import com.hyperlocal.model.Role;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.entity.User;
import com.hyperlocal.exception.AccessDeniedException;
import com.hyperlocal.exception.CategoryNotFoundException;
import com.hyperlocal.exception.ShopNotFoundException;
import com.hyperlocal.repository.CategoryRepository;
import com.hyperlocal.repository.ShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ShopRepository shopRepository;

    public CategoryService(CategoryRepository categoryRepository, ShopRepository shopRepository) {
        this.categoryRepository = categoryRepository;
        this.shopRepository = shopRepository;
    }

    public CategoryResponse createCategory(Long shopId, CategoryRequest request, User currentUser) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + shopId));
        verifyShopOwnership(shop, currentUser);

        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setShop(shop);

        Category savedCategory = categoryRepository.save(category);
        return mapToResponse(savedCategory);
    }

    public List<CategoryResponse> getCategoriesByShopId(Long shopId) {
        if (!shopRepository.existsById(shopId)) {
            throw new ShopNotFoundException("Shop not found with id: " + shopId);
        }

        List<Category> categories = categoryRepository.findByShopId(shopId);
        return categories.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public CategoryResponse getCategoryById(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + categoryId));
        return mapToResponse(category);
    }

    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request, User currentUser) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + categoryId));
        verifyShopOwnership(category.getShop(), currentUser);

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        Category updatedCategory = categoryRepository.save(category);
        return mapToResponse(updatedCategory);
    }

    public void deleteCategory(Long categoryId, User currentUser) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + categoryId));
        verifyShopOwnership(category.getShop(), currentUser);
        categoryRepository.delete(category);
    }

    private CategoryResponse mapToResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setShopId(category.getShop().getId());
        return response;
    }

    private void verifyShopOwnership(Shop shop, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (shop.getOwner() == null || !shop.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to modify categories for this shop.");
        }
    }
}
