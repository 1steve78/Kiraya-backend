package com.hyperlocal.service;

import com.hyperlocal.entity.DeliveryPartner;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.model.DistanceResult;
import com.hyperlocal.model.Location;
import org.springframework.stereotype.Service;

@Service("mockDistanceService")
public class MockDistanceService implements DistanceService {

    @Override
    public DistanceResult getRoute(Location origin, Location destination) {
        double distance = 1.0 + (Math.random() * 4.0);
        double duration = distance * 3.0;
        return new DistanceResult(distance, duration);
    }

    @Override
    public DistanceResult calculateDistance(DeliveryPartner partner, Shop shop) {
        Location partnerLoc = (partner != null) ? partner.getLocation() : null;
        Location shopLoc = (shop != null) ? shop.getLocation() : null;
        return getRoute(partnerLoc, shopLoc);
    }
}