package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.Product;

import java.util.List;
import java.util.UUID;

public interface ListProductsUseCase {

    /**
     * Lista productos del menú con filtros opcionales.
     * @param categoryId filtro opcional por categoría
     * @param onlyAvailable si true, solo productos disponibles
     */
    List<Product> listProducts(UUID categoryId, boolean onlyAvailable);
}
