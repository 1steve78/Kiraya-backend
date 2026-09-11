package com.hyperlocal.service;

import com.hyperlocal.dto.AssignDeliveryRequest;
import com.hyperlocal.dto.AvailabilityRequest;
import com.hyperlocal.dto.OrderItemResponse;
import com.hyperlocal.dto.OrderResponse;
import com.hyperlocal.entity.DeliveryPartner;
import com.hyperlocal.entity.Order;
import com.hyperlocal.model.OrderStatus;
import com.hyperlocal.exception.AccessDeniedException;
import com.hyperlocal.exception.DeliveryPartnerNotFoundException;
import com.hyperlocal.exception.InvalidOrderStateException;
import com.hyperlocal.exception.OrderNotFoundException;
import com.hyperlocal.repository.DeliveryPartnerRepository;
import com.hyperlocal.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class DeliveryService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final OrderEventPublisher orderEventPublisher;

    public DeliveryService(DeliveryPartnerRepository deliveryPartnerRepository,
                           OrderRepository orderRepository,
                           OrderStateMachine orderStateMachine,
                           OrderEventPublisher orderEventPublisher) {
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.orderRepository = orderRepository;
        this.orderStateMachine = orderStateMachine;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Transactional
    public void updateAvailability(Long userId, AvailabilityRequest request) {
        DeliveryPartner partner = deliveryPartnerRepository.findByUserId(userId)
                .orElseThrow(() -> new DeliveryPartnerNotFoundException("Delivery profile not found for user id: " + userId));
        partner.setAvailable(request.isAvailable());
        deliveryPartnerRepository.save(partner);
    }

    @Transactional
    public OrderResponse assignDelivery(Long orderId, AssignDeliveryRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new InvalidOrderStateException("Order must be READY_FOR_PICKUP to assign delivery");
        }

        if (order.getDeliveryPartner() != null) {
            throw new InvalidOrderStateException("Order is already assigned to a delivery partner");
        }

        DeliveryPartner partner = deliveryPartnerRepository.findById(request.getDeliveryPartnerId())
                .orElseThrow(() -> new DeliveryPartnerNotFoundException("Delivery Partner not found with id: " + request.getDeliveryPartnerId()));

        if (!partner.isAvailable()) {
            throw new InvalidOrderStateException("Delivery Partner is currently unavailable");
        }

        order.setDeliveryPartner(partner);
        order.setStatus(OrderStatus.ASSIGNED);
        partner.setAvailable(false);
        deliveryPartnerRepository.save(partner);

        Order savedOrder = orderRepository.save(order);
        return mapToResponse(savedOrder);
    }

    public Page<OrderResponse> getMyAssignedOrders(Long userId, OrderStatus status, Pageable pageable) {
        DeliveryPartner partner = deliveryPartnerRepository.findByUserId(userId)
                .orElseThrow(() -> new DeliveryPartnerNotFoundException("Delivery profile not found for user id: " + userId));

        Page<Order> ordersPage;
        if (status != null) {
            ordersPage = orderRepository.findByDeliveryPartnerIdAndStatus(partner.getId(), status, pageable);
        } else {
            ordersPage = orderRepository.findByDeliveryPartnerId(partner.getId(), pageable);
        }

        return ordersPage.map(this::mapToResponse);
    }

    @Transactional
    public OrderResponse updateDeliveryStatus(Long userId, Long orderId, OrderStatus newStatus) {
        DeliveryPartner partner = deliveryPartnerRepository.findByUserId(userId)
                .orElseThrow(() -> new DeliveryPartnerNotFoundException("Delivery profile not found for user id: " + userId));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        // If order is unassigned, and partner is trying to ACCEPT it
        if (order.getDeliveryPartner() == null && newStatus == OrderStatus.ACCEPTED) {
            if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
                throw new InvalidOrderStateException("Order must be READY_FOR_PICKUP to accept");
            }
            // Assign partner
            order.setDeliveryPartner(partner);
            order.setStatus(OrderStatus.ACCEPTED);
            partner.setAvailable(false);
            deliveryPartnerRepository.save(partner);
            
            Order savedOrder = orderRepository.save(order);
            // Notify others it was assigned so it disappears from pool
            orderEventPublisher.publishOrderAssigned(savedOrder);
            return mapToResponse(savedOrder);
        }

        // Strict Resource Ownership Check for already assigned orders
        if (order.getDeliveryPartner() == null || !order.getDeliveryPartner().getId().equals(partner.getId())) {
            throw new AccessDeniedException("You are not assigned to this order.");
        }

        if (!orderStateMachine.canTransition(order.getStatus(), newStatus)) {
            throw new InvalidOrderStateException("Order cannot transition from "
                    + order.getStatus() + " to " + newStatus);
        }

        order.setStatus(newStatus);

        if (newStatus == OrderStatus.DELIVERED) {
            partner.setAvailable(true);
            deliveryPartnerRepository.save(partner);
        }

        Order savedOrder = orderRepository.save(order);
        return mapToResponse(savedOrder);
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        if (order.getShop() != null) {
            response.setShopId(order.getShop().getId());
        }
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());

        if (order.getItems() != null) {
            response.setItems(order.getItems().stream().map(item -> {
                OrderItemResponse itemResponse = new OrderItemResponse();
                itemResponse.setId(item.getId());
                if (item.getProduct() != null) {
                    itemResponse.setProductId(item.getProduct().getId());
                    itemResponse.setProductName(item.getProduct().getName());
                }
                itemResponse.setQuantity(item.getQuantity());
                itemResponse.setUnitPrice(item.getUnitPrice());
                itemResponse.setSubtotal(item.getSubtotal());
                return itemResponse;
            }).toList());
        }

        return response;
    }
}