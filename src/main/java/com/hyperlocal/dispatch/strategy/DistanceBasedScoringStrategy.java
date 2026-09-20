package com.hyperlocal.dispatch.strategy;

import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;
import org.springframework.stereotype.Component;

@Component
public class DistanceBasedScoringStrategy implements DispatchScoringStrategy {

    @Override
    public double calculateScore(DeliveryPartnerCandidate candidate, int activeWorkload) {
        // In this basic version, distance is the dominant factor.
        return candidate.getDistanceKm() != null ? candidate.getDistanceKm() : Double.MAX_VALUE;
    }
}
