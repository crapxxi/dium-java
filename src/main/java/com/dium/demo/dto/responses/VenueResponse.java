package com.dium.demo.dto.responses;

import java.math.BigDecimal;

public record VenueResponse(
        Long id,
        String name,
        String description,
        String address,
        String imageUrl,
        Boolean canDeliver,
        String kaspiUrl,
        BigDecimal deliveryPrice,
        Boolean isWorking
) { }
