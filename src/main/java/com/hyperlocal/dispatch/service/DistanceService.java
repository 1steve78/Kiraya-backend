package com.hyperlocal.dispatch.service;

import com.hyperlocal.catalog.entity.Shop;
import com.hyperlocal.common.model.Location;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.exception.DistanceServiceException;
import com.hyperlocal.dispatch.model.DistanceResult;

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
