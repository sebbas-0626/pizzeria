package com.pizzeria.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long id,
    Long productId,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal,
    String notes
) {
}
