package com.hyperlocal.service;

import com.hyperlocal.entity.DeliveryPartner;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.dto.DistanceResult;
import org.springframework.stereotype.Service;

@Service
public class MockDistanceService implements DistanceService {
    @Override
    public DistanceResult calculateDistance(DeliveryPartner partner, Shop shop) {

        double distance = 1.0 + (Math.random() * 4.0);
        double duration = distance * 3.0;
        return new DistanceResult(distance, duration);
    }
}