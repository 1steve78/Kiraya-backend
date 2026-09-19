package com.hyperlocal.order.service;

import com.hyperlocal.order.enums.OrderStatus;

import org.springframework.stereotype.Component;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {

    private final Map<OrderStatus, Set<OrderStatus>> validTransitions = Map.ofEntries(
            Map.entry(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY_FOR_PICKUP)),
            Map.entry(OrderStatus.READY_FOR_PICKUP, EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.OUT_FOR_DELIVERY)),
            Map.entry(OrderStatus.ASSIGNED, EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP)),
            Map.entry(OrderStatus.ACCEPTED, EnumSet.of(OrderStatus.PICKED_UP)),
            Map.entry(OrderStatus.PICKED_UP, EnumSet.of(OrderStatus.OUT_FOR_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, EnumSet.of(OrderStatus.DELIVERED)),
            Map.entry(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class)),
            Map.entry(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class))
    );

    public boolean canTransition(OrderStatus currentStatus , OrderStatus requestedStatus){
        if(currentStatus == null || requestedStatus == null){
            return  false;
        }
        Set<OrderStatus> allowedNextStates = validTransitions.get(currentStatus);

        return  allowedNextStates != null && allowedNextStates.contains(requestedStatus);
    }
}
