package com.pizzeria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway aplica el esquema completo sobre una base vacia")
    void elEsquemaSeAplicaDesdeCero() {
        List<String> tablas = jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                order by table_name
                """, String.class);

        assertThat(tablas).containsExactlyInAnyOrder(
                "addresses",
                "categories",
                "flyway_schema_history",
                "ingredients",
                "order_items",
                "order_status_history",
                "orders",
                "product_ingredients",
                "products",
                "users");
    }

    @Test
    @DisplayName("Las migraciones quedan registradas como aplicadas correctamente")
    void lasMigracionesQuedanRegistradas() {
        List<String> historial = jdbc.queryForList("""
                select version || '|' || description || '|' || success
                from flyway_schema_history
                order by installed_rank
                """, String.class);

        assertThat(historial).containsExactly(
                "1|baseline schema|true");
    }
}
