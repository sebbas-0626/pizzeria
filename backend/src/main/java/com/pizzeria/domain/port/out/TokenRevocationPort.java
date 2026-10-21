package com.pizzeria.domain.port.out;

public interface TokenRevocationPort {
    void revoke(String token);
    boolean isRevoked(String token);
}