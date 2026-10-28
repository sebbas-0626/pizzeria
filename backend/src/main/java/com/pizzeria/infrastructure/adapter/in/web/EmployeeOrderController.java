package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.domain.model.Order;
import com.pizzeria.domain.model.OrderStatus;
import com.pizzeria.domain.port.out.OrderRepositoryPort;
import com.pizzeria.infrastructure.adapter.in.web.dto.OrderItemResponse;
import com.pizzeria.infrastructure.adapter.in.web.dto.OrderResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
/* este enpoind es para */
@RequestMapping("/api/v1/employee/orders")
public class EmployeeOrderController {

    private final OrderRepositoryPort orderRepository;

    public EmployeeOrderController(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public List<OrderResponse> listAll(@RequestParam(required = false) String status) {
        OrderStatus filtro = (status == null || status.isBlank()) ? null : OrderStatus.valueOf(status);
        return orderRepository.findAll(filtro).stream()
            .map(this::toResponse).toList();
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirm(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Order confirmed = order.confirm();
        Order saved = orderRepository.updateStatus(id, confirmed.status(), userId);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Order cancelled = order.cancelByEmployee();
        Order saved = orderRepository.updateStatus(id, cancelled.status(), userId);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/{id}/complete-pickup")
    public ResponseEntity<OrderResponse> completePickup(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Order delivered = order.completePickup();
        Order saved = orderRepository.updateStatus(id, delivered.status(), userId);
        return ResponseEntity.ok(toResponse(saved));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.items().stream()
            .map(item -> new OrderItemResponse(
                item.id(), item.productId(), item.quantity(), item.unitPrice(), item.subtotal(), item.notes()))
            .toList();
        return new OrderResponse(
            order.id(), order.userId(), order.addressId(), order.status().name(),
            order.paymentMethod().name(), order.subtotal(), order.deliveryFee(), order.total(),
            order.notes(), order.createdAt(), items);
    }
}

