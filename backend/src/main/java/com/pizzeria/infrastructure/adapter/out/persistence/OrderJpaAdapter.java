package com.pizzeria.infrastructure.adapter.out.persistence;

import com.pizzeria.domain.model.Order;
import com.pizzeria.domain.model.PaymentMethod;
import com.pizzeria.domain.model.OrderItem;
import com.pizzeria.domain.model.OrderStatus;
import com.pizzeria.domain.port.out.OrderRepositoryPort;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.OrderItemJpaEntity;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.OrderStatusHistoryJpaEntity;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.OrderItemJpaRepository;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.OrderJpaRepository;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.OrderStatusHistoryJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class OrderJpaAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;
    private final OrderStatusHistoryJpaRepository historyJpaRepository;

    public OrderJpaAdapter(OrderJpaRepository orderJpaRepository,
                          OrderItemJpaRepository orderItemJpaRepository,
                          OrderStatusHistoryJpaRepository historyJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderItemJpaRepository = orderItemJpaRepository;
        this.historyJpaRepository = historyJpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = toEntity(order);
        OrderJpaEntity saved = orderJpaRepository.save(entity);

        List<OrderItem> savedItems = new ArrayList<>();
        for (OrderItem item : order.items()) {
            OrderItemJpaEntity itemEntity = toItemEntity(item, saved.getId());
            OrderItemJpaEntity savedItem = orderItemJpaRepository.save(itemEntity);
            savedItems.add(new OrderItem(
                savedItem.getId(), saved.getId(), item.productId(), item.quantity(),
                item.unitPrice(), item.subtotal(), item.notes()));
        }

        OrderStatusHistoryJpaEntity history = new OrderStatusHistoryJpaEntity();
        history.setOrderId(saved.getId());
        history.setPreviousStatus(null);
        history.setNewStatus(order.status().name());
        history.setChangedBy(order.userId());
        history.setCreatedAt(Instant.now());
        historyJpaRepository.save(history);

        return new Order(
            saved.getId(), order.userId(), order.addressId(), order.status(), order.paymentMethod(),
            order.subtotal(), order.deliveryFee(), order.total(), order.notes(),
            saved.getCreatedAt(), saved.getUpdatedAt(), savedItems);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Order> findByIdAndUserId(Long id, Long userId) {
        return orderJpaRepository.findByIdAndUserId(id, userId).map(this::toDomain);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderJpaRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toDomain).toList();
    }

    @Override
    public List<Order> findAll(OrderStatus status) {
        List<OrderJpaEntity> entities = (status == null)
            ? orderJpaRepository.findAllByOrderByCreatedAtDesc()
            : orderJpaRepository.findByStatusOrderByCreatedAtDesc(status.name());
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public Order updateStatus(Long orderId, OrderStatus newStatus, Long changedBy) {
        OrderJpaEntity entity = orderJpaRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        String previous = entity.getStatus();
        entity.setStatus(newStatus.name());
        entity.setUpdatedAt(Instant.now());
        OrderJpaEntity saved = orderJpaRepository.save(entity);

        OrderStatusHistoryJpaEntity history = new OrderStatusHistoryJpaEntity();
        history.setOrderId(saved.getId());
        history.setPreviousStatus(previous);
        history.setNewStatus(newStatus.name());
        history.setChangedBy(changedBy);
        history.setCreatedAt(Instant.now());
        historyJpaRepository.save(history);

        return toDomain(saved);
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = orderItemJpaRepository.findByOrderId(entity.getId()).stream()
            .map(e -> new OrderItem(e.getId(), e.getOrderId(), e.getProductId(), e.getQuantity(),
                e.getUnitPrice(), e.getSubtotal(), e.getNotes()))
            .toList();
        return new Order(
            entity.getId(), entity.getUserId(), entity.getAddressId(),
            OrderStatus.valueOf(entity.getStatus()), PaymentMethod.valueOf(entity.getPaymentMethod()),
            entity.getSubtotal(), entity.getDeliveryFee(), entity.getTotal(), entity.getNotes(),
            entity.getCreatedAt(), entity.getUpdatedAt(), items);
    }

    private OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setId(order.id());
        entity.setUserId(order.userId());
        entity.setAddressId(order.addressId());
        entity.setStatus(order.status().name());
        entity.setPaymentMethod(order.paymentMethod().name());
        entity.setSubtotal(order.subtotal());
        entity.setDeliveryFee(order.deliveryFee());
        entity.setTotal(order.total());
        entity.setNotes(order.notes());
        entity.setCreatedAt(order.createdAt());
        entity.setUpdatedAt(order.updatedAt());
        return entity;
    }

    private OrderItemJpaEntity toItemEntity(OrderItem item, Long orderId) {
        OrderItemJpaEntity entity = new OrderItemJpaEntity();
        entity.setId(item.id());
        entity.setOrderId(orderId);
        entity.setProductId(item.productId());
        entity.setQuantity(item.quantity());
        entity.setUnitPrice(item.unitPrice());
        entity.setSubtotal(item.subtotal());
        entity.setNotes(item.notes());
        return entity;
    }
}
