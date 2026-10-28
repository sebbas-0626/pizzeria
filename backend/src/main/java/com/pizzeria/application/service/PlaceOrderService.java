package com.pizzeria.application.service;

import com.pizzeria.domain.exception.ProductNotFoundException;
import com.pizzeria.domain.exception.ProductNotAvailableException;
import com.pizzeria.domain.model.Order;
import com.pizzeria.domain.model.PaymentMethod;
import com.pizzeria.domain.model.Product;
import com.pizzeria.domain.model.RequestedOrderLine;
import com.pizzeria.domain.port.in.PlaceOrderUseCase;
import com.pizzeria.domain.port.out.OrderRepositoryPort;
import com.pizzeria.domain.port.out.ProductRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PlaceOrderService implements PlaceOrderUseCase {

    private final ProductRepositoryPort productRepository;
    private final OrderRepositoryPort orderRepository;

    public PlaceOrderService(ProductRepositoryPort productRepository, OrderRepositoryPort orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public Order placeOrder(Long userId, Long addressId, PaymentMethod paymentMethod, String notes,
                           List<RequestedOrderLine> items) {
        // Valida cada producto contra el catalogo y congela su precio.
        Map<Long, Product> productsById = new HashMap<>();
        for (RequestedOrderLine line : items) {
            Product product = productRepository.findById(line.productId())
                .orElseThrow(() -> new ProductNotFoundException(line.productId()));
            if (!product.available()) {
                throw new ProductNotAvailableException(line.productId());
            }
            productsById.put(product.id(), product);
        }

        // El dominio construye el pedido y calcula los importes.
        Order order = Order.create(userId, addressId, paymentMethod, notes, items, productsById);

        // Persiste cabecera, lineas e historial de estado.
        return orderRepository.save(order);
    }
}
