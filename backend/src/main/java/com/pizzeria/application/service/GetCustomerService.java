package com.pizzeria.application.service;

import com.pizzeria.domain.exception.InvalidCredentialsException;
import com.pizzeria.domain.model.User;
import com.pizzeria.domain.port.in.GetCustomerUseCase;
import com.pizzeria.domain.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetCustomerService implements GetCustomerUseCase {

    private final UserRepositoryPort userRepository;

    @Override
    public User get(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(InvalidCredentialsException::new);
    }
}
