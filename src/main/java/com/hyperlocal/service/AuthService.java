package com.hyperlocal.service;

import com.hyperlocal.dto.LoginRequest;
import com.hyperlocal.dto.LoginResponse;
import com.hyperlocal.dto.RegisterRequest;
import com.hyperlocal.dto.UserResponse;
import com.hyperlocal.model.Role;
import com.hyperlocal.entity.User;
import com.hyperlocal.exception.UserAlreadyExistsException;
import com.hyperlocal.repository.ShopRepository;
import com.hyperlocal.repository.UserRepository;
import com.hyperlocal.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       ShopRepository shopRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email is already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.CUSTOMER);

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Long shopId = null;
        if (user.getRole() == Role.SHOP_OWNER) {
            shopId = shopRepository.findFirstByOwnerId(user.getId())
                    .map(shop -> shop.getId())
                    .orElse(null);
        }

        Map<String,Object> extraClaims = new HashMap<>();
        extraClaims.put("role",user.getRole().name());
        extraClaims.put("name",user.getName());
        if(shopId != null){
            extraClaims.put("shopId",shopId);
        }

        String jwtToken = jwtService.generateToken(extraClaims,user);

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                shopId
        );

        return new LoginResponse(jwtToken, userResponse);
    }
}
