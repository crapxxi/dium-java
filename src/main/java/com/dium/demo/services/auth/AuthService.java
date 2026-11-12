package com.dium.demo.services.auth;

import com.dium.demo.dto.requests.LoginRequest;
import com.dium.demo.dto.requests.RegisterRequest;
import com.dium.demo.dto.requests.VenueOwnerRequest;
import com.dium.demo.dto.responses.AuthResponse;
import com.dium.demo.dto.responses.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserResponse getMe();
    /** Назначает уже зарегистрированного пользователя владельцем заведения (только ADMIN). */
    UserResponse assignVenueOwner(VenueOwnerRequest request);
}
