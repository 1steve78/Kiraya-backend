package com.hyperlocal.dispatch.dto;

import com.hyperlocal.dispatch.entity.DeliveryPartner;

public class DispatchCandidate {

    private final DeliveryPartner deliveryPartner;
    private final double distanceKm;
    private  final double etaMinutes;
    private final int activeOrders;
    private final double score;

    public DispatchCandidate(DeliveryPartner deliveryPartner, double distanceKm, double etaMinutes, int activeOrders, double score) {
        this.deliveryPartner = deliveryPartner;
        this.distanceKm = distanceKm;
        this.etaMinutes = etaMinutes;
        this.activeOrders = activeOrders;
        this.score = score;
    }

    public DeliveryPartner getDeliveryPartner() { return deliveryPartner; }
    public double getDistanceKm() { return distanceKm; }
    public double getEtaMinutes() { return etaMinutes; }
    public int getActiveOrders() { return activeOrders; }
    public double getScore() { return score; }
}
