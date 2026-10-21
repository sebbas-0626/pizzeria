package com.pizzeria.domain.model;

import java.math.BigDecimal;

public record Product(
    Long id,
    Long categoryId,
    String name,
    String description,
    BigDecimal price,
    String imageUrl,
    boolean available
) {
    // Regla de dominio: el precio nunca puede ser negativo
    public Product {
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be >= 0");
        }
    }
}
