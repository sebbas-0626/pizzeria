package com.pizzeria.domain.model;

import java.time.Instant;

/**
 * Representa un usuario del sistema (cliente o empleado
 * ejemplo: esto se encarga de validar las credenciales).
 */
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
