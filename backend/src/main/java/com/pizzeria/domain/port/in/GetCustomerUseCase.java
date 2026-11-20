package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.User;

public interface GetCustomerUseCase {
    /** Devuelve el perfil del cliente autenticado. Nunca incluye la contraseña (RF-ID-07, RF-ID-08). */
    User get(Long userId);
}
