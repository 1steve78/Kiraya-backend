package com.hyperlocal.dispatch.service;

import com.hyperlocal.dispatch.enums.DeliveryOfferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryOfferStateMachineTest {

    private DeliveryOfferStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new DeliveryOfferStateMachine();
    }

    @Test
    @DisplayName("PENDING can transition to ACCEPTED, REJECTED, EXPIRED, CANCELLED")
    void testPendingTransitions() {
        assertTrue(stateMachine.canTransition(DeliveryOfferStatus.PENDING, DeliveryOfferStatus.ACCEPTED));
        assertTrue(stateMachine.canTransition(DeliveryOfferStatus.PENDING, DeliveryOfferStatus.REJECTED));
        assertTrue(stateMachine.canTransition(DeliveryOfferStatus.PENDING, DeliveryOfferStatus.EXPIRED));
        assertTrue(stateMachine.canTransition(DeliveryOfferStatus.PENDING, DeliveryOfferStatus.CANCELLED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.PENDING, DeliveryOfferStatus.PENDING));
    }

    @Test
    @DisplayName("ACCEPTED is terminal and cannot transition to any other status")
    void testAcceptedIsTerminal() {
        assertTrue(stateMachine.isTerminal(DeliveryOfferStatus.ACCEPTED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.ACCEPTED, DeliveryOfferStatus.ACCEPTED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.ACCEPTED, DeliveryOfferStatus.REJECTED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.ACCEPTED, DeliveryOfferStatus.EXPIRED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.ACCEPTED, DeliveryOfferStatus.PENDING));
    }

    @Test
    @DisplayName("REJECTED is terminal and cannot transition to any other status")
    void testRejectedIsTerminal() {
        assertTrue(stateMachine.isTerminal(DeliveryOfferStatus.REJECTED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.REJECTED, DeliveryOfferStatus.ACCEPTED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.REJECTED, DeliveryOfferStatus.EXPIRED));
    }

    @Test
    @DisplayName("EXPIRED is terminal and cannot transition to any other status")
    void testExpiredIsTerminal() {
        assertTrue(stateMachine.isTerminal(DeliveryOfferStatus.EXPIRED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.EXPIRED, DeliveryOfferStatus.ACCEPTED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.EXPIRED, DeliveryOfferStatus.REJECTED));
    }

    @Test
    @DisplayName("Null handling returns false")
    void testNullHandling() {
        assertFalse(stateMachine.canTransition(null, DeliveryOfferStatus.ACCEPTED));
        assertFalse(stateMachine.canTransition(DeliveryOfferStatus.PENDING, null));
        assertFalse(stateMachine.isTerminal(null));
    }
}
