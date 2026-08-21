package com.bankflow.authservice.controller;

import com.bankflow.authservice.dto.LoginRequest;
import com.bankflow.authservice.dto.LoginResponse;
import com.bankflow.authservice.dto.RegisterRequest;
import com.bankflow.authservice.dto.UserResponse;
import com.bankflow.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<String> getCurrentUser(
            Authentication authentication) {

        return ResponseEntity.ok(
                "Authenticated as: "
                        + authentication.getName()
                        + " | Role: "
                        + authentication.getAuthorities()
        );
    }
}