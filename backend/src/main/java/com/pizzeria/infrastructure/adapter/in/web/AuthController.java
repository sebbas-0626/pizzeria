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

    /** Prefijo obligatorio de la cabecera {@code Authorization}. */
    private static final String BEARER_PREFIX = "Bearer ";

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
        User user = registerUserUseCase.register(
            request.email(), request.password(), request.firstName(), request.lastName(), request.phone());
        // Ni el correo ni la contraseña pasan por el log: sólo el id que acaba de recibir el usuario.
        log.info("User {} registered", user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(
            user.id(), user.email(), user.firstName(), user.lastName(), user.phone(), user.role(), user.createdAt()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = loginUserUseCase.login(request.email(), request.password());
        String token = jwtService.generateToken(user.id(), user.email(), java.util.List.of(user.role()));
        // Sólo el id: ni correo ni contraseña, y el token va únicamente en la respuesta.
        log.info("User {} logged in", user.id());
        return ResponseEntity.ok(new AuthResponse(token, user.id(), user.email(), user.role()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length());
            logoutUserUseCase.logout(token);
            // La cabecera Authorization es un secreto y jamás se imprime. El id se lee del token ya
            // validado; si no se puede leer —la ruta es pública y acepta cualquier cabecera— se avisa
            // sin reproducir su contenido, para que el log sirva sin convertirse en una fuga.
            if (jwtService.isValid(token)) {
                log.info("User {} logged out", jwtService.extractUserId(token));
            } else {
                log.warn("Logout request with a token that cannot be read");
            }
        }
        return ResponseEntity.noContent().build();
    }
}
