package com.dium.demo.dto.responses;

import java.util.List;

public record ModifierGroupResponse(
        Long id,
        String name,
        Boolean required,
        Integer minChoices,
        Integer maxChoices,
        List<ModifierResponse> modifiers
) { }
