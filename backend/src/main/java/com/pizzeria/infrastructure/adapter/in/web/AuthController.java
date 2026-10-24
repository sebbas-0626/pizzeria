package com.pizzeria.infrastructure.adapter.in.web;


import com.pizzeria.domain.model.User;
import com.pizzeria.domain.port.in.LoginUserUseCase;
import com.pizzeria.domain.port.in.LogoutUserUseCase;
import com.pizzeria.domain.port.in.RegisterUserUseCase;
import com.pizzeria.infrastructure.adapter.in.web.dto.AuthResponse;
import com.pizzeria.infrastructure.adapter.in.web.dto.LoginRequest;
import com.pizzeria.infrastructure.adapter.in.web.dto.RegisterRequest;
import com.pizzeria.infrastructure.adapter.in.web.dto.UserResponse;
import com.pizzeria.shared.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final LogoutUserUseCase logoutUserUseCase;
    private final JwtService jwtService;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                         LoginUserUseCase loginUserUseCase,
                         LogoutUserUseCase logoutUserUseCase,
                         JwtService jwtService) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.logoutUserUseCase = logoutUserUseCase;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registering user with email: {}", request.email());
        log.debug("Registering user: {}", request);
        User user = registerUserUseCase.register(
            request.email(), request.password(), request.firstName(), request.lastName(), request.phone());
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(
            user.id(), user.email(), user.firstName(), user.lastName(), user.phone(), user.role(), user.createdAt()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Logging in user with email: {}", request.email());
        log.debug("Logging in user: {}", request);
        User user = loginUserUseCase.login(request.email(), request.password());
        String token = jwtService.generateToken(user.id(), user.email(), java.util.List.of(user.role()));
        return ResponseEntity.ok(new AuthResponse(token, user.id(), user.email(), user.role()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        log.info("Logging out user: {}", authorization);
        log.debug("Logout request: {}", authorization);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            logoutUserUseCase.logout(authorization.substring(7));
        }
        return ResponseEntity.noContent().build();
    }
}
