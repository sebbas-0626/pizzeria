package com.pizzeria.infrastructure.adapter.in.web.dto;

import com.pizzeria.domain.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
    @NotNull Long addressId,
    PaymentMethod paymentMethod,
    String notes,
    @NotNull @Size(min = 1, max = 10) @Valid List<OrderItemRequest> items
) {
}
