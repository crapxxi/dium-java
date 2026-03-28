package com.dium.demo.dto.auth;


public record RegisterRequest(
        String phone,
        String name,
        String password
) { }
