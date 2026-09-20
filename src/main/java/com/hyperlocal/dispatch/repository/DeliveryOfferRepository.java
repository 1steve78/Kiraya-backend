package com.hyperlocal.dispatch.repository;

import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryOfferRepository extends JpaRepository<DeliveryOffer, Long> {

    List<DeliveryOffer> findByDeliveryId(Long deliveryId);

    List<DeliveryOffer> findByDeliveryIdOrderByCreatedAtDesc(Long deliveryId);

    List<DeliveryOffer> findByPartnerIdAndStatus(Long partnerId, DeliveryOfferStatus status);

    Optional<DeliveryOffer> findFirstByDeliveryIdAndStatus(Long deliveryId, DeliveryOfferStatus status);

    Optional<DeliveryOffer> findFirstByPartnerIdAndStatus(Long partnerId, DeliveryOfferStatus status);

    List<DeliveryOffer> findByStatusAndExpiresAtBefore(DeliveryOfferStatus status, Instant now);
}
