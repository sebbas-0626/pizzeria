package com.pizzeria.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Fuente de tiempo de toda la aplicación.
 *
 * <p>Existe para que el tiempo sea inyectable y las pruebas sean deterministas. Con
 * {@code Instant.now()} dentro de una clase, una prueba que falla un 31 de diciembre a las 23:59:59
 * pasa y el 1 de enero falla. Con {@link Clock} se fija el reloj y el resultado es siempre el mismo.
 *
 * <p>Se declara el método {@code public} para que Spring lo sustituya en las pruebas con
 * {@code @MockitoBean} o con un bean de reloj fijo.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}