package com.hyperlocal.service;

import com.hyperlocal.dto.DashboardSummaryResponse;
import com.hyperlocal.entity.Order;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.entity.User;
import com.hyperlocal.exception.InvalidOrderStateException;
import com.hyperlocal.exception.OrderNotFoundException;
import com.hyperlocal.exception.ShopNotFoundException;
import com.hyperlocal.model.OrderStatus;
import com.hyperlocal.model.ShopStatus;
import com.hyperlocal.model.UserStatus;
import com.hyperlocal.repository.DeliveryPartnerRepository;
import com.hyperlocal.repository.OrderRepository;
import com.hyperlocal.repository.ShopRepository;
import com.hyperlocal.repository.UserRepository;
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
