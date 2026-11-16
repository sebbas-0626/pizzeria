package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.domain.model.User;
import com.pizzeria.domain.port.in.GetCustomerUseCase;
import com.pizzeria.infrastructure.adapter.in.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final GetCustomerUseCase getCustomerUseCase;

    /** Perfil del cliente autenticado. Nunca devuelve la contraseña (RF-ID-07, RF-ID-08). */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = getCustomerUseCase.get(userId);
        return new UserResponse(user.id(), user.email(), user.firstName(), user.lastName(), user.phone(), user.role(), user.createdAt());
    }
}
