package com.dium.demo.dto.venue_product;

import java.math.BigDecimal;

public record VenueDTO(
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
