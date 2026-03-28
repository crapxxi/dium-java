package com.dium.demo.dto.auth;

import com.dium.demo.enums.UserRole;

public record UserResponse(
        String phone,
        String name,
        UserRole role
) { }
