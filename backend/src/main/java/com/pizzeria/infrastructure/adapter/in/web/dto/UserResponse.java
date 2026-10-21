package com.pizzeria.infrastructure.adapter.in.web.dto;

import java.time.Instant;

public record UserResponse(
    Long id,
    String email,
    String firstName,
    String lastName,
    String phone,
    String role,
    Instant createdAt
) {}