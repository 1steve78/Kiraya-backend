package com.hyperlocal.notification.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.dispatch.entity.DeliveryPartner;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderEventPublisherTransactionTest {

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("Transaction commits -> WebSocket events are published after commit")
    void testEventsPublished_WhenTransactionCommits() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        User customer = new User();
        customer.setEmail("customer@test.com");
        User partnerUser = new User();
        partnerUser.setEmail("partner@test.com");

        DeliveryPartner partner = new DeliveryPartner();
        partner.setId(10L);
        partner.setUser(partnerUser);

        Order order = new Order();
        order.setId(100L);
        order.setStatus(OrderStatus.ASSIGNED);
        order.setCustomer(customer);
        order.setDeliveryPartner(partner);

        txTemplate.execute(status -> {
            orderEventPublisher.publishOrderAssigned(order);
            // Verify event is NOT sent immediately while transaction is still open
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
            return null;
        });

        // After successful transaction commit, event must be published!
        verify(messagingTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Transaction rolls back -> WebSocket events are NOT published")
    void testEventsNotPublished_WhenTransactionRollsBack() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        User customer = new User();
        customer.setEmail("customer@test.com");
        User partnerUser = new User();
        partnerUser.setEmail("partner@test.com");

        DeliveryPartner partner = new DeliveryPartner();
        partner.setId(10L);
        partner.setUser(partnerUser);

        Order order = new Order();
        order.setId(100L);
        order.setStatus(OrderStatus.ASSIGNED);
        order.setCustomer(customer);
        order.setDeliveryPartner(partner);

        try {
            txTemplate.execute(status -> {
                orderEventPublisher.publishOrderAssigned(order);
                // Simulate failure before commit (e.g. OptimisticLockException or business conflict)
                throw new RuntimeException("Simulated database failure during transaction");
            });
        } catch (RuntimeException ignored) {
            // Expected
        }

        // Transaction failed and rolled back -> WebSocket message must NEVER be sent!
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }
}
