package com.pizzeria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Comprueba que el contexto de Spring arranca completo contra un PostgreSQL real.
 *
 * <p>Es una prueba de integración ({@code *IT}) y la ejecuta Failsafe en la fase verify,
 * no Surefire: necesita Docker y tarda.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PizzeriaApplicationIT {

    @Container
    @ServiceConnection
    // Sin diamante: en Testcontainers 2.0 la clase no es genérica por sí misma,
    // se especializa mediante JdbcDatabaseContainer<PostgreSQLContainer>.
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    @DisplayName("El contexto de la aplicación carga sin errores")
    void elContextoCarga() {
        // Si el contexto no arrancara, Spring fallaría antes de llegar a este método.
    }
}
