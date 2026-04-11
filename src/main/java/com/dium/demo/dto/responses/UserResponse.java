package com.dium.demo.dto.responses;

import com.dium.demo.enums.UserRole;

public record UserResponse(
        Long id,
        String phone,
        String name,
        UserRole role
) { }
