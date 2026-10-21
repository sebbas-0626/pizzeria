package com.pizzeria.domain.exception;

public class EmailAlreadyRegisteredException extends DomainException {
    public EmailAlreadyRegisteredException(String email) {
        super(ErrorCode.IDENTITY_EMAIL_ALREADY_REGISTERED, "El email ya está registrado: " + email);
    }
}