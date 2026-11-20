package com.pizzeria.domain.model;

/**
 * Categoría del catálogo (ej: Pizzas, Bebidas, Postres).
 * La baja es lógica: {@code active = false} oculta la categoría y sus productos del catálogo público.
 */
public record Category(
    Long id,
    String name,
    String description,
    boolean active
) {}
