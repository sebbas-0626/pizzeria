package com.pizzeria.infrastructure.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderApiIT {

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

    private String baseUrl() { return "http://localhost:" + port + "/api/v1"; }

    private String token;

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

        Map<String, Object> reg = Map.of(
            "email", "lucia@pizzeria.co",
            "password", "password123",
            "firstName", "Lucia",
            "lastName", "Perez"
        );
        post(baseUrl() + "/auth/register", json.writeValueAsString(reg), null);

        Map<String, Object> login = Map.of("email", "lucia@pizzeria.co", "password", "password123");
        HttpResponse<String> loginResp = post(baseUrl() + "/auth/login", json.writeValueAsString(login), null);
        JsonNode node = json.readTree(loginResp.body());
        token = node.get("token").asText();

        Long userId = node.get("userId").asLong();
        jdbc.update("INSERT INTO addresses (user_id, alias, street, city) VALUES (?, 'casa', 'calle 1', 'Bogota')", userId);
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

    @Test
    void creaListaYCancelaPedido() throws Exception {
        Long productId = jdbc.queryForObject("SELECT id FROM products WHERE name='Margarita' LIMIT 1", Long.class);
        Long addressId = jdbc.queryForObject("SELECT id FROM addresses LIMIT 1", Long.class);

        String body = String.format("{\"addressId\":%d,\"paymentMethod\":\"CASH\",\"notes\":\"nota\",\"items\":[{\"productId\":%d,\"quantity\":2,\"notes\":\"x\"}]}",
            addressId, productId);

        HttpResponse<String> create = post(baseUrl() + "/orders", body, token);
        assertThat(create.statusCode()).isEqualTo(201);
        JsonNode created = json.readTree(create.body());
        long orderId = created.get("id").asLong();
        assertThat(created.get("total").asInt()).isEqualTo(50000);

        HttpResponse<String> list = get(baseUrl() + "/orders", token);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(json.readTree(list.body())).hasSize(1);

        HttpResponse<String> detail = get(baseUrl() + "/orders/" + orderId, token);
        assertThat(detail.statusCode()).isEqualTo(200);

        HttpResponse<String> cancel = post(baseUrl() + "/orders/" + orderId + "/cancel", "{\"reason\":\"no\"}", token);
        assertThat(cancel.statusCode()).isEqualTo(200);
        assertThat(json.readTree(cancel.body()).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void empleadoListaYConfirma() throws Exception {
        jdbc.update("UPDATE users SET role='ADMIN' WHERE email='lucia@pizzeria.co'");

        Map<String, Object> login = Map.of("email", "lucia@pizzeria.co", "password", "password123");
        HttpResponse<String> loginResp = post(baseUrl() + "/auth/login", json.writeValueAsString(login), null);
        String adminToken = json.readTree(loginResp.body()).get("token").asText();

        Long productId = jdbc.queryForObject("SELECT id FROM products WHERE name='Margarita' LIMIT 1", Long.class);
        Long addressId = jdbc.queryForObject("SELECT id FROM addresses LIMIT 1", Long.class);
        String body = String.format("{\"addressId\":%d,\"items\":[{\"productId\":%d,\"quantity\":1}]}",
            addressId, productId);

        HttpResponse<String> create = post(baseUrl() + "/orders", body, adminToken);
        long orderId = json.readTree(create.body()).get("id").asLong();

        HttpResponse<String> list = get(baseUrl() + "/employee/orders", adminToken);
        assertThat(list.statusCode()).isEqualTo(200);

        HttpResponse<String> confirm = post(baseUrl() + "/employee/orders/" + orderId + "/confirm", "{}", adminToken);
        assertThat(confirm.statusCode()).isEqualTo(200);
        assertThat(json.readTree(confirm.body()).get("status").asText()).isEqualTo("IN_PREPARATION");

        HttpResponse<String> pickup = post(baseUrl() + "/employee/orders/" + orderId + "/complete-pickup", "{}", adminToken);
        assertThat(pickup.statusCode()).isEqualTo(200);
        assertThat(json.readTree(pickup.body()).get("status").asText()).isEqualTo("DELIVERED");
    }
}


