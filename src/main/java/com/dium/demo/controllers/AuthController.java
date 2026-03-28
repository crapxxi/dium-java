package com.dium.demo.controllers;

import com.dium.demo.dto.auth.AuthResponse;
import com.dium.demo.dto.auth.LoginRequest;
import com.dium.demo.dto.auth.RegisterRequest;
import com.dium.demo.dto.auth.UserResponse;
import com.dium.demo.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authorization")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "register client")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/venue-register")
    @Operation(summary = "register venue")
    public ResponseEntity<UserResponse> venueRegister(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.venueRegister(request));
    }

    @PostMapping("/login")
    @Operation(summary = "login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "get about user")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.getMe(userDetails));
    }
}