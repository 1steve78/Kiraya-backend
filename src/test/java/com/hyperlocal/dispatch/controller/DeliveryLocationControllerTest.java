package com.hyperlocal.dispatch.controller;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.Role;
import com.hyperlocal.dispatch.dto.LocationUpdateRequest;
import com.hyperlocal.dispatch.service.DeliveryTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryLocationControllerTest {

    @Mock
    private DeliveryTrackingService deliveryTrackingService;

    @InjectMocks
    private DeliveryLocationController controller;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(10L);
        user.setEmail("rider@test.com");
        user.setRole(Role.DELIVERY_PARTNER);
        authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    @DisplayName("WebSocket handler delegates processing directly to DeliveryTrackingService")
    void testHandleLocationUpdate_DelegatesToService() {
        LocationUpdateRequest request = new LocationUpdateRequest(12.9716, 77.5946, Instant.now());

        controller.handleLocationUpdate(request, authentication);

        verify(deliveryTrackingService).receiveLocation(request, authentication);
    }

    @Test
    @DisplayName("WebSocket exception handler cleanly logs without throwing")
    void testHandleException_DoesNotThrow() {
        assertDoesNotThrow(() -> controller.handleException(new IllegalArgumentException("Invalid coords")));
    }
}
