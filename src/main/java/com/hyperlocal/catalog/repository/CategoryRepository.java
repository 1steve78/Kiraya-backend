package com.hyperlocal.catalog.repository;

import com.hyperlocal.catalog.entity.Category;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByShopId(Long shopId);
}
