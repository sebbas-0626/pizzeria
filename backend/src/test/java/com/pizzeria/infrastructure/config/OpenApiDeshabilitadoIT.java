package com.pizzeria.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que la documentación de la API **no** se publica fuera del entorno local.
 *
 * <p>Es el criterio de seguridad de US-005. Springdoc se activa por defecto en cuanto entra en el
 * classpath, así que la única forma de estar seguros es comprobar que el perfil por defecto —el que
 * se parece a producción— responde 404. Un test de arranque normal no lo detectaría: la aplicación
 * arrancaría igual, sana, con el mapa entero de la API publicado.
 *
 * <p>Ningún perfil se activa a propósito.
 *
 * <p>Se usa el {@link HttpClient} del JDK y no un cliente de test de Spring: una petición real sobre
 * un puerto real es justo lo que hay que comprobar, y no merece la pena añadir una dependencia para
 * ello.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDeshabilitadoIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    private final tools.jackson.databind.ObjectMapper json = new tools.jackson.databind.ObjectMapper();

    @Test
    @DisplayName("Sin perfil local, /v3/api-docs responde 404")
    void laDocumentacionNoSePublicaSinPerfilLocal() throws Exception {
        HttpResponse<String> respuesta = pedir("/v3/api-docs");

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.body()).doesNotContain("openapi", "paths", "info");
    }

    @Test
    @DisplayName("Sin perfil local, Swagger UI responde 404 y no sirve HTML")
    void swaggerUiNoSePublicaSinPerfilLocal() throws Exception {
        HttpResponse<String> respuesta = pedir("/swagger-ui/index.html");

        assertThat(respuesta.statusCode()).isEqualTo(404);

        // Lo que se comprueba es que no se sirve la INTERFAZ, no que la palabra no aparezca: el
        // cuerpo del error es un problem+json que incluye "instance":"/swagger-ui/index.html", así
        // que la palabra sale igual. Lo que no debe salir es la interfaz en sí.
        assertThat(respuesta.body())
                .doesNotContain("<!DOCTYPE html", "<html", "swagger-ui-bundle", "SwaggerUIBundle");
        assertThat(respuesta.headers().firstValue("content-type"))
                .contains("application/problem+json");
    }

    @Test
    @DisplayName("Sin perfil local, la salud sigue respondiendo: lo apagado es la doc, no la app")
    void laAplicacionSigueSanaSinPerfilLocal() throws Exception {
        // Si esto fallara, un 404 en /v3/api-docs podría deberse a que la aplicación no arrancó,
        // y la prueba del 404 pasaría por el motivo equivocado.
        assertThat(pedir("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Sin perfil local, actuator/env NO esta expuesto")
    void actuatorEnvNoEstaExpuesto() throws Exception {
        // Último criterio de US-080. /actuator/env puede filtrar secretos aunque no haya usuario
        // configurado, así que la lista de `management.endpoints.web.exposure.include` es
        // deliberadamente corta: health, info y metrics.
        HttpResponse<String> respuesta = pedir("/actuator/env");

        assertThat(respuesta.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Solo se exponen los endpoints de actuator permitidos")
    void soloSeExponenLosEndpointsPermitidos() throws Exception {
        HttpResponse<String> respuesta = pedir("/actuator");
        List<String> expuestos = json.readTree(respuesta.body())
                .path("_links").properties().stream()
                .map(entrada -> entrada.getKey())
                .toList();

        // No se comprueba una lista exacta: Spring Boot añade enlaces de hipermedia como
        // `health-path` o `metrics-requiredMetricName`, que no son endpoints. Lo que importa es
        // que estén los tres previstos y que no asome ninguno de los que filtran información.
        assertThat(expuestos).contains("self", "health", "info", "metrics");
        assertThat(expuestos)
                .as("estos endpoints pueden filtrar secretos aunque no haya usuario configurado")
                .doesNotContain("env", "configprops", "beans", "loggers", "heapdump", "threaddump",
                        "mappings", "scheduledtasks", "conditions");
    }

    private HttpResponse<String> pedir(String ruta) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}