package com.hyperlocal.order.repository;

import com.hyperlocal.order.entity.OrderItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository  extends JpaRepository<OrderItem,Long> {

}
