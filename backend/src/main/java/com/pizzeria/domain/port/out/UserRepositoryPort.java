package com.pizzeria.domain.port.out;

import com.pizzeria.domain.model.User;

import java.util.Optional;
 /* userRepository port es para acceder a los datos del usuario
  *  se usa para buscar, guardar y verificar la existencia de usuarios
  */
public interface UserRepositoryPort {
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    User save(User user); //save metodo para guardar un usuario o actualizarlo
    boolean existsByEmail(String email);
}
