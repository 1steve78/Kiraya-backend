package com.hyperlocal.dto;

public class DistanceResult {
    private double distanceKm;
    private double durationMinutes;

    public DistanceResult(double distanceKm, double durationMinutes) {
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
    }

    public double getDistanceKm() { return distanceKm; }
    public double getDurationMinutes() { return durationMinutes; }
}