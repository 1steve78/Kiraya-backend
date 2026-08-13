package com.hyperlocal.service;

import com.hyperlocal.dto.CategoryRequest;
import com.hyperlocal.dto.CategoryResponse;
import com.hyperlocal.entity.Category;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.exception.CategoryNotFoundException;
import com.hyperlocal.exception.ShopNotFoundException;
import com.hyperlocal.repository.CategoryRepository;
import com.hyperlocal.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ShopRepository shopRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Shop shop;
    private Category category;

    @BeforeEach
    void setUp() {
        shop = new Shop(1L, "Fresh Mart", "123 Main St", "+919876543210");
        category = new Category("Dairy", "Milk products", shop);
        category.setId(10L);
    }

    @Test
    void testCreateCategory_Success() {
        CategoryRequest request = new CategoryRequest();
        request.setName("Dairy");
        request.setDescription("Milk products");

        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        CategoryResponse response = categoryService.createCategory(1L, request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Dairy", response.getName());
        assertEquals(1L, response.getShopId());
    }

    @Test
    void testCreateCategory_ShopNotFound() {
        CategoryRequest request = new CategoryRequest();
        request.setName("Dairy");

        when(shopRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ShopNotFoundException.class, () -> categoryService.createCategory(99L, request));
    }

    @Test
    void testGetCategoriesByShopId_Success() {
        when(shopRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.findByShopId(1L)).thenReturn(List.of(category));

        List<CategoryResponse> categories = categoryService.getCategoriesByShopId(1L);

        assertEquals(1, categories.size());
        assertEquals("Dairy", categories.get(0).getName());
    }

    @Test
    void testUpdateCategory_Success() {
        CategoryRequest updateReq = new CategoryRequest();
        updateReq.setName("Dairy & Bakery");
        updateReq.setDescription("Updated description");

        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        CategoryResponse response = categoryService.updateCategory(10L, updateReq);

        assertNotNull(response);
        assertEquals("Dairy & Bakery", category.getName());
    }

    @Test
    void testDeleteCategory_NotFound() {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThrows(CategoryNotFoundException.class, () -> categoryService.deleteCategory(99L));
    }
}
