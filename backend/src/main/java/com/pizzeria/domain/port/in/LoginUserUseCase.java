package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.User;

public interface LoginUserUseCase {
    User login(String email, String password);
}