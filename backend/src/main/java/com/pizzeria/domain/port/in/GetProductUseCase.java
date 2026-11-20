package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.Product;

public interface GetProductUseCase {
    /** Devuelve el detalle de un producto disponible. Lanza {@code ProductNotFoundException} si no existe o no está disponible. */
    Product get(Long productId);
}
