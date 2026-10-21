package com.pizzeria.domain.port.out;

/* token revocation port es para revocar tokens
 *  se usa para evitar que un token sea usado despues de ser invalidado
 *  por ejemplo cuando un usuario cierra sesion
 *  o cuando un token expira
  */
public interface TokenRevocationPort {
    void revoke(String token);
    boolean isRevoked(String token);
}
