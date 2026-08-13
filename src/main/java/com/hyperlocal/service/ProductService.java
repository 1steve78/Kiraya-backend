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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, ShopRepository shopRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.shopRepository = shopRepository;
        this.categoryRepository = categoryRepository;
    }

    public ProductResponse createProduct(Long shopId, ProductRequest request) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + shopId));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + request.getCategoryId()));

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setShop(shop);
        product.setCategory(category);

        Product savedProduct = productRepository.save(product);
        return mapToResponse(savedProduct);
    }

    public ProductResponse getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
        return mapToResponse(product);
    }

    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + request.getCategoryId()));
            product.setCategory(category);
        }

        Product updatedProduct = productRepository.save(product);
        return mapToResponse(updatedProduct);
    }

    public void deleteProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Product not found with id: " + productId);
        }
        productRepository.deleteById(productId);
    }

    public Page<ProductResponse> getProducts(Long shopId, Long categoryId, String search, Pageable pageable) {
        if (!shopRepository.existsById(shopId)) {
            throw new ShopNotFoundException("Shop not found with id: " + shopId);
        }

        Page<Product> productPage;

        if (categoryId != null && search != null) {
            productPage = productRepository.findByShopIdAndCategoryIdAndNameContainingIgnoreCase(
                    shopId, categoryId, search, pageable);
        } else if (categoryId != null) {
            productPage = productRepository.findByShopIdAndCategoryId(shopId, categoryId, pageable);
        } else if (search != null) {
            productPage = productRepository.findByShopIdAndNameContainingIgnoreCase(shopId, search, pageable);
        } else {
            productPage = productRepository.findByShopId(shopId, pageable);
        }

        return productPage.map(this::mapToResponse);
    }

    public List<ProductResponse> getLowStockProducts(Long shopId, Integer threshold) {
        if (!shopRepository.existsById(shopId)) {
            throw new ShopNotFoundException("Shop not found with id: " + shopId);
        }
        List<Product> products = productRepository.findByShopIdAndStockQuantityLessThan(shopId, threshold);
        return products.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setShopId(product.getShop().getId());

        if (product.getCategory() != null) {
            response.setCategoryId(product.getCategory().getId());
        }

        return response;
    }
}