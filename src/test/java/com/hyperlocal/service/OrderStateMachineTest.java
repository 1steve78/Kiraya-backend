package com.hyperlocal.service;

import com.hyperlocal.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    @Test
    void testValidTransitionsFromPending() {
        assertTrue(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED));
        assertTrue(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
        assertFalse(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.PREPARING));
        assertFalse(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.DELIVERED));
    }

    @Test
    void testValidTransitionsFromConfirmed() {
        assertTrue(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.PREPARING));
        assertTrue(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        assertFalse(stateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.DELIVERED));
    }

    @Test
    void testValidTransitionsFromPreparing() {
        assertTrue(stateMachine.canTransition(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP));
        assertFalse(stateMachine.canTransition(OrderStatus.PREPARING, OrderStatus.CANCELLED));
        assertFalse(stateMachine.canTransition(OrderStatus.PREPARING, OrderStatus.DELIVERED));
    }

    @Test
    void testValidTransitionsFromReadyForPickup() {
        assertTrue(stateMachine.canTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY));
        assertFalse(stateMachine.canTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERED));
    }

    @Test
    void testValidTransitionsFromOutForDelivery() {
        assertTrue(stateMachine.canTransition(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED));
        assertFalse(stateMachine.canTransition(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED));
    }

    @Test
    void testTerminalStates() {
        assertFalse(stateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED));
        assertFalse(stateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.PENDING));
        assertFalse(stateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.CONFIRMED));
    }

    @Test
    void testNullInputs() {
        assertFalse(stateMachine.canTransition(null, OrderStatus.CONFIRMED));
        assertFalse(stateMachine.canTransition(OrderStatus.PENDING, null));
        assertFalse(stateMachine.canTransition(null, null));
    }
}
