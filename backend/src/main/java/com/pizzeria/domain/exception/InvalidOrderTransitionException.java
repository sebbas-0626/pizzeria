package com.pizzeria.domain.exception;

public class InvalidOrderTransitionException extends DomainException {
    public InvalidOrderTransitionException(String from, String to) {
        super(com.pizzeria.domain.exception.ErrorCode.ORDERS_INVALID_TRANSITION,
            "Transicion de estado no permitida: " + from + " -> " + to);
    }
}
