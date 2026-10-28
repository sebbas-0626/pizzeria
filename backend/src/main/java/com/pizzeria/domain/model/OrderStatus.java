package com.pizzeria.domain.model;

/**
 * Estados posibles de un pedido a lo largo de su ciclo de vida.
 */
public enum OrderStatus {
    RECEIVED,
    IN_PREPARATION,
    READY,
    ON_THE_WAY,
    DELIVERED,
    CANCELLED
}
