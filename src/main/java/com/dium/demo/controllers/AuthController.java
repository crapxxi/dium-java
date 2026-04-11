package com.dium.demo.controllers;

import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.responses.UserResponse;
import com.dium.demo.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/venue-register")
    public ResponseEntity<UserResponse> venueRegister(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.venueRegister(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'VENUE_OWNER', 'CLIENT')")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@Valid @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.getMe());
    }
}