package com.pizzeria.domain.port.in;

/** caso de uso de logout
 * se llama LogoutUserUseCase porque es un caso de uso
 * y se implementa en el adaptador de entrada */
public interface LogoutUserUseCase {
    void logout(String token);
}
