package com.hyperlocal.notification.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.dispatch.dto.Coordinates;
import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.notification.dto.DeliveryOfferEvent;
import com.hyperlocal.notification.dto.NewDeliveryEvent;
import com.hyperlocal.notification.dto.OrderAssignedEvent;
import com.hyperlocal.notification.dto.OrderStatusEvent;
import com.hyperlocal.notification.dto.RealtimeEvent;
import com.hyperlocal.notification.enums.EventType;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Defers event publishing until after successful transaction commit.
     * If the transaction fails, errors out, or rolls back, afterCommit is not called,
     * ensuring clients never receive events for uncommitted database state changes.
     * When executed outside an active transaction synchronization, it executes immediately.
     */
    private void publishAfterCommit(Runnable publishAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        publishAction.run();
                    } catch (Exception e) {
                        log.error("Failed to execute afterCommit event publication", e);
                    }
                }
            });
        } else {
            publishAction.run();
        }
    }

    /**
     * Published when an order status changes.
     */
    public void publishOrderStatusChanged(Order order) {
        Long orderId = order.getId();
        OrderStatus status = order.getStatus();
        Long shopId = (order.getShop() != null) ? order.getShop().getId() : null;
        String customerEmail = (order.getCustomer() != null) ? order.getCustomer().getEmail() : null;
        String shopOwnerEmail = (order.getShop() != null && order.getShop().getOwner() != null)
                ? order.getShop().getOwner().getEmail() : null;

        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.ORDER_STATUS_CHANGED,
                Instant.now(),
                new OrderStatusEvent(orderId, status)
        );

        publishAfterCommit(() -> {
            messagingTemplate.convertAndSend("/topic/orders/" + orderId, event);
            if (shopId != null) {
                messagingTemplate.convertAndSend("/topic/shops/" + shopId, event);
                messagingTemplate.convertAndSend("/topic/shops/" + shopId + "/orders", event);
            }
            if (customerEmail != null) {
                messagingTemplate.convertAndSendToUser(customerEmail, "/queue/orders", event);
            }
            if (shopOwnerEmail != null) {
                messagingTemplate.convertAndSendToUser(shopOwnerEmail, "/queue/shop-orders", event);
            }
            log.info("Published ORDER_STATUS_CHANGED for Order {} to Customer, Shop topic, and user queues", orderId);
        });
    }

    /**
     * Published when a new order is placed by a customer.
     */
    public void publishNewOrder(Order order) {
        Long orderId = order.getId();
        OrderStatus status = order.getStatus();
        Long shopId = (order.getShop() != null) ? order.getShop().getId() : null;
        String shopOwnerEmail = (order.getShop() != null && order.getShop().getOwner() != null)
                ? order.getShop().getOwner().getEmail() : null;

        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.NEW_ORDER,
                Instant.now(),
                new OrderStatusEvent(orderId, status)
        );

        publishAfterCommit(() -> {
            if (shopId != null) {
                messagingTemplate.convertAndSend("/topic/shops/" + shopId, event);
                messagingTemplate.convertAndSend("/topic/shops/" + shopId + "/orders", event);
            }
            if (shopOwnerEmail != null) {
                messagingTemplate.convertAndSendToUser(shopOwnerEmail, "/queue/shop-orders", event);
            }
            log.info("Published NEW_ORDER for Order {} to Shop {}", orderId, shopId);
        });
    }

    /**
     * Published when an order is cancelled.
     */
    public void publishOrderCancelled(Order order) {
        Long orderId = order.getId();
        OrderStatus status = order.getStatus();
        Long shopId = (order.getShop() != null) ? order.getShop().getId() : null;
        String customerEmail = (order.getCustomer() != null) ? order.getCustomer().getEmail() : null;
        String shopOwnerEmail = (order.getShop() != null && order.getShop().getOwner() != null)
                ? order.getShop().getOwner().getEmail() : null;

        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.ORDER_CANCELLED,
                Instant.now(),
                new OrderStatusEvent(orderId, status)
        );

        publishAfterCommit(() -> {
            messagingTemplate.convertAndSend("/topic/orders/" + orderId, event);
            if (shopId != null) {
                messagingTemplate.convertAndSend("/topic/shops/" + shopId, event);
                messagingTemplate.convertAndSend("/topic/shops/" + shopId + "/orders", event);
            }
            if (customerEmail != null) {
                messagingTemplate.convertAndSendToUser(customerEmail, "/queue/orders", event);
            }
            if (shopOwnerEmail != null) {
                messagingTemplate.convertAndSendToUser(shopOwnerEmail, "/queue/shop-orders", event);
            }
            log.info("Published ORDER_CANCELLED for Order {} to Customer and Shop", orderId);
        });
    }

    /**
     * Published when a delivery partner is assigned.
     */
    public void publishOrderAssigned(Order order) {
        if (order.getDeliveryPartner() == null) return;

        Long orderId = order.getId();
        Long partnerId = order.getDeliveryPartner().getId();
        String partnerEmail = (order.getDeliveryPartner().getUser() != null)
                ? order.getDeliveryPartner().getUser().getEmail() : null;
        OrderStatus orderStatus = order.getStatus();

        RealtimeEvent<OrderAssignedEvent> event = new RealtimeEvent<>(
                EventType.ORDER_ASSIGNED,
                Instant.now(),
                new OrderAssignedEvent(orderId, partnerId)
        );

        RealtimeEvent<OrderStatusEvent> poolEvent = new RealtimeEvent<>(
                EventType.DELIVERY_ASSIGNED,
                Instant.now(),
                new OrderStatusEvent(orderId, orderStatus)
        );

        publishAfterCommit(() -> {
            messagingTemplate.convertAndSend("/topic/orders/" + orderId, event);
            if (partnerEmail != null) {
                messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-orders", event);
            }
            messagingTemplate.convertAndSend("/topic/deliveries/pool", poolEvent);
            log.info("Published ORDER_ASSIGNED for Order {} to Partner {}", orderId, partnerEmail);
        });
    }

    public void publishNewDelivery(Order order, List<DeliveryPartner> candidates) {
        Double distanceKm = 2.5;
        BigDecimal fee = BigDecimal.valueOf(150.0);

        Long orderId = order.getId();
        Long shopId = (order.getShop() != null) ? order.getShop().getId() : null;
        String shopAddress = (order.getShop() != null && order.getShop().getAddress() != null)
                ? order.getShop().getAddress() : "Unknown Shop Address";

        NewDeliveryEvent deliveryData = new NewDeliveryEvent(
                orderId,
                orderId,
                shopId,
                shopAddress,
                "Customer Destination",
                distanceKm,
                fee
        );

        RealtimeEvent<NewDeliveryEvent> event = new RealtimeEvent<>(
                EventType.NEW_DELIVERY,
                Instant.now(),
                deliveryData
        );

        List<String> partnerEmails = candidates.stream()
                .filter(p -> p.getUser() != null && p.getUser().getEmail() != null)
                .map(p -> p.getUser().getEmail())
                .toList();

        publishAfterCommit(() -> {
            messagingTemplate.convertAndSend("/topic/deliveries/pool", event);
            for (String partnerEmail : partnerEmails) {
                messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-orders", event);
            }
            log.info("Published NEW_DELIVERY for Order {} to {} candidates without leaking customer info", orderId, partnerEmails.size());
        });
    }

    public void publishDeliveryStatusChanged(Order order) {
        Long orderId = order.getId();
        OrderStatus status = order.getStatus();
        Long shopId = (order.getShop() != null) ? order.getShop().getId() : null;
        String partnerEmail = (order.getDeliveryPartner() != null && order.getDeliveryPartner().getUser() != null)
                ? order.getDeliveryPartner().getUser().getEmail() : null;

        RealtimeEvent<OrderStatusEvent> event = new RealtimeEvent<>(
                EventType.DELIVERY_STATUS_CHANGED,
                Instant.now(),
                new OrderStatusEvent(orderId, status)
        );

        publishAfterCommit(() -> {
            if (partnerEmail != null) {
                messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-orders", event);
            }
            messagingTemplate.convertAndSend("/topic/orders/" + orderId, event);
            if (shopId != null) {
                messagingTemplate.convertAndSend("/topic/shops/" + shopId, event);
            }
            log.info("Published DELIVERY_STATUS_CHANGED for Order {}", orderId);
        });
    }

    public void publishDeliveryOfferCreated(DeliveryOffer offer, Order order, DeliveryPartner partner) {
        Coordinates pickup = null;
        if (order.getShop() != null && order.getShop().getLatitude() != null && order.getShop().getLongitude() != null) {
            pickup = new Coordinates(order.getShop().getLatitude(), order.getShop().getLongitude());
        }

        DeliveryOfferEvent offerEvent = new DeliveryOfferEvent(
                offer.getId(),
                offer.getDeliveryId(),
                pickup,
                offer.getExpiresAt()
        );

        RealtimeEvent<DeliveryOfferEvent> event = new RealtimeEvent<>(
                EventType.DELIVERY_OFFER_CREATED,
                Instant.now(),
                offerEvent
        );

        Long offerId = offer.getId();
        Long deliveryId = offer.getDeliveryId();
        Long partnerId = offer.getPartnerId();
        String partnerEmail = (partner != null && partner.getUser() != null)
                ? partner.getUser().getEmail() : null;

        publishAfterCommit(() -> {
            if (partnerEmail != null) {
                messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-offers", event);
            }
            messagingTemplate.convertAndSend("/user/" + partnerId + "/queue/delivery-offers", event);
            messagingTemplate.convertAndSend("/topic/partners/" + partnerId + "/offers", event);
            log.info("Published DELIVERY_OFFER_CREATED for offer {} (order {}) to partner {}",
                    offerId, deliveryId, partnerId);
        });
    }

    public void publishDeliveryOfferAccepted(DeliveryOffer offer, Order order, DeliveryPartner partner) {
        Long offerId = offer.getId();
        Long partnerId = offer.getPartnerId();
        String partnerEmail = (partner != null && partner.getUser() != null)
                ? partner.getUser().getEmail() : null;

        RealtimeEvent<DeliveryOfferEvent> event = new RealtimeEvent<>(
                EventType.DELIVERY_OFFER_ACCEPTED,
                Instant.now(),
                new DeliveryOfferEvent(offer.getId(), offer.getDeliveryId(), null, offer.getExpiresAt())
        );

        publishAfterCommit(() -> {
            if (partnerEmail != null) {
                messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-offers", event);
            }
            messagingTemplate.convertAndSend("/topic/partners/" + partnerId + "/offers", event);
            log.info("Published DELIVERY_OFFER_ACCEPTED for offer {} by partner {}", offerId, partnerId);
        });
    }

    public void publishDeliveryOfferRejected(DeliveryOffer offer, Order order, DeliveryPartner partner) {
        Long offerId = offer.getId();
        Long partnerId = offer.getPartnerId();
        String partnerEmail = (partner != null && partner.getUser() != null)
                ? partner.getUser().getEmail() : null;

        RealtimeEvent<DeliveryOfferEvent> event = new RealtimeEvent<>(
                EventType.DELIVERY_OFFER_REJECTED,
                Instant.now(),
                new DeliveryOfferEvent(offer.getId(), offer.getDeliveryId(), null, offer.getExpiresAt())
        );

        publishAfterCommit(() -> {
            if (partnerEmail != null) {
                messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-offers", event);
            }
            messagingTemplate.convertAndSend("/topic/partners/" + partnerId + "/offers", event);
            log.info("Published DELIVERY_OFFER_REJECTED for offer {} by partner {}", offerId, partnerId);
        });
    }

    public void publishDeliveryOfferExpired(DeliveryOffer offer, Order order, DeliveryPartner partner) {
        Long offerId = offer.getId();
        Long partnerId = offer.getPartnerId();
        String partnerEmail = (partner != null && partner.getUser() != null)
                ? partner.getUser().getEmail() : null;

        RealtimeEvent<DeliveryOfferEvent> event = new RealtimeEvent<>(
                EventType.DELIVERY_OFFER_EXPIRED,
                Instant.now(),
                new DeliveryOfferEvent(offer.getId(), offer.getDeliveryId(), null, offer.getExpiresAt())
        );

        publishAfterCommit(() -> {
            if (partnerEmail != null) {
                messagingTemplate.convertAndSendToUser(partnerEmail, "/queue/delivery-offers", event);
            }
            messagingTemplate.convertAndSend("/user/" + partnerId + "/queue/delivery-offers", event);
            messagingTemplate.convertAndSend("/topic/partners/" + partnerId + "/offers", event);
            log.info("Published DELIVERY_OFFER_EXPIRED for offer {} by partner {}", offerId, partnerId);
        });
    }
}
