package com.hyperlocal.service;

import com.hyperlocal.dto.EventType;
import com.hyperlocal.dto.OrderAssignedEvent;
import com.hyperlocal.dto.OrderStatusEvent;
import com.hyperlocal.dto.NewDeliveryEvent;
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

    /**
     * Published when an order status changes.
     * Sends to:
     *  - /topic/orders/{orderId}          — customer tracking page subscribes here
     *  - /topic/shops/{shopId}            — shop dashboard subscribes here
     *  - /user/{customerEmail}/queue/orders       — authenticated customer user-queue
     *  - /user/{shopOwnerEmail}/queue/shop-orders — authenticated shop owner user-queue
     */
    public void publishOrderStatusChanged(Order order) {
        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.ORDER_STATUS_CHANGED,
                Instant.now(),
                new OrderStatusEvent(order.getId(), order.getStatus())
        );

        // Broadcast topics (for subscribers without user authentication)
        messagingTemplate.convertAndSend("/topic/orders/" + order.getId(), event);
        messagingTemplate.convertAndSend("/topic/shops/" + order.getShop().getId(), event);
        messagingTemplate.convertAndSend("/topic/shops/" + order.getShop().getId() + "/orders", event);

        // User-specific queues (for authenticated STOMP connections)
        String customerEmail = order.getCustomer().getEmail();
        messagingTemplate.convertAndSendToUser(customerEmail, "/queue/orders", event);

        String shopOwnerEmail = order.getShop().getOwner().getEmail();
        messagingTemplate.convertAndSendToUser(shopOwnerEmail, "/queue/shop-orders", event);

        log.info("Published ORDER_STATUS_CHANGED for Order {} to Customer, Shop topic, and user queues", order.getId());
    }

    /**
     * Published when a new order is placed by a customer.
     * Sends to:
     *  - /topic/shops/{shopId}            — shop dashboard subscribes here
     *  - /user/{shopOwnerEmail}/queue/shop-orders — authenticated shop owner user-queue
     */
    public void publishNewOrder(Order order) {
        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.NEW_ORDER,
                Instant.now(),
                new OrderStatusEvent(order.getId(), order.getStatus())
        );

        messagingTemplate.convertAndSend("/topic/shops/" + order.getShop().getId(), event);
        messagingTemplate.convertAndSend("/topic/shops/" + order.getShop().getId() + "/orders", event);

        String shopOwnerEmail = order.getShop().getOwner().getEmail();
        messagingTemplate.convertAndSendToUser(shopOwnerEmail, "/queue/shop-orders", event);

        log.info("Published NEW_ORDER for Order {} to Shop {}", order.getId(), order.getShop().getId());
    }

    /**
     * Published when an order is cancelled.
     * Sends to:
     *  - /topic/orders/{orderId}          — customer tracking page
     *  - /topic/shops/{shopId}            — shop dashboard
     *  - user queues for customer and shop owner
     */
    public void publishOrderCancelled(Order order) {
        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.ORDER_CANCELLED,
                Instant.now(),
                new OrderStatusEvent(order.getId(), order.getStatus())
        );

        messagingTemplate.convertAndSend("/topic/orders/" + order.getId(), event);
        messagingTemplate.convertAndSend("/topic/shops/" + order.getShop().getId(), event);
        messagingTemplate.convertAndSend("/topic/shops/" + order.getShop().getId() + "/orders", event);

        String customerEmail = order.getCustomer().getEmail();
        messagingTemplate.convertAndSendToUser(customerEmail, "/queue/orders", event);

        String shopOwnerEmail = order.getShop().getOwner().getEmail();
        messagingTemplate.convertAndSendToUser(shopOwnerEmail, "/queue/shop-orders", event);

        log.info("Published ORDER_CANCELLED for Order {} to Customer and Shop", order.getId());
    }

    /**
     * Published when a delivery partner is assigned.
     * Sends to:
     *  - /topic/orders/{orderId}          — customer tracking page
     *  - /user/{partnerEmail}/queue/delivery-orders — delivery partner user-queue
     */
    public void publishOrderAssigned(Order order) {
        if (order.getDeliveryPartner() == null) return;

        RealtimeEvent<OrderAssignedEvent> event = new RealtimeEvent<>(
                EventType.ORDER_ASSIGNED,
                Instant.now(),
                new OrderAssignedEvent(order.getId(), order.getDeliveryPartner().getId())
        );

        messagingTemplate.convertAndSend("/topic/orders/" + order.getId(), event);

        String partnerEmail = order.getDeliveryPartner().getUser().getEmail();
        messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-orders", event);

        // Also broadcast the assignment to the generic deliveries pool so other partners can remove it
        RealtimeEvent<OrderStatusEvent> poolEvent = new RealtimeEvent<>(
                EventType.DELIVERY_ASSIGNED,
                Instant.now(),
                new OrderStatusEvent(order.getId(), order.getStatus())
        );
        messagingTemplate.convertAndSend("/topic/deliveries/pool", poolEvent);

        log.info("Published ORDER_ASSIGNED for Order {} to Partner {}", order.getId(), partnerEmail);
    }

    public void publishNewDelivery(Order order, java.util.List<com.hyperlocal.entity.DeliveryPartner> candidates) {
        // Calculate a fake or real distance & fee
        Double distanceKm = 2.5; // Example
        java.math.BigDecimal fee = java.math.BigDecimal.valueOf(150.0); // Example

        NewDeliveryEvent deliveryData = new NewDeliveryEvent(
            order.getId(),
            order.getId(),
            order.getShop().getId(),
            order.getShop().getAddress() != null ? order.getShop().getAddress() : "Unknown Shop Address",
            "Customer Destination",
            distanceKm,
            fee
        );

        RealtimeEvent<NewDeliveryEvent> event = new RealtimeEvent<>(
                EventType.NEW_DELIVERY,
                Instant.now(),
                deliveryData
        );

        // Broadcast to a generic topic that delivery partners subscribe to
        messagingTemplate.convertAndSend("/topic/deliveries/pool", event);

        // OR we can send to each candidate individually
        for (com.hyperlocal.entity.DeliveryPartner partner : candidates) {
            String partnerEmail = partner.getUser().getEmail();
            messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-orders", event);
        }
        
        log.info("Published NEW_DELIVERY for Order {} to {} candidates without leaking customer info", order.getId(), candidates.size());
    }

    public void publishDeliveryStatusChanged(Order order) {
        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.DELIVERY_STATUS_CHANGED,
                Instant.now(),
                new OrderStatusEvent(order.getId(), order.getStatus())
        );

        if (order.getDeliveryPartner() != null) {
            String partnerEmail = order.getDeliveryPartner().getUser().getEmail();
            messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-orders", event);
        }
        
        messagingTemplate.convertAndSend("/topic/orders/" + order.getId(), event);
        messagingTemplate.convertAndSend("/topic/shops/" + order.getShop().getId(), event);
        
        log.info("Published DELIVERY_STATUS_CHANGED for Order {}", order.getId());
    }
}
