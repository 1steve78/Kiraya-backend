package com.hyperlocal.dispatch.service;

import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import com.hyperlocal.dispatch.repository.DeliveryOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OfferExpirationService {

    private static final Logger log = LoggerFactory.getLogger(OfferExpirationService.class);

    private final DeliveryOfferRepository deliveryOfferRepository;
    private final DeliveryAssignmentService deliveryAssignmentService;

    public OfferExpirationService(DeliveryOfferRepository deliveryOfferRepository,
                                  DeliveryAssignmentService deliveryAssignmentService) {
        this.deliveryOfferRepository = deliveryOfferRepository;
        this.deliveryAssignmentService = deliveryAssignmentService;
    }

    @Scheduled(fixedDelay = 1000)
    public void processExpiredOffers() {
        processExpiredOffersAt(Instant.now());
    }

    public List<DeliveryOffer> processExpiredOffersAt(Instant now) {
        List<DeliveryOffer> expiredOffers = deliveryOfferRepository
                .findByStatusAndExpiresAtBefore(DeliveryOfferStatus.PENDING, now);

        if (expiredOffers.isEmpty()) {
            return List.of();
        }

        log.debug("Found {} pending offers past expiration at {}", expiredOffers.size(), now);

        List<DeliveryOffer> processed = new ArrayList<>();
        for (DeliveryOffer offer : expiredOffers) {
            try {
                deliveryAssignmentService.expireOffer(offer.getId())
                        .ifPresent(processed::add);
            } catch (Exception e) {
                log.error("Failed to expire offer with id: {}", offer.getId(), e);
            }
        }
        return processed;
    }
}
