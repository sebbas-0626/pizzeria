package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.domain.model.Order;
import com.pizzeria.domain.model.OrderItem;
import com.pizzeria.domain.model.PaymentMethod;
import com.pizzeria.domain.model.RequestedOrderLine;
import com.pizzeria.domain.port.in.PlaceOrderUseCase;
import com.pizzeria.domain.port.out.OrderRepositoryPort;
import com.pizzeria.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.pizzeria.infrastructure.adapter.in.web.dto.OrderItemResponse;
import com.pizzeria.infrastructure.adapter.in.web.dto.OrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final OrderRepositoryPort orderRepository;

    public OrderController(PlaceOrderUseCase placeOrderUseCase, OrderRepositoryPort orderRepository) {
        this.placeOrderUseCase = placeOrderUseCase;
        this.orderRepository = orderRepository;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        PaymentMethod paymentMethod = request.paymentMethod() != null
            ? request.paymentMethod()
            : PaymentMethod.CASH;

        List<RequestedOrderLine> lines = request.items().stream()
            .map(item -> new RequestedOrderLine(item.productId(), item.quantity(), item.notes()))
            .toList();

        Order order = placeOrderUseCase.placeOrder(
            userId, request.addressId(), paymentMethod, request.notes(), lines);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
    }

    @GetMapping
    public List<OrderResponse> listMine(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return orderRepository.findByUserId(userId).stream()
            .map(this::toResponse).toList();
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getMine(@PathVariable Long orderId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return orderRepository.findByIdAndUserId(orderId, userId)
            .map(order -> ResponseEntity.ok(toResponse(order)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelMine(@PathVariable Long orderId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElse(null);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        Order cancelled = order.cancelByCustomer();
        Order saved = orderRepository.updateStatus(orderId, cancelled.status(), userId);
        return ResponseEntity.ok(toResponse(saved));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.items().stream()
            .map(this::toItemResponse)
            .toList();
        return new OrderResponse(
            order.id(), order.userId(), order.addressId(), order.status().name(),
            order.paymentMethod().name(), order.subtotal(), order.deliveryFee(), order.total(),
            order.notes(), order.createdAt(), items);
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
            item.id(), item.productId(), item.quantity(), item.unitPrice(), item.subtotal(), item.notes());
    }
}
