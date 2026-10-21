package com.pizzeria.infrastructure.adapter.in.web.dto;

public record AuthResponse(
    String token,
    Long userId,
    String email,
    String role
) {}