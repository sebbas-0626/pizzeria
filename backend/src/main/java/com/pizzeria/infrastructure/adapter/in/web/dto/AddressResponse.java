package com.pizzeria.infrastructure.adapter.in.web.dto;

import java.time.Instant;

public record AddressResponse(
    Long id,
    String alias,
    String street,
    String city,
    String neighborhood,
    String reference,
    Instant createdAt
) {
}
