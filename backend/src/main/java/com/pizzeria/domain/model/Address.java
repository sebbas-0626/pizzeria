package com.pizzeria.domain.model;

import java.time.Instant;

/**
 * Dirección del cliente.
 *
 * <p>La tabla existe porque el contrato de creación de pedido exige una {@code addressId}, y la regla
 * del servidor es no aceptar un identificador que no puede comprobar: una dirección que no existe o que
 * es de otro cliente tiene que rechazarse antes de tocar la base, no mediante un fallo de clave ajena.
 *
 * <p>La columna {@code is_default} no se modela a propósito: no hay ningún requisito que defina qué
 * significa que una dirección sea la predeterminada, y guardando un booleano que nada interpreta sería
 * inventar una regla de negocio. Se queda en el valor por defecto de la base y no sale por la API.
 */
public record Address(
    Long id,
    Long userId,
    String alias,
    String street,
    String city,
    String neighborhood,
    String reference,
    Instant createdAt
) {
}
