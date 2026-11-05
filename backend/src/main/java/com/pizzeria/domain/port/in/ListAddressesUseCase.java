package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.Address;

import java.util.List;

public interface ListAddressesUseCase {

    /** Lista las direcciones del cliente autenticado, y sólo las suyas. */
    List<Address> listMine(Long userId);
}
