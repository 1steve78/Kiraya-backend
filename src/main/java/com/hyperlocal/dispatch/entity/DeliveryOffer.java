package com.hyperlocal.dispatch.entity;

import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "delivery_offers", indexes = {
        @Index(name = "idx_offer_delivery_id", columnList = "delivery_id"),
        @Index(name = "idx_offer_partner_id", columnList = "partner_id")
})
public class DeliveryOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delivery_id", nullable = false)
    private Long deliveryId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryOfferStatus status = DeliveryOfferStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public DeliveryOffer() {
    }

    public DeliveryOffer(Long deliveryId, Long partnerId, Instant expiresAt) {
        this.deliveryId = deliveryId;
        this.partnerId = partnerId;
        this.status = DeliveryOfferStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public DeliveryOffer(Long deliveryId, Long partnerId, DeliveryOfferStatus status, Instant createdAt, Instant expiresAt) {
        this.deliveryId = deliveryId;
        this.partnerId = partnerId;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(Long deliveryId) {
        this.deliveryId = deliveryId;
    }

    public Long getOrderId() {
        return deliveryId;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public DeliveryOfferStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryOfferStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }
}
