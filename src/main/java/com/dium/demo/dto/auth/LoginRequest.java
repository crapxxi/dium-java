package com.dium.demo.dto.auth;

public record LoginRequest(
        String phone,
        String password
) { }
