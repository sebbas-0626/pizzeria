package com.pizzeria.domain.port.in;

import com.pizzeria.domain.model.User;

public interface RegisterUserUseCase {
    User register(String email, String password, String firstName, String lastName, String phone);
}