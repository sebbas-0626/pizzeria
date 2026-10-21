package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.User;

/** esta es la interfaz que define el caso de uso
 * se llama LoginUserUseCase porque es un caso de uso
 * y se implementa en el adaptador de entrada*/
public interface LoginUserUseCase {
    User login(String email, String password);
}
