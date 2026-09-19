package com.hyperlocal.auth.controller;

import com.hyperlocal.auth.dto.LoginRequest;
import com.hyperlocal.auth.dto.LoginResponse;
import com.hyperlocal.auth.dto.RegisterRequest;
import com.hyperlocal.auth.dto.UserResponse;
import com.hyperlocal.auth.entity.User;
import com.hyperlocal.auth.service.AuthService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private  final AuthService authService;

    public  AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User currentUser){
        UserResponse response = new UserResponse(
                currentUser.getId(),
                currentUser.getName(),
                currentUser.getEmail(),
                currentUser.getRole()
        );
        return ResponseEntity.ok(response);
    }

}
