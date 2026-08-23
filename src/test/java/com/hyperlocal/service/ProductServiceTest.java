package com.hyperlocal.service;

import com.hyperlocal.dto.ProductRequest;
import com.hyperlocal.dto.ProductResponse;
import com.hyperlocal.entity.Category;
import com.hyperlocal.entity.Product;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.exception.CategoryNotFoundException;
import com.hyperlocal.exception.ProductNotFoundException;
import com.hyperlocal.exception.ShopNotFoundException;
import com.hyperlocal.repository.CategoryRepository;
import com.hyperlocal.repository.ProductRepository;
import com.hyperlocal.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Shop shop;
    private Category category;
    private Product product;
    private com.hyperlocal.entity.User user;

    @BeforeEach
    void setUp() {
        user = new com.hyperlocal.entity.User();
        user.setId(1L);
        user.setRole(com.hyperlocal.model.Role.SHOP_OWNER);

        shop = new Shop(1L, "Fresh Mart", "123 Main St", "+919876543210");
        shop.setOwner(user);

        category = new Category("Dairy", "Milk products", shop);
        category.setId(10L);

        product = new Product("Amul Milk", "1L Pouch", BigDecimal.valueOf(65.0), 20, shop, category);
        product.setId(100L);
    }

    @Test
    void testCreateProduct_Success() {
        ProductRequest request = new ProductRequest();
        request.setName("Amul Milk");
        request.setDescription("1L Pouch");
        request.setPrice(BigDecimal.valueOf(65.0));
        request.setStockQuantity(20);
        request.setCategoryId(10L);

        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.createProduct(1L, request, user);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Amul Milk", response.getName());
        assertEquals(1L, response.getShopId());
        assertEquals(10L, response.getCategoryId());
    }

    @Test
    void testCreateProduct_CategoryNotFound() {
        ProductRequest request = new ProductRequest();
        request.setCategoryId(99L);

        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> productService.createProduct(1L, request, user));
    }

    @Test
    void testGetProducts_WithPaginationAndFiltering() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price"));
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

        when(shopRepository.existsById(1L)).thenReturn(true);
        when(productRepository.findByShopIdAndCategoryIdAndNameContainingIgnoreCase(eq(1L), eq(10L), eq("milk"), eq(pageable)))
                .thenReturn(page);

        Page<ProductResponse> result = productService.getProducts(1L, 10L, "milk", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Amul Milk", result.getContent().get(0).getName());
    }

    @Test
    void testGetLowStockProducts() {
        when(shopRepository.existsById(1L)).thenReturn(true);
        when(productRepository.findByShopIdAndStockQuantityLessThan(1L, 5))
                .thenReturn(List.of(product));

        List<ProductResponse> lowStock = productService.getLowStockProducts(1L, 5);

        assertEquals(1, lowStock.size());
        assertEquals("Amul Milk", lowStock.get(0).getName());
    }

    @Test
    void testUpdateProduct_Success() {
        ProductRequest updateReq = new ProductRequest();
        updateReq.setName("Amul Full Cream Milk");
        updateReq.setDescription("1L Pouch Updated");
        updateReq.setPrice(BigDecimal.valueOf(68.0));
        updateReq.setStockQuantity(25);
        updateReq.setCategoryId(10L);

        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.updateProduct(100L, updateReq, user);

        assertNotNull(response);
        assertEquals("Amul Full Cream Milk", product.getName());
    }

    @Test
    void testDeleteProduct_Success() {
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));

        productService.deleteProduct(100L, user);

        verify(productRepository, times(1)).delete(product);
    }
}
