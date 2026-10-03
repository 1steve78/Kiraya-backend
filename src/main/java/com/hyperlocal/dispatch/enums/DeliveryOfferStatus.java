package com.hyperlocal.dispatch.enums;

public enum DeliveryOfferStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }

    public boolean canTransitionTo(DeliveryOfferStatus nextStatus) {
        if (this == PENDING) {
            return nextStatus == ACCEPTED || nextStatus == REJECTED || nextStatus == EXPIRED || nextStatus == CANCELLED;
        }
        return false;
    }
}
