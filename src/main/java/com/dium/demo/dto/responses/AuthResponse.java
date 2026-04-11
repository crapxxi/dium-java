package com.dium.demo.dto.responses;

import com.dium.demo.enums.UserRole;

public record AuthResponse(
        String token,
        String phone
) { }
