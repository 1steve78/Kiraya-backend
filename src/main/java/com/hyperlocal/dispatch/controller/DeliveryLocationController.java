package com.hyperlocal.dispatch.controller;

import com.hyperlocal.dispatch.dto.LocationUpdateRequest;
import com.hyperlocal.dispatch.service.DeliveryTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class DeliveryLocationController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryLocationController.class);

    private final DeliveryTrackingService deliveryTrackingService;

    public DeliveryLocationController(DeliveryTrackingService deliveryTrackingService) {
        this.deliveryTrackingService = deliveryTrackingService;
    }

    @MessageMapping("/delivery/location")
    public void handleLocationUpdate(
            @Payload LocationUpdateRequest request,
            Authentication authentication
    ) {
        deliveryTrackingService.receiveLocation(request, authentication);
    }

    @MessageExceptionHandler
    public void handleException(Exception ex) {
        log.warn("Rejected location update in WebSocket handler: {}", ex.getMessage());
    }
}
