package com.dium.demo.services.auth;

import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.responses.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserResponse getMe();
    UserResponse venueRegister(RegisterRequest request);
}
