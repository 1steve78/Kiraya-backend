package com.hyperlocal.repository;

import com.hyperlocal.entity.Shop;
import com.hyperlocal.model.ShopStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    // You don't need to write any code in here!
    // JpaRepository automatically provides save(), findAll(), findById(), etc.
    List<Shop> findByNameContainingIgnoreCase(String name);

    List<Shop> findByStatus(ShopStatus status);
    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);
    long countByStatus(ShopStatus status);
}