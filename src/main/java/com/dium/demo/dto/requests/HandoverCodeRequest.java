package com.dium.demo.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Код передачи заказа: код курьера (вводит заведение) или код клиента (вводит курьер). */
public record HandoverCodeRequest(
        @NotBlank(message = "Введите код")
        @Pattern(regexp = "\\s*\\d{4,8}\\s*", message = "Код состоит из 4–8 цифр")
        String code
) {
}
