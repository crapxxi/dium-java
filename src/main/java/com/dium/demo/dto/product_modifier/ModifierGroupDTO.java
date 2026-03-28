package com.dium.demo.dto.product_modifier;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ModifierGroupDTO(
        Long id,
        String name,
        @JsonProperty("required")
        Boolean required,
        Integer minChoices,
        Integer maxChoices,
        List<ModifierDTO> modifiers
) { }
