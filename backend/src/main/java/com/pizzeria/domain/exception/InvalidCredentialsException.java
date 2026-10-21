package com.pizzeria.domain.exception;

public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super(ErrorCode.IDENTITY_INVALID_CREDENTIALS, "Credenciales inválidas");
    }
}