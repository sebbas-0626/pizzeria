package com.pizzeria.domain.model;

import com.pizzeria.domain.exception.InvalidOrderTransitionException;
import com.pizzeria.domain.exception.OrderEmptyException;
import com.pizzeria.domain.exception.ProductNotFoundException;
import com.pizzeria.domain.exception.ProductNotAvailableException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pedido del dominio. El servidor calcula todos los importes.
 */
public record Order(
    Long id,
    Long userId,
    Long addressId,
    OrderStatus status,
    PaymentMethod paymentMethod,
    BigDecimal subtotal,
    BigDecimal deliveryFee,
    BigDecimal total,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    List<OrderItem> items
) {

    // Factory Method: construye el pedido congelando precios del catalogo.
    public static Order create(Long userId, Long addressId, PaymentMethod paymentMethod, String notes,
                              List<RequestedOrderLine> requestedLines, Map<Long, Product> productsById) {
        if (requestedLines == null || requestedLines.isEmpty()) {
            throw new OrderEmptyException();
        }

        Map<Long, Integer> quantityByProduct = new HashMap<>();
        Map<Long, String> notesByProduct = new HashMap<>();
        for (RequestedOrderLine line : requestedLines) {
            quantityByProduct.merge(line.productId(), line.quantity(), Integer::sum);
            notesByProduct.putIfAbsent(line.productId(), line.notes());
        }

        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();
            Product product = productsById.get(productId);
            if (product == null) {
                throw new ProductNotFoundException(productId);
            }
            if (!product.available()) {
                throw new ProductNotAvailableException(productId);
            }

            BigDecimal unitPrice = product.price();
            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(lineSubtotal);

            items.add(new OrderItem(null, null, productId, quantity, unitPrice, lineSubtotal, notesByProduct.get(productId)));
        }

        BigDecimal deliveryFee = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee);

        Instant now = Instant.now();
        return new Order(null, userId, addressId, OrderStatus.RECEIVED, paymentMethod,
            subtotal, deliveryFee, total, notes, now, now, items);
    }

    // Transiciones de estado (la maquina vive en el dominio).

    public Order confirm() {
        if (status != OrderStatus.RECEIVED) {
            throw new InvalidOrderTransitionException(status.name(), OrderStatus.IN_PREPARATION.name());
        }
        return copyWithStatus(OrderStatus.IN_PREPARATION);
    }

    public Order cancelByCustomer() {
        if (status != OrderStatus.RECEIVED) {
            throw new InvalidOrderTransitionException(status.name(), OrderStatus.CANCELLED.name());
        }
        return copyWithStatus(OrderStatus.CANCELLED);
    }

    public Order cancelByEmployee() {
        if (status != OrderStatus.RECEIVED && status != OrderStatus.IN_PREPARATION) {
            throw new InvalidOrderTransitionException(status.name(), OrderStatus.CANCELLED.name());
        }
        return copyWithStatus(OrderStatus.CANCELLED);
    }

    public Order completePickup() {
        if (status != OrderStatus.IN_PREPARATION) {
            throw new InvalidOrderTransitionException(status.name(), OrderStatus.DELIVERED.name());
        }
        return copyWithStatus(OrderStatus.DELIVERED);
    }

    private Order copyWithStatus(OrderStatus newStatus) {
        return new Order(id, userId, addressId, newStatus, paymentMethod, subtotal, deliveryFee, total,
            notes, createdAt, Instant.now(), items);
    }
}
