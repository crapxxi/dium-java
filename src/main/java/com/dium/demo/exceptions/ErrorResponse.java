package com.dium.demo.exceptions;

public record ErrorResponse(
        int status,
        String message,
        long timestamp
) {}