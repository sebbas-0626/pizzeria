package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.Address;

public interface CreateAddressUseCase {

    /**
     * Da de alta una dirección para el cliente indicado.
     *
     * <p>El cliente no lo decide: el identificador sale del token, nunca del cuerpo.
     */
    Address create(Long userId, String alias, String street, String city,
                   String neighborhood, String reference);
}
