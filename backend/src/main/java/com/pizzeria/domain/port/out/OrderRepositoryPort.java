package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.Order;
import com.pizzeria.domain.model.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepositoryPort {
    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findByUserId(Long userId);

    List<Order> findAll(OrderStatus status);

    Order updateStatus(Long orderId, OrderStatus newStatus, Long changedBy);
}
