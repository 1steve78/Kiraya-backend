package com.hyperlocal.catalog.repository;

import com.hyperlocal.catalog.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByShopId(Long shopId);
    List<Product> findByShopIdAndNameContainingIgnoreCase(Long shopId, String name);

    Page<Product> findByShopId(Long shopId, Pageable pageable);

    Page<Product> findByShopIdAndCategoryId(Long shopId, Long categoryId, Pageable pageable);

    Page<Product> findByShopIdAndNameContainingIgnoreCase(Long shopId, String search, Pageable pageable);

    Page<Product> findByShopIdAndCategoryIdAndNameContainingIgnoreCase(Long shopId, Long categoryId, String search, Pageable pageable);

    List<Product> findByShopIdAndStockQuantityLessThan(Long shopId, Integer threshold);
}
