package com.hyperlocal.dispatch.controller;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.dispatch.dto.DeliveryOfferResponse;
import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.dispatch.service.DeliveryAssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/delivery-offers")
public class DeliveryOfferController {

    private final DeliveryAssignmentService deliveryAssignmentService;
    private final DeliveryPartnerRepository deliveryPartnerRepository;

    public DeliveryOfferController(DeliveryAssignmentService deliveryAssignmentService,
                                   DeliveryPartnerRepository deliveryPartnerRepository) {
        this.deliveryAssignmentService = deliveryAssignmentService;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
    }

    @PostMapping("/{offerId}/accept")
    public ResponseEntity<DeliveryOfferResponse> acceptOffer(
            @PathVariable Long offerId,
            @RequestHeader(value = "X-Partner-Id", required = false) Long partnerIdHeader,
            @AuthenticationPrincipal User currentUser) {

        Long partnerId = resolvePartnerId(partnerIdHeader, currentUser);
        DeliveryOffer offer = deliveryAssignmentService.acceptOffer(offerId, partnerId);
        return ResponseEntity.ok(DeliveryOfferResponse.from(offer));
    }

    @PostMapping("/{offerId}/reject")
    public ResponseEntity<DeliveryOfferResponse> rejectOffer(
            @PathVariable Long offerId,
            @RequestHeader(value = "X-Partner-Id", required = false) Long partnerIdHeader,
            @AuthenticationPrincipal User currentUser) {

        Long partnerId = resolvePartnerId(partnerIdHeader, currentUser);
        DeliveryOffer offer = deliveryAssignmentService.rejectOffer(offerId, partnerId);
        return ResponseEntity.ok(DeliveryOfferResponse.from(offer));
    }

    @GetMapping("/{offerId}")
    public ResponseEntity<DeliveryOfferResponse> getOffer(@PathVariable Long offerId) {
        DeliveryOffer offer = deliveryAssignmentService.getOffer(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery offer not found with id: " + offerId));
        return ResponseEntity.ok(DeliveryOfferResponse.from(offer));
    }

    private Long resolvePartnerId(Long partnerIdHeader, User currentUser) {
        if (partnerIdHeader != null) {
            return partnerIdHeader;
        }
        if (currentUser != null) {
            return deliveryPartnerRepository.findByUserId(currentUser.getId())
                    .map(DeliveryPartner::getId)
                    .orElse(null);
        }
        return null;
    }
}
