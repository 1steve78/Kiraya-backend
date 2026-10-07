package com.hyperlocal.dispatch.controller;

import com.hyperlocal.common.exception.GlobalExceptionHandler;
import com.hyperlocal.dispatch.entity.DeliveryOffer;
import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import com.hyperlocal.dispatch.exception.InvalidAssignmentStateException;
import com.hyperlocal.dispatch.repository.DeliveryPartnerRepository;
import com.hyperlocal.dispatch.service.DeliveryAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeliveryOfferControllerTest {

    @Mock
    private DeliveryAssignmentService deliveryAssignmentService;

    @Mock
    private DeliveryPartnerRepository deliveryPartnerRepository;

    @InjectMocks
    private DeliveryOfferController deliveryOfferController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(deliveryOfferController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /delivery-offers/{offerId}/accept - Success returns 200 and ACCEPTED offer")
    void testAcceptOffer_Success() throws Exception {
        DeliveryOffer offer = new DeliveryOffer(100L, 1L, Instant.now().plusSeconds(30));
        offer.setId(10L);
        offer.setStatus(DeliveryOfferStatus.ACCEPTED);
        offer.setRespondedAt(Instant.now());

        when(deliveryAssignmentService.acceptOffer(eq(10L), eq(1L))).thenReturn(offer);

        mockMvc.perform(post("/delivery-offers/10/accept")
                        .header("X-Partner-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.deliveryId").value(100))
                .andExpect(jsonPath("$.partnerId").value(1))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("POST /delivery-offers/{offerId}/accept - Conflict returns 409 when already assigned or duplicate accept")
    void testAcceptOffer_Conflict() throws Exception {
        when(deliveryAssignmentService.acceptOffer(eq(10L), eq(1L)))
                .thenThrow(new InvalidAssignmentStateException("DELIVERY_ALREADY_ASSIGNED", "This delivery is no longer available."));

        mockMvc.perform(post("/delivery-offers/10/accept")
                        .header("X-Partner-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_ALREADY_ASSIGNED"))
                .andExpect(jsonPath("$.message").value("This delivery is no longer available."));
    }

    @Test
    @DisplayName("POST /delivery-offers/{offerId}/accept - Optimistic lock conflict returns 409")
    void testAcceptOffer_OptimisticLockConflict() throws Exception {
        when(deliveryAssignmentService.acceptOffer(eq(10L), eq(1L)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(DeliveryOffer.class, 10L));

        mockMvc.perform(post("/delivery-offers/10/accept")
                        .header("X-Partner-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_ALREADY_ASSIGNED"))
                .andExpect(jsonPath("$.message").value("This delivery is no longer available."));
    }

    @Test
    @DisplayName("POST /delivery-offers/{offerId}/reject - Success returns 200 and REJECTED offer")
    void testRejectOffer_Success() throws Exception {
        DeliveryOffer offer = new DeliveryOffer(100L, 1L, Instant.now().plusSeconds(30));
        offer.setId(10L);
        offer.setStatus(DeliveryOfferStatus.REJECTED);
        offer.setRespondedAt(Instant.now());

        when(deliveryAssignmentService.rejectOffer(eq(10L), eq(1L))).thenReturn(offer);

        mockMvc.perform(post("/delivery-offers/10/reject")
                        .header("X-Partner-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.deliveryId").value(100))
                .andExpect(jsonPath("$.partnerId").value(1))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    @DisplayName("GET /delivery-offers/{offerId} - Success returns offer")
    void testGetOffer_Success() throws Exception {
        DeliveryOffer offer = new DeliveryOffer(100L, 1L, Instant.now().plusSeconds(30));
        offer.setId(10L);

        when(deliveryAssignmentService.getOffer(10L)).thenReturn(Optional.of(offer));

        mockMvc.perform(get("/delivery-offers/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.deliveryId").value(100))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
