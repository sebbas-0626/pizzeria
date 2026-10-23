# Autenticación y arquitectura hexagonal (ejemplo con auth)

Documento de apoyo para entender cómo se aplica la arquitectura hexagonal del proyecto (ADR-0004) al caso de uso de identidad (registro, login, logout). No es fuente de verdad de negocio; sirve de lectura.

## 1. Mapa de componentes por capa

### Dominio (`com.pizzeria.domain`) — no conoce a nadie

- Modelo: `domain.model.User` (record, sin anotaciones de framework).
- Puertos de entrada: `domain.port.in.RegisterUserUseCase`, `LoginUserUseCase`, `LogoutUserUseCase`.
- Puertos de salida: `domain.port.out.UserRepositoryPort`, `TokenRevocationPort`.
- Excepciones: `EmailAlreadyRegisteredException`, `InvalidCredentialsException`.

### Aplicación (`com.pizzeria.application`) — orquesta

- `application.service.RegisterUserService` (implementa `RegisterUserUseCase`).
- `application.service.LoginUserService`.
- `application.service.LogoutUserService`.
- Transacciones con `@Transactional` (regla ADR-0002).

### Infraestructura (`com.pizzeria.infrastructure`) — adapta

- Entrada web: `infrastructure.adapter.in.web.AuthController`, DTOs `RegisterRequest`, `LoginRequest`, `AuthResponse`, `UserResponse`.
- Persistencia: `infrastructure.adapter.out.persistence.UserJpaAdapter` (implementa `UserRepositoryPort`), `entity.UserJpaEntity`, `repository.UserJpaRepository`.
- Seguridad: `infrastructure.adapter.in.web.JwtAuthenticationFilter`, `infrastructure.config.SecurityConfig`, `infrastructure.adapter.out.security.InMemoryTokenRevocationAdapter`.
- Filtro de traza: `shared` (transversal).

### Transversal (`com.pizzeria.shared`)

- `shared.security.JwtService` (emisión/validación de JWT).
- `shared.exception.GlobalExceptionHandler` (traduce a `ProblemDetail`).

## 2. Flujo de registro

1. HTTP `POST /api/v1/auth/register` → `AuthController.register`.
2. El controlador traduce `RegisterRequest` → parámetros y llama a `RegisterUserUseCase.register`.
3. `RegisterUserService` (aplicación):
   - normaliza el email (`trim().toLowerCase()`),
   - verifica duplicado con `UserRepositoryPort.existsByEmail`,
   - codifica la contraseña con `PasswordEncoder` (bean de infra/config),
   - construye `User` del dominio con rol forzado `CLIENTE`,
   - guarda con `UserRepositoryPort.save`.
4. `UserJpaAdapter` mapea `User` → `UserJpaEntity`, persiste con `UserJpaRepository`, y devuelve `User`.
5. El controlador mapea a `UserResponse` y responde `201 Created`.

Reglas clave: el servidor ignora cualquier rol recibido (fuerza `CLIENTE`); el cliente nunca envía precio/importes (no aplica aquí, pero mismo principio: el servidor decide).

## 3. Flujo de login

1. HTTP `POST /api/v1/auth/login` → `AuthController.login`.
2. `LoginUserService.login`:
   - busca el usuario por email normalizado,
   - verifica `enabled` y contraseña con `PasswordEncoder.matches`,
   - si falla lanza `InvalidCredentialsException`.
3. Si es válido, `JwtService.generateToken` crea el JWT (HS256, expiración `jwt.expiration-ms`).
4. Respuesta `200 OK` con `AuthResponse(token, userId, email, role)`.

## 4. Flujo de logout

1. HTTP `POST /api/v1/auth/logout` con `Authorization: Bearer <token>`.
2. `AuthController.logout` extrae el token y llama a `LogoutUserUseCase.logout`.
3. `LogoutUserService` delega en `TokenRevocationPort.revoke`.
4. `InMemoryTokenRevocationAdapter` guarda el token en un `Set` en memoria.
5. A partir de ahí, `JwtAuthenticationFilter` rechaza ese token (`isRevoked`).

## 5. Filtro de autenticación (entrada)

`JwtAuthenticationFilter` (en `infrastructure.adapter.in.web`) se ejecuta antes del controlador:

- Lee `Authorization`,
- valida firma/expiración con `JwtService`,
- comprueba revocación con `TokenRevocationPort`,
- si todo ok, coloca un `UsernamePasswordAuthenticationToken` en el contexto de seguridad.

`SecurityConfig` declara las rutas públicas (`/api/v1/auth/**`, actuator, docs) y exige autenticación para el resto.

## 6. Mapeo dominio ↔ persistencia

- El dominio usa `User` (record, sin JPA).
- La infraestructura usa `UserJpaEntity` (`@Entity`, tabla `users`).
- `UserJpaAdapter` hace la conversión en ambos sentidos. Así el dominio queda libre de Jakarta/JPA (ADR-0003).

## 7. Cómo se verifica

- Tests de dominio: sin Spring.
- Tests de casos de uso: con dobles (Mockito).
- Tests de persistencia/API: Testcontainers.
- Reglas de dependencia: `ArchitectureRulesTest` (ArchUnit) en cada `verify`.

## 8. Puntos de extensión

- Sustituir `InMemoryTokenRevocationAdapter` por un adaptador persistente si se escala a varias instancias.
- Añadir roles/autorización fina en `SecurityConfig` y en `JwtAuthenticationFilter` (hoy sólo `ROLE_USER`).
