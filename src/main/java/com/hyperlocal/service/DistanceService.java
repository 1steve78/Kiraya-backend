package com.hyperlocal.service;

import com.hyperlocal.entity.DeliveryPartner;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.exception.DistanceServiceException;
import com.hyperlocal.model.DistanceResult;
import com.hyperlocal.model.Location;

public interface DistanceService {
    DistanceResult getRoute(Location origin, Location destination);

    default DistanceResult calculateDistance(DeliveryPartner partner, Shop shop) {
        if (partner == null || partner.getLocation() == null) {
            throw new DistanceServiceException("Delivery partner location is missing");
        }
        if (shop == null || shop.getLocation() == null) {
            throw new DistanceServiceException("Shop location is missing");
        }
        return getRoute(partner.getLocation(), shop.getLocation());
    }
}