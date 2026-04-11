package com.dium.demo.dto.requests;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Phone cannot be empty")
        String phone,
        @NotBlank(message = "Password cannot be empty")
        String password
) { }
