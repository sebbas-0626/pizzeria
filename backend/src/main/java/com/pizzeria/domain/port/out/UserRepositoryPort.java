package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.User;

import java.util.Optional;

// esta es una interfaz de puerto de salida
// indica que la implementacion de este puerto queda fuera del dominio
public interface UserRepositoryPort {
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    User save(User user);
    boolean existsByEmail(String email);
}
