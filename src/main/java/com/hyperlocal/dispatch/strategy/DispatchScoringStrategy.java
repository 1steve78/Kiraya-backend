package com.hyperlocal.dispatch.strategy;

import com.hyperlocal.dispatch.dto.DeliveryPartnerCandidate;

public interface DispatchScoringStrategy {
    
    /**
     * Calculates the score for a delivery partner candidate.
     * Lower score is better (think of it like cost/distance).
     */
    double calculateScore(DeliveryPartnerCandidate candidate, int activeWorkload);
}
