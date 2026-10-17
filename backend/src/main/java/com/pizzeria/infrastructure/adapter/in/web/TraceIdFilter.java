package com.pizzeria.infrastructure.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Da a cada petición un identificador de correlación y lo propaga en tres sitios.
 *
 * <p>Sin esto, cuando un usuario reporta «me ha dado error», no hay forma de encontrar su petición
 * entre millones de líneas de log. Con esto, el identificador aparece en el log, en la cabecera de
 * respuesta y en el cuerpo del error, así que el usuario puede copiarla y pegarla.
 *
 * <p>Se reutiliza el identificador si el cliente lo envía (por ejemplo desde un gateway), y si no se
 * genera uno nuevo. Confiar en el del cliente sin más sería permitir inyectar cualquier texto en los
 * logs, así que se acepta sólo con forma de UUID.
 *
 * <p>Completado en US-043: logs en JSON estructurado y redacción de campos sensibles.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** Clave del identificador en el MDC, que es de donde lo lee el patrón de log. */
    public static final String MDC_KEY = "traceId";

    /** Cabecera de respuesta. El usuario puede copiarla y decir "falló esto". */
    public static final String HEADER = "X-Trace-Id";

    /** Cabecera de petición que se acepta si trae un UUID con el formato correcto. */
    public static final String REQUEST_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        String traceId = esUuid(peticion.getHeader(REQUEST_HEADER))
                ? peticion.getHeader(REQUEST_HEADER)
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, traceId);
        respuesta.setHeader(HEADER, traceId);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            // Sin esto el identificador se quedaría pegado al hilo del contenedor y la petición
            // siguiente heredaría el identificador de la anterior.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Identificador de la petición en curso, o vacío si no hay ninguno.
     *
     * <p>Se usa en la respuesta de error para que el cliente pueda correlacionar.
     */
    public static String currentTraceId() {
        return Optional.ofNullable(MDC.get(MDC_KEY)).orElse("");
    }

    private static boolean esUuid(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(valor);
            return true;
        } catch (IllegalArgumentException excepcion) {
            return false;
        }
    }
}