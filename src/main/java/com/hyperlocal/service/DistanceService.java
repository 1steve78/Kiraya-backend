package com.hyperlocal.service;

import com.hyperlocal.entity.DeliveryPartner;
import com.hyperlocal.entity.Shop;
import com.hyperlocal.dto.DistanceResult;

public interface DistanceService {
    DistanceResult calculateDistance(DeliveryPartner partner, Shop shop);
}