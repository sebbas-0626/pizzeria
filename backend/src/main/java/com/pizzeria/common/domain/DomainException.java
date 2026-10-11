package com.pizzeria.common.domain;

/**
 * Raíz de las excepciones que expresan un problema del negocio previsto.
 *
 * <p>Un fallo de negocio no es un error técnico: significa que alguien intentó hacer algo que las
 * reglas del dominio no permiten. La capa web la traduce a un código HTTP concreto y la capa de
 * aplicación decide si tiene sentido recuperarla.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
