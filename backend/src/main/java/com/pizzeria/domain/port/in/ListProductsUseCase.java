package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.Product;

import java.util.List;

public interface ListProductsUseCase {

    /**
     * Lista productos del menú con filtros opcionales.
     * @param categoryId filtro opcional por categoría
     * @param onlyAvailable si true, solo productos disponibles
     */
    List<Product> listProducts(Long categoryId, boolean onlyAvailable);
}
