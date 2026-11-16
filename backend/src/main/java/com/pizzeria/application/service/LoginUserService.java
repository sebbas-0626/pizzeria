package com.pizzeria.application.service;

import com.pizzeria.domain.exception.InvalidCredentialsException;
import com.pizzeria.domain.model.User;
import com.pizzeria.domain.port.in.LoginUserUseCase;
import com.pizzeria.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LoginUserService implements LoginUserUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    /* Este servicio permite a un usuario iniciar sesión en la aplicación,  */
    public User login(String email, String password) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
            .orElseThrow(InvalidCredentialsException::new);
        if (!user.enabled() || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }
}
