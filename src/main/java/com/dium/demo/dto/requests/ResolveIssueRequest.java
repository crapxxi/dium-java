package com.dium.demo.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveIssueRequest(
        @NotBlank(message = "Опишите решение")
        @Size(max = 1000, message = "Решение не длиннее 1000 символов")
        String resolution
) {
}
