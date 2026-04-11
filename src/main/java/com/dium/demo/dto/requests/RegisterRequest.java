package com.dium.demo.dto.requests;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Phone cannot be empty")
        String phone,
        @NotBlank(message = "Name cannot be empty")
        @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
        String name,
        @NotBlank(message = "Password cannot be empty")
        @Size(min = 6, message = "Password must be at least 8 characters long")
        String password
) { }
