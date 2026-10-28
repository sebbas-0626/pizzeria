package com.pizzeria.domain.model;

import com.pizzeria.domain.exception.InvalidOrderTransitionException;
import com.pizzeria.domain.exception.OrderEmptyException;
import com.pizzeria.domain.exception.ProductNotFoundException;
import com.pizzeria.domain.exception.ProductNotAvailableException;
import com.pizzeria.domain.port.out.ProductRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private Product producto(Long id, BigDecimal price, boolean available) {
        return new Product(id, 1L, "Pizza " + id, "desc", price, null, available);
    }

    @Test
    void creaPedidoCongelandoPrecioYCalculandoTotal() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("25000.00"), true));
        List<RequestedOrderLine> lineas = List.of(new RequestedOrderLine(1L, 2, "sin cebolla"));

        Order order = Order.create(10L, 5L, PaymentMethod.CASH, "nota", lineas, productos);

        assertThat(order.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).unitPrice()).isEqualByComparingTo("25000.00");
        assertThat(order.items().get(0).subtotal()).isEqualByComparingTo("50000.00");
        assertThat(order.total()).isEqualByComparingTo("50000.00");
    }

    @Test
    void fusionaLineasConLaMismaVariante() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("10000.00"), true));
        List<RequestedOrderLine> lineas = List.of(
            new RequestedOrderLine(1L, 1, null),
            new RequestedOrderLine(1L, 2, null)
        );

        Order order = Order.create(10L, 5L, PaymentMethod.CASH, null, lineas, productos);

        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void pedidoVacioLanzaOrderEmpty() {
        assertThatThrownBy(() -> Order.create(10L, 5L, PaymentMethod.CASH, null, List.of(),
            Map.of()))
            .isInstanceOf(OrderEmptyException.class);
    }

    @Test
    void productoNoEncontradoLanzaExcepcion() {
        assertThatThrownBy(() -> Order.create(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(99L, 1, null)), Map.of()))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void productoNoDisponibleLanzaExcepcion() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("10000.00"), false));
        assertThatThrownBy(() -> Order.create(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null)), productos))
            .isInstanceOf(ProductNotAvailableException.class);
    }

    @Test
    void confirmarDesdeReceivedPasaAInPreparation() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("10000.00"), true));
        Order order = Order.create(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null)), productos);

        Order confirmed = order.confirm();

        assertThat(confirmed.status()).isEqualTo(OrderStatus.IN_PREPARATION);
    }

    @Test
    void confirmarDesdeOtroEstadoLanzaInvalidTransition() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("10000.00"), true));
        Order order = Order.create(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null)), productos).confirm();

        assertThatThrownBy(order::confirm).isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void cancelarPorClienteSoloEnReceived() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("10000.00"), true));
        Order order = Order.create(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null)), productos);

        Order cancelled = order.cancelByCustomer();

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelarPorClienteEnOtroEstadoLanza() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("10000.00"), true));
        Order order = Order.create(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null)), productos).confirm();

        assertThatThrownBy(order::cancelByCustomer).isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void cancelarPorEmpleadoEnReceivedOInPreparation() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("10000.00"), true));
        Order received = Order.create(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null)), productos);
        Order inPrep = received.confirm();

        assertThat(received.cancelByEmployee().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(inPrep.cancelByEmployee().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void completePickupSoloEnInPreparation() {
        Map<Long, Product> productos = Map.of(1L, producto(1L, new BigDecimal("10000.00"), true));
        Order received = Order.create(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null)), productos);
        Order inPrep = received.confirm();

        assertThatThrownBy(received::completePickup).isInstanceOf(InvalidOrderTransitionException.class);
        assertThat(inPrep.completePickup().status()).isEqualTo(OrderStatus.DELIVERED);
    }
}
