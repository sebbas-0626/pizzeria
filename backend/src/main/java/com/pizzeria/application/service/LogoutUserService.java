package com.pizzeria.application.service;

import com.pizzeria.domain.port.in.LogoutUserUseCase;
import com.pizzeria.domain.port.out.TokenRevocationPort;
import org.springframework.stereotype.Service;

@Service
public class LogoutUserService implements LogoutUserUseCase {

    private final TokenRevocationPort tokenRevocationPort;

    public LogoutUserService(TokenRevocationPort tokenRevocationPort) {
        this.tokenRevocationPort = tokenRevocationPort;
    }

    @Override
    public void logout(String token) {
        tokenRevocationPort.revoke(token);
    }
}