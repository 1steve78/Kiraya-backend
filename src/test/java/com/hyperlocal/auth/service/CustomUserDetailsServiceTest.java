package com.hyperlocal.auth.service;

import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.enums.UserStatus;
import com.hyperlocal.auth.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User activeUser;
    private User suspendedUser;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setEmail("active@example.com");
        activeUser.setStatus(UserStatus.ACTIVE);

        suspendedUser = new User();
        suspendedUser.setEmail("suspended@example.com");
        suspendedUser.setStatus(UserStatus.SUSPENDED);
    }

    @Test
    void testLoadUserByUsername_ActiveUser_Success() {
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        UserDetails details = userDetailsService.loadUserByUsername("active@example.com");

        assertNotNull(details);
        assertEquals("active@example.com", details.getUsername());
    }

    @Test
    void testLoadUserByUsername_SuspendedUser_ThrowsDisabledException() {
        when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(suspendedUser));

        DisabledException ex = assertThrows(DisabledException.class, () ->
                userDetailsService.loadUserByUsername("suspended@example.com"));

        assertTrue(ex.getMessage().contains("suspended"));
    }

    @Test
    void testLoadUserByUsername_NotFound_ThrowsUsernameNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("missing@example.com"));
    }
}
