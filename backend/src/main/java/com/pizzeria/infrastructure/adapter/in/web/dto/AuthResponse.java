package com.pizzeria.infrastructure.adapter.in.web.dto;
/*Este de dto sirve para representar la respuesta de autenticación */
public record AuthResponse(
    String token,
    Long userId,
    String email,
    String role
) {}
