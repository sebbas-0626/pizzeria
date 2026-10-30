package com.pizzeria.infrastructure.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre lo que motivó el endpoint: que {@code POST /orders} deje de devolver 500 cuando la dirección
 * no puede comprobarse, y que nadie peda con una dirección que no es suya.
 *
 * <p>El 500 original era una violación de clave ajena ({@code fk_orders_address}) porque el servidor
 * no validaba el {@code addressId} antes de insertar. Aquí se comprueba el estado y el {@code code},
 * que es lo que el cliente debe mirar.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AddressApiIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    private String tokenPropia;
    private String tokenAjena;

    private String baseUrl() { return "http://localhost:" + port + "/api/v1"; }

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("DELETE FROM order_status_history");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM addresses");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM users");

        jdbc.update("INSERT INTO categories (name, description) VALUES ('Cat', 'desc') ON CONFLICT DO NOTHING");
        Long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE name='Cat' LIMIT 1", Long.class);
        jdbc.update("INSERT INTO products (category_id, name, description, price, available) VALUES (?, 'Margarita', 'desc', ?, true)",
            categoryId, new BigDecimal("25000.00"));

        tokenPropia = registrarYAutenticar("lucia@pizzeria.co");
        tokenAjena = registrarYAutenticar("marcos@pizzeria.co");
    }

    private String registrarYAutenticar(String email) throws Exception {
        Map<String, Object> registro = Map.of(
            "email", email,
            "password", "password123",
            "firstName", "Nombre",
            "lastName", "Apellido"
        );
        post(baseUrl() + "/auth/register", json.writeValueAsString(registro), null);

        Map<String, Object> login = Map.of("email", email, "password", "password123");
        HttpResponse<String> respuesta = post(baseUrl() + "/auth/login", json.writeValueAsString(login), null);
        return json.readTree(respuesta.body()).get("token").asText();
    }

    private HttpResponse<String> post(String url, String body, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String url, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final String DIRECCION_ALTA =
        "{\"alias\":\"casa\",\"street\":\"calle 1\",\"city\":\"Bogota\",\"neighborhood\":\"Centro\"}";

    @Test
    @DisplayName("Alta y listado de direcciones: cada cliente sólo ve las suyas")
    void creaYListaDireccionesPropias() throws Exception {
        HttpResponse<String> alta = post(baseUrl() + "/addresses", DIRECCION_ALTA, tokenPropia);
        assertThat(alta.statusCode()).isEqualTo(201);
        JsonNode creada = json.readTree(alta.body());
        assertThat(creada.get("id").asLong()).isPositive();
        assertThat(creada.get("alias").asText()).isEqualTo("casa");
        assertThat(creada.has("userId")).as("el propietario no sale por la API").isFalse();

        post(baseUrl() + "/addresses",
            "{\"alias\":\"oficina\",\"street\":\"carrera 2\",\"city\":\"Bogota\"}", tokenAjena);

        HttpResponse<String> lista = get(baseUrl() + "/addresses", tokenPropia);
        assertThat(lista.statusCode()).isEqualTo(200);
        JsonNode propias = json.readTree(lista.body());
        assertThat(propias).hasSize(1);
        assertThat(propias.get(0).get("alias").asText()).isEqualTo("casa");
    }

    @Test
    @DisplayName("El pedido se crea con una dirección del propio cliente")
    void pedidoConDireccionPropiaSeCrea() throws Exception {
        long addressId = idDeDireccionCreada(tokenPropia);

        HttpResponse<String> create = post(baseUrl() + "/orders",
            pedidoCon(addressId, idDelProducto()), tokenPropia);

        assertThat(create.statusCode()).isEqualTo(201);
        assertThat(json.readTree(create.body()).get("addressId").asLong()).isEqualTo(addressId);
    }

    @Test
    @DisplayName("Una dirección de otro cliente devuelve 404, no 500")
    void pedidoConDireccionAjenaDevuelve404() throws Exception {
        long addressIdAjena = idDeDireccionCreada(tokenAjena);

        HttpResponse<String> create = post(baseUrl() + "/orders",
            pedidoCon(addressIdAjena, idDelProducto()), tokenPropia);

        assertThat(create.statusCode())
            .as("una clave ajena no puede salir como error interno: %s", create.body())
            .isEqualTo(404);
        JsonNode problema = json.readTree(create.body());
        assertThat(problema.get("code").asText()).isEqualTo("IDENTITY_ADDRESS_NOT_FOUND");
        assertThat(problema.get("traceId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Una dirección que no existe devuelve 404 con el mismo código que una ajena")
    void pedidoConDireccionInexistenteDevuelve404() throws Exception {
        HttpResponse<String> create = post(baseUrl() + "/orders",
            pedidoCon(999_999L, idDelProducto()), tokenPropia);

        assertThat(create.statusCode()).isEqualTo(404);
        assertThat(json.readTree(create.body()).get("code").asText())
            .isEqualTo("IDENTITY_ADDRESS_NOT_FOUND");
    }

    @Test
    @DisplayName("El alta de dirección exige alias, calle y ciudad")
    void altaSinCamposObligatoriosDevuelve400() throws Exception {
        HttpResponse<String> alta = post(baseUrl() + "/addresses",
            "{\"street\":\"calle 1\"}", tokenPropia);

        assertThat(alta.statusCode()).isEqualTo(400);
        JsonNode problema = json.readTree(alta.body());
        assertThat(problema.get("code").asText()).isEqualTo("REQUEST_VALIDATION_FAILED");
        assertThat(problema.get("errors").toString()).contains("alias", "city");
    }

    @Test
    @DisplayName("El propietario no se puede mandar en el cuerpo: es un campo desconocido")
    void altaConPropietarioEnElCuerpoDevuelve400() throws Exception {
        HttpResponse<String> alta = post(baseUrl() + "/addresses",
            "{\"alias\":\"casa\",\"street\":\"calle 1\",\"city\":\"Bogota\",\"userId\":999}", tokenPropia);

        assertThat(alta.statusCode()).isEqualTo(400);
        assertThat(json.readTree(alta.body()).get("code").asText()).isEqualTo("MALFORMED_REQUEST");
    }

    private long idDeDireccionCreada(String token) throws Exception {
        HttpResponse<String> alta = post(baseUrl() + "/addresses", DIRECCION_ALTA, token);
        assertThat(alta.statusCode()).isEqualTo(201);
        return json.readTree(alta.body()).get("id").asLong();
    }

    /**
     * El identificador no se da por supuesto: la secuencia de identidades sigue entre pruebas dentro
     * del mismo contenedor, así que el producto de esta ejecución puede no ser el 1.
     */
    private long idDelProducto() {
        return jdbc.queryForObject("SELECT id FROM products WHERE name='Margarita' LIMIT 1", Long.class);
    }

    private static String pedidoCon(long addressId, long productId) {
        return String.format(
            "{\"addressId\":%d,\"paymentMethod\":\"CASH\",\"items\":[{\"productId\":%d,\"quantity\":2}]}",
            addressId, productId);
    }
}
