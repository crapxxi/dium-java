package com.dium.demo.dto.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VenueRequest(
        @NotBlank(message = "Venue name is required")
        String name,
        String description,
        @NotBlank(message = "Address is required")
        String address,
        String imageUrl,
        @NotNull(message = "Delivery capability must be specified")
        Boolean canDeliver,
        BigDecimal deliveryPrice
) { }
