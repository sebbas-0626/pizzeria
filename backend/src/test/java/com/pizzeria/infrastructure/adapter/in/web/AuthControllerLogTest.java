package com.pizzeria.infrastructure.adapter.in.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pizzeria.domain.model.User;
import com.pizzeria.domain.port.in.LoginUserUseCase;
import com.pizzeria.domain.port.in.LogoutUserUseCase;
import com.pizzeria.domain.port.in.RegisterUserUseCase;
import com.pizzeria.infrastructure.adapter.in.web.dto.AuthResponse;
import com.pizzeria.infrastructure.adapter.in.web.dto.LoginRequest;
import com.pizzeria.infrastructure.adapter.in.web.dto.RegisterRequest;
import com.pizzeria.shared.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprueba que las tres rutas de autenticación no vuelcan secretos al log.
 *
 * <p><strong>Por qué un test de logs.</strong> El nivel {@code DEBUG} está encendido para
 * {@code com.pizzeria} en los perfiles {@code local} y {@code test}, así que todo lo que este
 * controlador pase a un {@code log.debug} se escribe de verdad en el laboratorio y en los tests de
 * integración. La regla de conventions.md §8 —ni contraseñas, ni tokens, ni cabeceras
 * {@code Authorization}, ni correos completos— sin una prueba que la vigile es una regla que se
 * rompe sin enterarse: aquí se rompería como fallo de build.
 *
 * <p>El capturador se instala con el logger a nivel {@code TRACE} a propósito. Con el nivel por
 * defecto, un volcado {@code debug} no llegaría nunca al capturador y el test aprobaría sin mirar
 * nada. También se comprueba que el hito de negocio sí se registra: la regla no es callarse, es
 * registrar el identificador en lugar del secreto.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLogTest {

    private static final String CORREO = "cliente.confidencial@pizzeria.co";
    private static final String CONTRASENA = "ClaveDePruebaSinValorReal2026";
    private static final String TOKEN = "token-que-representa-al-de-la-peticion";
    private static final String MARCA_HASH = "$2a$";
    private static final long ID_USUARIO = 77L;

    private static final User USUARIO = new User(
        ID_USUARIO, CORREO, "$2a$10$hashDeEjemplo", "Ana", "Perez", "3001112222",
        "CLIENTE", true, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

    @Mock
    private RegisterUserUseCase registro;

    @Mock
    private LoginUserUseCase acceso;

    @Mock
    private LogoutUserUseCase cierre;

    @Mock
    private JwtService jwt;

    private AuthController controlador;

    private Logger logger;
    private Level nivelOriginal;
    private ListAppender<ILoggingEvent> capturador;

    @BeforeEach
    void vigilaElLogDelControlador() {
        controlador = new AuthController(registro, acceso, cierre, jwt);
        logger = (Logger) LoggerFactory.getLogger(AuthController.class);
        nivelOriginal = logger.getLevel();
        logger.setLevel(Level.TRACE);
        capturador = new ListAppender<>();
        capturador.start();
        logger.addAppender(capturador);
    }

    @AfterEach
    void dejaElLogComoEstaba() {
        logger.detachAppender(capturador);
        capturador.stop();
        logger.setLevel(nivelOriginal);
    }

    @Test
    @DisplayName("El registro no escribe la contraseña ni el correo, pero sí el id del usuario")
    void registroSinContrasenaNiCorreo() {
        when(registro.register(any(), any(), any(), any(), any())).thenReturn(USUARIO);

        controlador.register(new RegisterRequest(CORREO, CONTRASENA, "Ana", "Perez", "3001112222"));

        assertThat(textoRegistrado())
            .doesNotContain(CONTRASENA)
            .doesNotContain(MARCA_HASH)
            .doesNotContain(CORREO)
            .contains(String.valueOf(ID_USUARIO));
    }

    @Test
    @DisplayName("El login entrega el token pero no lo escribe, ni la contraseña ni el correo")
    void loginSinContrasenaNiToken() {
        when(acceso.login(any(), any())).thenReturn(USUARIO);
        when(jwt.generateToken(any(), any(), any())).thenReturn(TOKEN);

        ResponseEntity<AuthResponse> respuesta = controlador.login(new LoginRequest(CORREO, CONTRASENA));

        // El token se entrega igual que siempre: lo que no puede ocurrir es que salga por el log.
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().token()).isEqualTo(TOKEN);
        assertThat(textoRegistrado())
            .doesNotContain(CONTRASENA)
            .doesNotContain(MARCA_HASH)
            .doesNotContain(CORREO)
            .doesNotContain(TOKEN)
            .contains(String.valueOf(ID_USUARIO));
    }

    @Test
    @DisplayName("El logout no imprime la cabecera Authorization ni el token, y sigue registrando al usuario")
    void logoutSinCabeceraNiToken() {
        when(jwt.isValid(TOKEN)).thenReturn(true);
        when(jwt.extractUserId(TOKEN)).thenReturn(ID_USUARIO);

        ResponseEntity<Void> respuesta = controlador.logout("Bearer " + TOKEN);

        verify(cierre).logout(TOKEN);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(textoRegistrado())
            .doesNotContain(TOKEN)
            .doesNotContain("Bearer")
            .contains(String.valueOf(ID_USUARIO));
    }

    @Test
    @DisplayName("Una cabecera ilegible se avisa sin reproducir su contenido y sin romper la petición")
    void logoutConCabeceraIlegible() {
        String tokenIlegible = "esto-no-es-un-jwt";

        ResponseEntity<Void> respuesta = controlador.logout("Bearer " + tokenIlegible);

        // La revocación no cambia: sigue ocurriendo para cualquier cabecera que llegue.
        verify(cierre).logout(tokenIlegible);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(textoRegistrado()).doesNotContain(tokenIlegible);
        assertThat(capturador.list)
            .anySatisfy(evento -> assertThat(evento.getLevel()).isEqualTo(Level.WARN));
    }

    /** Todo lo que el logger del controlador ha escrito, con sus argumentos ya sustituidos. */
    private String textoRegistrado() {
        return capturador.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
    }
}
