package com.dium.demo.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Действие администратора над доставкой. Причина обязательна: она сохраняется в доставке. */
public record AdminDeliveryActionRequest(
        @NotBlank(message = "Укажите причину")
        @Size(max = 500, message = "Причина не длиннее 500 символов")
        String reason
) {
}
