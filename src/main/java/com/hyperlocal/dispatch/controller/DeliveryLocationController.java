package com.hyperlocal.dispatch.controller;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.dispatch.dto.LocationUpdateRequest;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.dispatch.service.LocationService;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import java.util.Optional;

@Controller
public class DeliveryLocationController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryLocationController.class);

    private final LocationService locationService;
    private final DeliveryPartnerRepository deliveryPartnerRepository;

    public DeliveryLocationController(LocationService locationService, DeliveryPartnerRepository deliveryPartnerRepository) {
        this.locationService = locationService;
        this.deliveryPartnerRepository = deliveryPartnerRepository;
    }

    @MessageMapping("/delivery/location")
    public void handleLocationUpdate(
            @Payload @Valid LocationUpdateRequest request,
            Authentication authentication
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            log.warn("Received location update without valid authentication");
            return;
        }

        Optional<DeliveryPartner> partnerOpt = deliveryPartnerRepository.findByUserId(user.getId());
        if (partnerOpt.isEmpty()) {
            log.warn("User {} is not a delivery partner", user.getEmail());
            return;
        }

        Long partnerId = partnerOpt.get().getId();
        locationService.processLocation(partnerId, request);
    }
}
