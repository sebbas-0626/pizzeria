package com.pizzeria.domain.exception;

public class ProductNotAvailableException extends DomainException {
    public ProductNotAvailableException(Long productId) {
        super(com.pizzeria.domain.exception.ErrorCode.CATALOG_PRODUCT_NOT_AVAILABLE,
            "Producto no disponible: " + productId);
    }
}
