package com.hyperlocal.repository;

import com.hyperlocal.entity.Order;
import com.hyperlocal.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerId(Long customerId);
    List<Order> findByShopId(Long shopId);

    Page<Order> findByShopId(Long shopId, Pageable pageable);
    Page<Order> findByShopIdAndStatus(Long shopId, OrderStatus orderStatus, Pageable pageable);

    Page<Order> findByDeliveryPartnerId(Long deliveryPartnerId, Pageable pageable);
    Page<Order> findByDeliveryPartnerIdAndStatus(Long deliveryPartnerId, OrderStatus status, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.deliveryPartner.id = :partnerId AND o.status IN :statuses")
    int countActiveOrdersForPartner(@Param("partnerId") Long partnerId, @Param("statuses") List<OrderStatus> statuses);
}
