package com.microapproval.api.controller;

import com.microapproval.api.dto.AuthResponse;
import com.microapproval.api.dto.LoginRequest;
import com.microapproval.api.dto.RegisterRequest;
import com.microapproval.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // API: POST http://localhost:8080/api/v1/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // API: POST http://localhost:8080/api/v1/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }
}
