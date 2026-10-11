package com.pizzeria.common.domain;

/**
 * Raíz de las excepciones que expresan un problema del negocio previsto.
 *
 * <p>Un fallo de negocio no es un error técnico: significa que alguien intentó hacer algo que las
 * reglas del dominio no permiten. La capa web la traduce a un código HTTP concreto y la capa de
 * aplicación decide si tiene sentido recuperarla.
 *
 * <p>Cada excepción concreta lleva su {@link ErrorCode}. El <strong>mensaje</strong> es para logs y
 * para el usuario final en español; el <strong>código</strong> es para quien integra y nunca cambia.
 * Un cliente no debe parsear el mensaje para decidir qué hacer: debe comparar el código.
 *
 * <p>Por qué el código no se deduce de la clase. Si cada módulo declarase su propia excepción con su
 * propio nombre, la capa web necesitaría una rama por excepción y crecería sin fin. Con el código, la
 * traducción es una tabla y añadir un caso nuevo es añadir una fila.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode code;

    protected DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    protected DomainException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Código estable del contrato. No cambia nunca una vez publicado.
     */
    public ErrorCode code() {
        return code;
    }
}