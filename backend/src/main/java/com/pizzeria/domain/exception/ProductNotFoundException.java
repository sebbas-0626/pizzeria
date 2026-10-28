package com.pizzeria.domain.exception;

public class ProductNotFoundException extends DomainException {
    public ProductNotFoundException(Long productId) {
        super(com.pizzeria.domain.exception.ErrorCode.CATALOG_PIZZA_NOT_FOUND,
            "Producto no encontrado: " + productId);
    }
}
