package com.hyperlocal.model;

public class DistanceResult {

    private double distanceKm;
    private double durationMinutes;

    public DistanceResult() {
    }

    public DistanceResult(double distanceKm, double durationMinutes) {
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public double getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(double durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}
