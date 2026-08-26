package com.hyperlocal.service;

import com.hyperlocal.dto.EventType;
import com.hyperlocal.dto.OrderAssignedEvent;
import com.hyperlocal.dto.OrderStatusEvent;
import com.hyperlocal.dto.RealtimeEvent;
import com.hyperlocal.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishOrderStatusChanged(Order order){
        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.ORDER_STATUS_CHANGED,
                Instant.now(),
                new OrderStatusEvent(order.getId(), order.getStatus())
        );

        String customerEmail = order.getCustomer().getEmail();
        messagingTemplate.convertAndSendToUser(customerEmail, "/queue/orders", event);

        String shopOwnerEmail = order.getShop().getOwner().getEmail();
        messagingTemplate.convertAndSendToUser(shopOwnerEmail, "/queue/shop-orders", event);

        log.info("Published ORDER_STATUS_CHANGED for Order {} to Customer and Shop", order.getId());

    }

    public void publishOrderAssigned(Order order) {
        if (order.getDeliveryPartner() == null) return;

        RealtimeEvent<OrderAssignedEvent> event = new RealtimeEvent<>(
                EventType.ORDER_ASSIGNED,
                Instant.now(),
                new OrderAssignedEvent(order.getId(), order.getDeliveryPartner().getId())
        );

        // Notify Delivery Partner
        String partnerEmail = order.getDeliveryPartner().getUser().getEmail(); // Assuming relation exists
        messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-orders", event);

        log.info("Published ORDER_ASSIGNED for Order {} to Partner {}", order.getId(), partnerEmail);
    }
}
