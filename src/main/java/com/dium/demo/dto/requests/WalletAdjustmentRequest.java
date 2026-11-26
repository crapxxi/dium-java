package com.dium.demo.dto.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ручная правка баланса администратором: плюс — начислить, минус — списать. Причина сохраняется в журнале. */
public record WalletAdjustmentRequest(
        @Min(value = -100_000, message = "Не больше 100 000 баллов за раз")
        @Max(value = 100_000, message = "Не больше 100 000 баллов за раз")
        long amount,
        @NotBlank(message = "Укажите причину")
        @Size(max = 500, message = "Причина не длиннее 500 символов")
        String reason
) {
}
