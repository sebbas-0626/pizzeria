package com.pizzeria.infrastructure.adapter.out.security;

import com.pizzeria.domain.port.out.TokenRevocationPort;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryTokenRevocationAdapter implements TokenRevocationPort {
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();

    @Override
    public void revoke(String token) {
        revoked.add(token);
    }

    @Override
    public boolean isRevoked(String token) {
        return revoked.contains(token);
    }
}