package com.pizzeria.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de una dirección.
 *
 * <p>No lleva {@code userId}: el propietario sale del token, nunca del cuerpo. Un {@code userId} en la
 * petición se rechaza como campo desconocido, igual que un {@code role} en el registro.
 */
public record CreateAddressRequest(
    @NotBlank @Size(max = 50) String alias,
    @NotBlank @Size(max = 200) String street,
    @NotBlank @Size(max = 100) String city,
    @Size(max = 100) String neighborhood,
    @Size(max = 500) String reference
) {
}
