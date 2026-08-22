package com.hyperlocal.service;

import com.hyperlocal.entity.DeliveryPartner;
import com.hyperlocal.entity.Order;
import com.hyperlocal.entity.OrderStatus;
import com.hyperlocal.dto.DispatchCandidate;
import com.hyperlocal.dto.DistanceResult;
import com.hyperlocal.repository.DeliveryPartnerRepository;
import com.hyperlocal.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DispatchService {

    private final OrderRepository orderRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final DistanceService distanceService; // Injects the interface, not the mock directly!

    // Configuration constants (Could be moved to application.yml later)
    private static final int MAX_ACTIVE_ORDERS = 3;
    private static final double WEIGHT_DISTANCE = 0.5;
    private static final double WEIGHT_WORKLOAD = 2.0;
    private static final double WEIGHT_ETA = 0.3;

    public DispatchService(OrderRepository orderRepository,
                           DeliveryPartnerRepository deliveryPartnerRepository,
                           DistanceService distanceService) {
        this.orderRepository = orderRepository;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
        this.distanceService = distanceService;
    }

    @Transactional
    public void dispatchOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order must be READY_FOR_PICKUP to be dispatched");
        }
        if (order.getDeliveryPartner() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already assigned to a partner");
        }


        List<DeliveryPartner> availablePartners = deliveryPartnerRepository.findByIsAvailableTrue();


        List<OrderStatus> activeStatuses = List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY);

        Optional<DispatchCandidate> bestCandidate = availablePartners.stream()
                .map(partner -> {
                    int activeOrders = orderRepository.countActiveOrdersForPartner(partner.getId(), activeStatuses);
                    return new Object[]{partner, activeOrders};
                })
                .filter(data -> (int) data[1] <= MAX_ACTIVE_ORDERS) // Filter by workload
                .map(data -> {
                    DeliveryPartner partner = (DeliveryPartner) data[0];
                    int activeOrders = (int) data[1];

                    DistanceResult distanceData = distanceService.calculateDistance(partner, order.getShop());

                    // Calculate Score: Lower is better
                    double score = (distanceData.getDistanceKm() * WEIGHT_DISTANCE)
                            + (activeOrders * WEIGHT_WORKLOAD)
                            + (distanceData.getDurationMinutes() * WEIGHT_ETA);

                    return new DispatchCandidate(partner, distanceData.getDistanceKm(), distanceData.getDurationMinutes(), activeOrders, score);
                })
                .min(Comparator.comparingDouble(DispatchCandidate::getScore)); // Select lowest score


        if (bestCandidate.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No suitable delivery partner available");
        }

        order.setDeliveryPartner(bestCandidate.get().getDeliveryPartner());
        orderRepository.save(order);
    }
}