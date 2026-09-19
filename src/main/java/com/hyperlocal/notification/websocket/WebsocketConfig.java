package com.hyperlocal.notification.websocket;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.security.JwtService;
import com.hyperlocal.order.entity.Order;
import com.hyperlocal.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebsocketConfig.class);
    private static final Pattern DELIVERY_LOCATION_TOPIC = Pattern.compile("^/topic/delivery/(\\d+)/location$");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final OrderRepository orderRepository;

    // Use @Lazy for OrderRepository to avoid circular dependency issues if any
    public WebsocketConfig(JwtService jwtService, UserDetailsService userDetailsService, @Lazy OrderRepository orderRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.orderRepository = orderRepository;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry){
        registry.enableSimpleBroker("/topic","/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration){
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        String username = jwtService.extractUsername(token);

                        if (username != null) {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            if (jwtService.isTokenValid(token, userDetails)) {
                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                                accessor.setUser(authentication);
                            }
                        }
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null) {
                        Matcher matcher = DELIVERY_LOCATION_TOPIC.matcher(destination);
                        if (matcher.matches()) {
                            Long orderId = Long.parseLong(matcher.group(1));
                            Authentication auth = (Authentication) accessor.getUser();
                            
                            if (auth == null || !(auth.getPrincipal() instanceof User user)) {
                                log.warn("Unauthenticated attempt to subscribe to order location: {}", orderId);
                                throw new IllegalArgumentException("Access Denied");
                            }
                            
                            Optional<Order> orderOpt = orderRepository.findById(orderId);
                            if (orderOpt.isEmpty()) {
                                log.warn("Subscribe attempt for non-existent order: {}", orderId);
                                throw new IllegalArgumentException("Access Denied");
                            }
                            
                            Order order = orderOpt.get();
                            if (!order.getCustomer().getId().equals(user.getId())) {
                                log.warn("User {} attempted to subscribe to order {} belonging to another customer", user.getId(), orderId);
                                throw new IllegalArgumentException("Access Denied");
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}
