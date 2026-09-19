package com.hyperlocal.dispatch.dto;

public record Coordinates(double latitude ,double longitude) {
    public boolean isValid(){
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }
}
