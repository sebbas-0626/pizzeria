package com.pizzeria.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que en el perfil local la documentación se publica y que el esquema {@code bearer} está
 * declarado.
 *
 * <p>Es la otra mitad de US-005: que la documentación exista es lo que permite probarla a mano desde
 * el navegador. El esquema de autenticación se verifica leyendo el JSON generado, no confiando en que
 * la configuración «parece correcta».
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class OpenApiHabilitadoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("Con perfil local, /v3/api-docs responde 200 con OpenAPI 3.1")
    void laDocumentacionSePublicaEnLocal() throws Exception {
        HttpResponse<String> respuesta = pedir("/v3/api-docs");

        assertThat(respuesta.statusCode()).isEqualTo(200);

        JsonNode contrato = json.readTree(respuesta.body());
        assertThat(contrato.path("openapi").asText()).startsWith("3.1");
        assertThat(contrato.path("info").path("title").asText()).isEqualTo("API de la pizzería");
    }

    @Test
    @DisplayName("Con perfil local, Swagger UI responde 200")
    void swaggerUiSePublicaEnLocal() throws Exception {
        assertThat(pedir("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("El esquema de autenticacion bearer esta declarado en el contrato")
    void elEsquemaBearerEstaDeclarado() throws Exception {
        JsonNode esquema = json.readTree(pedir("/v3/api-docs").body())
                .path("components").path("securitySchemes").path(OpenApiConfig.ESQUEMA_BEARER);

        // Sin esto el boton Authorize de Swagger no aparece, y documentar la autenticacion se
        // queda en prosa: quien integra no puede probar un endpoint protegido.
        assertThat(esquema.isMissingNode()).isFalse();
        assertThat(esquema.path("type").asText()).isEqualTo("http");
        assertThat(esquema.path("scheme").asText()).isEqualTo("bearer");
        assertThat(esquema.path("bearerFormat").asText()).isEqualTo("JWT");
    }

    @Test
    @DisplayName("El esquema no se aplica a todas las operaciones: hay endpoints publicos")
    void elBearerNoSeAplicaGlobalmente() throws Exception {
        JsonNode contrato = json.readTree(pedir("/v3/api-docs").body());

        // Un requisito de seguridad global marcaria como privados endpoints publicos como
        // GET /api/v1/pizzas, y quien integra acabaria mandando un token donde no hace falta.
        // El esquema se declara; cada operacion aplica el suyo en la historia que la crea.
        assertThat(contrato.has("security")).isFalse();
    }

    @Test
    @DisplayName("Los endpoints del catalogo aparecen en el contrato")
    void losEndpointsDelCatalogoAparecen() throws Exception {
        JsonNode rutas = json.readTree(pedir("/v3/api-docs").body()).path("paths");

        // Un `paths` vacío es el síntoma de que springdoc no ha encontrado ningún controlador, y no
        // falla: responde 200 con un contrato aparentemente válido. Por eso se comprueba que las rutas
        // están, y no sólo que el documento se publica.
        assertThat(rutas.has("/api/v1/products")).isTrue();
    }

    @Test
    @DisplayName("Las operaciones del catalogo no exigen token")
    void lasOperacionesDelCatalogoNoExigenToken() throws Exception {
        JsonNode rutas = json.readTree(pedir("/v3/api-docs").body()).path("paths");

        // El catálogo es público. Si una operación declarase un requisito de
        // seguridad, Swagger mostraría candado en una ruta que no lo necesita.
        assertThat(rutas.path("/api/v1/products").path("get").has("security")).isFalse();
    }

    private HttpResponse<String> pedir(String ruta) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}