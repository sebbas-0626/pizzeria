package com.pizzeria.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

public record ProductResponse(
    Long id,
    Long categoryId,
    String name,
    String description,
    BigDecimal price,
    String imageUrl,
    boolean available
) {}
