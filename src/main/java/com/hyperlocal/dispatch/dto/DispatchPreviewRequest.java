package com.hyperlocal.dispatch.dto;

public class DispatchPreviewRequest {
    private Double pickupLatitude;
    private Double pickupLongitude;

    public DispatchPreviewRequest() {}

    public DispatchPreviewRequest(Double pickupLatitude, Double pickupLongitude) {
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
    }

    public Double getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(Double pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public Double getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(Double pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }
}
