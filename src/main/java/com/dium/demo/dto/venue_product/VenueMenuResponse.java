package com.dium.demo.dto.venue_product;

import java.util.List;

public record VenueMenuResponse(
        String venueName,
        boolean canDeliver,
        String description,
        List<ProductDTO> products
) { }
