package com.hyperlocal.dispatch.service;

import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class DeliveryOfferStateMachine {

    private final Map<DeliveryOfferStatus, Set<DeliveryOfferStatus>> validTransitions = Map.of(
            DeliveryOfferStatus.PENDING, EnumSet.of(
                    DeliveryOfferStatus.ACCEPTED,
                    DeliveryOfferStatus.REJECTED,
                    DeliveryOfferStatus.EXPIRED,
                    DeliveryOfferStatus.CANCELLED
            ),
            DeliveryOfferStatus.ACCEPTED, EnumSet.noneOf(DeliveryOfferStatus.class),
            DeliveryOfferStatus.REJECTED, EnumSet.noneOf(DeliveryOfferStatus.class),
            DeliveryOfferStatus.EXPIRED, EnumSet.noneOf(DeliveryOfferStatus.class),
            DeliveryOfferStatus.CANCELLED, EnumSet.noneOf(DeliveryOfferStatus.class)
    );

    public boolean canTransition(DeliveryOfferStatus current, DeliveryOfferStatus target) {
        if (current == null || target == null) {
            return false;
        }
        Set<DeliveryOfferStatus> allowed = validTransitions.get(current);
        return allowed != null && allowed.contains(target);
    }

    public boolean isTerminal(DeliveryOfferStatus status) {
        return status != null && status != DeliveryOfferStatus.PENDING;
    }
}
