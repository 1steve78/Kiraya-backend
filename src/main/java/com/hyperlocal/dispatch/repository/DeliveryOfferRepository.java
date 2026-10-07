package com.hyperlocal.dispatch.repository;

import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Modifying(clearAutomatically = true)
    @Query("UPDATE DeliveryOffer o SET o.status = :newStatus, o.respondedAt = :respondedAt WHERE o.id = :id AND o.status = :expectedStatus")
    int updateOfferStatusConditionally(@Param("id") Long id,
                                       @Param("expectedStatus") DeliveryOfferStatus expectedStatus,
                                       @Param("newStatus") DeliveryOfferStatus newStatus,
                                       @Param("respondedAt") Instant respondedAt);
}
