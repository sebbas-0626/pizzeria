package com.pizzeria.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long id,
    Long userId,
    Long addressId,
    String status,
    String paymentMethod,
    BigDecimal subtotal,
    BigDecimal deliveryFee,
    BigDecimal total,
    String notes,
    Instant createdAt,
    List<OrderItemResponse> items
) {
}
