package com.dium.demo.dto.requests;

import com.dium.demo.enums.DeliveryIssueType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeliveryIssueRequest(
        @NotNull(message = "Выберите, что случилось")
        DeliveryIssueType type,
        @Size(max = 1000, message = "Описание не длиннее 1000 символов")
        String description
) {
}
