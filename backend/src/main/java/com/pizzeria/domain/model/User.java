package com.pizzeria.domain.model;

import java.time.Instant;

public record User(
    Long id,
    String email,
    String passwordHash,
    String firstName,
    String lastName,
    String phone,
    String role,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}