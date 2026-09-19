package com.hyperlocal.admin.service;

import com.hyperlocal.admin.dto.DashboardSummaryResponse;
import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.UserStatus;
import com.hyperlocal.auth.repository.UserRepository;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.catalog.enums.ShopStatus;
import com.hyperlocal.catalog.exception.ShopNotFoundException;
import com.hyperlocal.catalog.repository.ShopRepository;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import com.hyperlocal.order.exception.InvalidOrderStateException;
import com.hyperlocal.order.exception.OrderNotFoundException;
import com.hyperlocal.order.repository.OrderRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final OrderRepository orderRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;

    public DashboardSummaryResponse getDashboardMetrics(){
        long users = userRepository.count();
        long shops = shopRepository.count();
        long pendingShops = shopRepository.countByStatus(ShopStatus.PENDING_APPROVAL);
        long activeOrders = orderRepository.countByStatusIn(
                List.of(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY)
        );
        long availablePartners = deliveryPartnerRepository.findByIsAvailableTrue().size();

        return new DashboardSummaryResponse(users, shops, pendingShops, activeOrders, availablePartners);

    }

    @Transactional
    public User updateUserStatus(Long userId , UserStatus newStatus){
       User user = userRepository.findById(userId)
               .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
       user.setStatus(newStatus);

       return userRepository.save(user);
    }

    @Transactional
    public Shop updateShopStatus(Long shopId, ShopStatus newStatus) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ShopNotFoundException("Shop not found with id: " + shopId));
        shop.setStatus(newStatus);
        return shopRepository.save(shop);
    }

    public Page<Shop> getPendingShops(Pageable pageable) {
        return shopRepository.findByStatus(ShopStatus.PENDING_APPROVAL, pageable);
    }

    @Transactional
    public Order cancelOrderAsAdmin(Long orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if(order.getStatus() == OrderStatus.DELIVERED){
            throw new InvalidOrderStateException("Cannot cancel an already delivered order");
        }
        order.setStatus(OrderStatus.CANCELLED);

        return orderRepository.save(order);

    }
}
