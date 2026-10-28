package com.pizzeria.application.service;

import com.pizzeria.domain.exception.ProductNotFoundException;
import com.pizzeria.domain.exception.ProductNotAvailableException;
import com.pizzeria.domain.model.Order;
import com.pizzeria.domain.model.PaymentMethod;
import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.model.RequestedOrderLine;
import com.pizzeria.domain.port.out.OrderRepositoryPort;
import com.pizzeria.domain.port.out.ProductRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderServiceTest {

    @Mock
    private ProductRepositoryPort productRepository;

    @Mock
    private OrderRepositoryPort orderRepository;

    @InjectMocks
    private PlaceOrderService service;

    @Test
    void creaYGuardaPedido() {
        Product product = new Product(1L, 1L, "Margarita", "desc", new BigDecimal("25000.00"), null, true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = service.placeOrder(10L, 5L, PaymentMethod.CASH, "nota",
            List.of(new RequestedOrderLine(1L, 2, null)));

        assertThat(order.total()).isEqualByComparingTo("50000.00");
    }

    @Test
    void productoNoEncontradoLanza() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null))))
            .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void productoNoDisponibleLanza() {
        Product product = new Product(1L, 1L, "Margarita", "desc", new BigDecimal("25000.00"), null, false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.placeOrder(10L, 5L, PaymentMethod.CASH, null,
            List.of(new RequestedOrderLine(1L, 1, null))))
            .isInstanceOf(ProductNotAvailableException.class);
    }
}
