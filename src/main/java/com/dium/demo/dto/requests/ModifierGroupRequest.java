package com.dium.demo.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ModifierGroupRequest(
        @NotBlank(message = "Group name is required")
        String name,
        @NotNull(message = "Required flag must be specified")
        Boolean required,
        @Min(value = 0, message = "Min choices cannot be negative")
        Integer minChoices,
        @Min(value = 1, message = "Max choices must be at least 1")
        Integer maxChoices,
        @NotEmpty(message = "Modifier group must have at least one modifier")
        @Valid
        List<ModifierRequest> modifiers
) { }
