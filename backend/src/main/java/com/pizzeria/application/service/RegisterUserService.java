package com.pizzeria.application.service;

import com.pizzeria.domain.exception.EmailAlreadyRegisteredException;
import com.pizzeria.domain.model.User;
import com.pizzeria.domain.port.in.RegisterUserUseCase;
import com.pizzeria.domain.port.out.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserService(UserRepositoryPort userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User register(String email, String password, String firstName, String lastName, String phone) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }
        Instant now = Instant.now();
        User user = new User(
            null,
            normalizedEmail,
            passwordEncoder.encode(password),
            firstName.trim(),
            lastName.trim(),
            phone,
            "CLIENTE",
            true,
            now,
            now
        );
        return userRepository.save(user);
    }
}