package com.pizzeria.domain.exception;

/**
 * Catálogo de códigos de error estables del contrato público.
 *
 * <p>Un código es parte del <strong>contrato</strong>, igual que una ruta o un método HTTP. Si cambia,
 * se rompe a quien integra contra la API. Por eso vive en un enumerado y no se escribe a mano en cada
 * excepción: un typo en un literal sería invisible en las pruebas y llegaría al cliente.
 *
 * <p>Formato: {@code MODULO_SITUACION} en mayúsculas, en inglés, sin el número HTTP. El estado lo decide
 * la capa web ({@code shared.exception.GlobalExceptionHandler}), no el dominio: el dominio no sabe qué es un
 * 404.
 *
 * <p>Reglas al añadir uno:
 * <ul>
 *   <li>Nunca reutilizar un código para un significado distinto.</li>
 *   <li>Nunca cambiar un código ya publicado. Si el significado cambia, se añade otro.</li>
 *   <li>Cada módulo añade sus códigos en la historia que los necesita.</li>
 * </ul>
 */
public enum ErrorCode {

    // ---- 400 · Petición inválida ---------------------------------------------------
    /** Falló la validación de formato del cuerpo o de los parámetros. */
    REQUEST_VALIDATION_FAILED,
    /** El cuerpo no se pudo leer: JSON mal formado, tipo inesperado. */
    MALFORMED_REQUEST,

    // ---- 404 · No existe ------------------------------------------------------------
    /**
     * La ruta solicitada no existe en la API. Genérico, para no mentir sobre qué faltaba:
     * un 404 de ruta y un 404 de recurso ausente son situaciones distintas.
     */
    RESOURCE_NOT_FOUND,
    /** El recurso no existe, o es de otro cliente. Nunca se distingue el caso ajeno del inexistente. */
    ORDERS_NOT_FOUND,
    CATALOG_PIZZA_NOT_FOUND,
    CATALOG_VARIANT_NOT_FOUND,
    IDENTITY_CUSTOMER_NOT_FOUND,
    IDENTITY_INVALID_CREDENTIALS,

    // ---- 409 · Conflicto con el estado actual ---------------------------------------
    /** Ya existe un recurso con esa clave única. */
    IDENTITY_EMAIL_ALREADY_REGISTERED,
    /** La transición de estado pedida no está permitida desde el estado actual. */
    ORDERS_INVALID_TRANSITION,
    /** El pedido ya tiene un cobro registrado. Lo garantiza {@code UNIQUE (order_id)}. */
    PAYMENTS_ALREADY_REGISTERED,

    // ---- 422 · Regla de negocio violada ----------------------------------------------
    /** Se intentó crear un pedido sin ninguna línea. */
    ORDERS_EMPTY,
    /** La variante no existe, está inactiva o no está disponible. */
    CATALOG_VARIANT_NOT_AVAILABLE,
    /** El importe recibido no coincide con el total del pedido. */
    PAYMENTS_AMOUNT_MISMATCH,

    // ---- 500 · Fallo interno --------------------------------------------------------
    /**
     * Fallo no previsto. Es el único código que puede aparecer en cualquier operación y su mensaje
     * nunca debe revelar detalles internos.
     */
    INTERNAL_ERROR
}