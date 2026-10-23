package com.pizzeria.application.service;

import com.pizzeria.domain.exception.InvalidCredentialsException;
import com.pizzeria.domain.model.User;
import com.pizzeria.domain.port.in.LoginUserUseCase;
import com.pizzeria.domain.port.out.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
/* este servicio es el que se encarga de validar las credenciales del usuario
 * y devolver el usuario si las credenciales son válidas
 * si las credenciales no son válidas lanza una excepción
 * el servicio se llama LoginUserService y se implementa la interfaz LoginUserUseCase
 * se inyectan las dependencias necesarias para el funcionamiento del servicio
 * UserRepositoryPort para acceder a los usuarios y PasswordEncoder para codificar contraseñas
 */
public class LoginUserService implements LoginUserUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginUserService(UserRepositoryPort userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User login(String email, String password) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(InvalidCredentialsException::new);
        if (!user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
