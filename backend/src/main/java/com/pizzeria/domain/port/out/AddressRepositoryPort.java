package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.Address;

import java.util.List;
import java.util.Optional;

public interface AddressRepositoryPort {

    Address save(Address address);

    List<Address> findByUserId(Long userId);

    /**
     * Busca una dirección de un cliente concreto.
     *
     * <p>El identificador del cliente forma parte de la consulta, no se compara después: es la misma
     * propiedad que se verifica en los pedidos, y devuelve {@code empty} tanto si la dirección no existe
     * como si es de otro cliente, para que la respuesta no distinga uno de otro caso.
     */
    Optional<Address> findByIdAndUserId(Long id, Long userId);
}
