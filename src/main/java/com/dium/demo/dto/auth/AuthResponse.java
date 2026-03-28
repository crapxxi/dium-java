package com.dium.demo.dto.auth;

import com.dium.demo.enums.UserRole;

public record AuthResponse(
        String token,
        String phone
) { }
