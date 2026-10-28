package com.pizzeria.domain.exception;

import com.pizzeria.domain.exception.ErrorCode;

public class OrderEmptyException extends DomainException {
    public OrderEmptyException() {
        super(ErrorCode.ORDERS_EMPTY, "El pedido no puede estar vacio");
    }
}
