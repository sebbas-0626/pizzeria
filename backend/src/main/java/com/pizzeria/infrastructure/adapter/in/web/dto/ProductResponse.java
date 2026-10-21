package com.pizzeria.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    UUID categoryId,
    String name,
    String description,
    BigDecimal price,
    String imageUrl,
    boolean available
) {}
