package com.pizzeria.shared.exception;

import com.pizzeria.domain.exception.DomainException;
import com.pizzeria.domain.exception.ErrorCode;
import com.pizzeria.infrastructure.adapter.in.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Traduce cualquier excepción a un error HTTP con el mismo formato.
 *
 * <p>Formato único {@code application/problem+json} (RFC 9457) ampliado con tres campos que el cliente
 * necesita y el estándar no trae: {@code code} estable, {@code timestamp} y {@code traceId}.
 *
 * <p>Decisión de diseño: <strong>el estado HTTP lo decide esta tabla, no la excepción</strong>. El
 * dominio no sabe qué es un 404, así que {@link DomainException} transporta un {@link ErrorCode} y aquí
 * se traduce. Añadir un caso nuevo es añadir una fila, no una rama nueva en cada sitio.
 *
 * <p>Regla dura: este mensaje nunca contiene datos de otro usuario, contraseñas, tokens, cabeceras de
 * autorización ni SQL. El detalle de un 500 es siempre genérico, porque su texto puede contener
 * cualquier cosa.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TIPO_BASE = "https://pizzeria.local/errors/";

    /**
     * Traducción código → estado. Explícita y exhaustiva: si se añade un código al catálogo y se
     * olvida esta fila, el estado por defecto avisa en lugar de fallar en silencio.
     *
     * <p>Se construye con {@code ofEntries} y no con {@code of} porque {@code Map.of} admite como
     * mucho diez pares y el catálogo tiene trece. El límite está pensado para detectar a tiempo el
     * mapa gigante que debería ser otro tipo de estructura.
     */
    private static final Map<ErrorCode, HttpStatus> ESTADOS = Map.ofEntries(
            Map.entry(ErrorCode.REQUEST_VALIDATION_FAILED, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.MALFORMED_REQUEST, HttpStatus.BAD_REQUEST),

            Map.entry(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.ORDERS_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.CATALOG_PIZZA_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.CATALOG_VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.IDENTITY_CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND),

            Map.entry(ErrorCode.IDENTITY_EMAIL_ALREADY_REGISTERED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.ORDERS_INVALID_TRANSITION, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.PAYMENTS_ALREADY_REGISTERED, HttpStatus.CONFLICT),

            Map.entry(ErrorCode.ORDERS_EMPTY, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ErrorCode.CATALOG_VARIANT_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ErrorCode.PAYMENTS_AMOUNT_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY),

            Map.entry(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR));

    private final Clock reloj;

    public GlobalExceptionHandler(Clock reloj) {
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------------------------
    // Reglas de negocio
    // ---------------------------------------------------------------------------------

    /**
     * Error de negocio previsto: el dominio rechazó la operación.
     */
    @ExceptionHandler(DomainException.class)
    public ProblemDetail manejarErrorDeDominio(DomainException excepcion, HttpServletRequest peticion) {
        ErrorCode codigo = excepcion.code();
        HttpStatus estado = ESTADOS.getOrDefault(codigo, HttpStatus.UNPROCESSABLE_ENTITY);

        // Un 4xx es un fallo esperado: se registra en WARN, sin traza, porque la traza de un
        // rechazo de negocio no aporta nada y en volumen llena el disco.
        log.warn("Operacion rechazada: code={} uri={}", codigo, uriDe(peticion));

        return construir(estado, codigo, excepcion.getMessage(), List.of());
    }

    // ---------------------------------------------------------------------------------
    // Validación de la petición
    // ---------------------------------------------------------------------------------

    /**
     * Falla la validación del cuerpo: devuelve 400 con la lista campo/mensaje, que es lo que permite
     * al cliente señalar el input culpable en vez de recibir un "algo está mal".
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacionDelCuerpo(MethodArgumentNotValidException excepcion) {
        List<ErrorDeCampo> errores = excepcion.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDeCampo(error.getField(), error.getDefaultMessage()))
                .toList();

        return construir(HttpStatus.BAD_REQUEST, ErrorCode.REQUEST_VALIDATION_FAILED,
                "La peticion no supera la validacion", errores);
    }

    /**
     * Falla la validación de un parámetro de método o de la petición, no del cuerpo.
     */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ProblemDetail manejarValidacionDeParametros(Exception excepcion) {
        return construir(HttpStatus.BAD_REQUEST, ErrorCode.REQUEST_VALIDATION_FAILED,
                "La peticion no supera la validacion", List.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ProblemDetail manejarParametroInvalido(Exception excepcion) {
        return construir(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "El parametro indicado no es valido", List.of());
    }

    /**
     * El cuerpo no se pudo convertir. El mensaje del cliente puede traer el tipo que esperaba, así que
     * no se propaga: sería filtrar la configuración interna de la API.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarCuerpoIlegible(HttpMessageNotReadableException excepcion) {
        log.warn("Cuerpo ilegible: causa={}", excepcion.getMostSpecificCause().getClass().getSimpleName());
        return construir(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "El cuerpo de la peticion no es valido", List.of());
    }

    /**
     * Ruta inexistente.
     *
     * <p>Usa un código genérico y no uno de dominio: decir {@code CATALOG_PIZZA_NOT_FOUND} cuando lo
     * que falló fue la URL sería mentirle a quien integra, y le haría buscar un problema de catálogo
     * donde sólo hay una dirección mal escrita.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail manejarRutaInexistente(NoResourceFoundException excepcion) {
        return construir(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe", List.of());
    }

    // ---------------------------------------------------------------------------------
    // Cualquier otra cosa
    // ---------------------------------------------------------------------------------

    /**
     * Lo que no se ha previsto.
     *
     * <p>Se registra la excepción COMPLETA, porque aquí sí hace falta la traza para diagnosticar. Pero
     * la respuesta al cliente es genérica: un mensaje de error real puede contener nombres de tabla,
     * nombres de columna o fragmentos de una consulta.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarErrorInesperado(Exception excepcion, HttpServletRequest peticion) {
        log.error("Fallo no previsto en {}: {}", uriDe(peticion), excepcion.getMessage(), excepcion);

        return construir(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Se ha producido un error inesperado", List.of());
    }

    // ---------------------------------------------------------------------------------
    // Construcción de la respuesta
    // ---------------------------------------------------------------------------------

    private ProblemDetail construir(HttpStatus estado,
                                    ErrorCode codigo,
                                    String detalle,
                                    List<ErrorDeCampo> errores) {

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(TIPO_BASE + codigo.name()));
        problema.setTitle(estado.getReasonPhrase());

        problema.setProperty("code", codigo.name());
        problema.setProperty("timestamp", Instant.now(reloj).toString());
        problema.setProperty("traceId", TraceIdFilter.currentTraceId());

        if (!errores.isEmpty()) {
            problema.setProperty("errors", errores);
        }

        return problema;
    }

    private static String uriDe(HttpServletRequest peticion) {
        return peticion.getMethod() + " " + peticion.getRequestURI();
    }

    /**
     * Detalle de un campo que no superó la validación.
     *
     * @param campo   nombre del campo, en notación de la API (por ejemplo {@code items[0].quantity})
     * @param mensaje motivo, en español, apta para mostrarse
     */
    public record ErrorDeCampo(String campo, String mensaje) {
    }
}