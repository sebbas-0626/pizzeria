package com.pizzeria.domain.model;

import java.math.BigDecimal;

/**
 * Linea de un pedido con el precio congelado en el momento de la compra.
 */
public record OrderItem(
    Long id,
    Long orderId,
    Long productId,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal,
    String notes
) {
}
