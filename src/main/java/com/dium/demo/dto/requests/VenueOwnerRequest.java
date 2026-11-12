package com.dium.demo.dto.requests;

import jakarta.validation.constraints.NotBlank;

/** Назначение уже зарегистрированного пользователя владельцем заведения. */
public record VenueOwnerRequest(
        @NotBlank(message = "Phone cannot be empty")
        String phone
) { }
