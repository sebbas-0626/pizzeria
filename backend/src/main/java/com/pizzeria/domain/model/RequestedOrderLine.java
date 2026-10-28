package com.pizzeria.domain.model;

/**
 * Linea solicitada por el cliente al crear un pedido.
 */
public record RequestedOrderLine(Long productId, int quantity, String notes) {
}
