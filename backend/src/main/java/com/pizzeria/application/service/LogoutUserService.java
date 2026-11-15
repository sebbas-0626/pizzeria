package com.pizzeria.application.service;

import com.pizzeria.domain.port.in.LogoutUserUseCase;
import com.pizzeria.domain.port.out.TokenRevocationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutUserService implements LogoutUserUseCase {

    private final TokenRevocationPort tokenRevocationPort;

    @Override
    public void logout(String token) {
        tokenRevocationPort.revoke(token);
    }
}