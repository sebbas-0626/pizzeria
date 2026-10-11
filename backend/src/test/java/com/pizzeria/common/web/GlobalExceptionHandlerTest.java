package com.pizzeria.common.web;

import com.pizzeria.common.config.ClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Comprueba la traducción de cada tipo de excepción a su respuesta HTTP.
 *
 * <p>Un test por tipo de excepción mapeada: es el criterio de aceptación de US-004 y la razón de que
 * estas pruebas sean muchas y pequeñas. Un manejador de errores se rompe en silencio —sigue
 * devolviendo 200, o devuelve 500 con la traza dentro— y sólo se nota cuando ya está en producción.
 */
@WebMvcTest({GlobalExceptionHandler.class, ControladorDePruebas.class})
@Import(ClockConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("Un error de negocio sale como problem+json con su codigo estable")
    void unErrorDeNegocioSaleConSuCodigo() throws Exception {
        JsonNode cuerpo = invocar("/api/v1/_pruebas/dominio?codigo=ORDERS_EMPTY", 422);

        assertThat(cuerpo.path("code").asText()).isEqualTo("ORDERS_EMPTY");
        assertThat(cuerpo.path("status").asInt()).isEqualTo(422);
        assertThat(cuerpo.path("detail").asText()).isEqualTo("Regla de negocio incumplida");
        assertThat(cuerpo.path("type").asText()).endsWith("/ORDERS_EMPTY");
    }

    @Test
    @DisplayName("Cada codigo del catalogo se traduce al estado HTTP que le corresponde")
    void cadaCodigoSeTraduceASuEstado() throws Exception {
        assertThat(estadoDe("ORDERS_NOT_FOUND")).isEqualTo(404);
        assertThat(estadoDe("CATALOG_PIZZA_NOT_FOUND")).isEqualTo(404);
        assertThat(estadoDe("CATALOG_VARIANT_NOT_FOUND")).isEqualTo(404);
        assertThat(estadoDe("IDENTITY_CUSTOMER_NOT_FOUND")).isEqualTo(404);

        assertThat(estadoDe("IDENTITY_EMAIL_ALREADY_REGISTERED")).isEqualTo(409);
        assertThat(estadoDe("ORDERS_INVALID_TRANSITION")).isEqualTo(409);
        assertThat(estadoDe("PAYMENTS_ALREADY_REGISTERED")).isEqualTo(409);

        assertThat(estadoDe("ORDERS_EMPTY")).isEqualTo(422);
        assertThat(estadoDe("CATALOG_VARIANT_NOT_AVAILABLE")).isEqualTo(422);
        assertThat(estadoDe("PAYMENTS_AMOUNT_MISMATCH")).isEqualTo(422);
    }

    @Test
    @DisplayName("La validacion del cuerpo devuelve 400 con la lista campo y mensaje")
    void laValidacionDevuelveCamposYMensajes() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/_pruebas/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"", "cantidad": 99}
                                """))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);

        JsonNode cuerpo = leer(resultado);
        assertThat(cuerpo.path("code").asText()).isEqualTo("REQUEST_VALIDATION_FAILED");

        // Sin la lista, el cliente no sabe qué corregir. Por eso este test exige los campos
        // concretos y no sólo el código.
        JsonNode errores = cuerpo.path("errors");
        assertThat(errores.isArray()).isTrue();
        assertThat(errores).hasSize(2);

        List<String> campos = new ArrayList<>();
        List<String> mensajes = new ArrayList<>();
        errores.forEach(error -> {
            campos.add(error.path("campo").asText());
            mensajes.add(error.path("mensaje").asText());
        });

        assertThat(campos).containsExactlyInAnyOrder("nombre", "cantidad");
        assertThat(mensajes).noneMatch(String::isBlank);
    }

    @Test
    @DisplayName("Un cuerpo ilegible devuelve 400 y no filtra el tipo que esperaba la API")
    void unCuerpoIlegibleNoFiltraInternos() throws Exception {
        // JSON truncado: falla al deserializar, antes incluso de validar los campos.
        JsonNode cuerpo = invocarJson("{\"nombre\": ", 400);

        assertThat(cuerpo.path("code").asText()).isEqualTo("MALFORMED_REQUEST");
        // El mensaje real de Jackson menciona la clase interna que esperaba. No debe salir.
        assertThat(cuerpo.path("detail").asText()).doesNotContain("ControladorDePruebas");
        assertThat(cuerpo.path("detail").asText()).doesNotContain("PeticionDePrueba");
    }

    @Test
    @DisplayName("Un fallo inesperado devuelve 500 generico y SIN traza de pila")
    void unFalloInesperadoNoFiltraElDetalle() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/_pruebas/inesperado")).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(500);

        String cuerpo = resultado.getResponse().getContentAsString();
        JsonNode nodo = leer(resultado);

        assertThat(nodo.path("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(nodo.path("detail").asText()).isEqualTo("Se ha producido un error inesperado");

        // El mensaje original NO puede salir. Ni el texto, ni la traza, ni el nombre de la clase.
        assertThat(cuerpo).doesNotContain("Detalle interno");
        assertThat(cuerpo).doesNotContain("IllegalStateException");
        assertThat(cuerpo).doesNotContain("java.lang");
    }

    @Test
    @DisplayName("Toda respuesta de error incluye timestamp y traceId para poder correlacionar")
    void todaRespuestaIncluyeTimestampYTraceId() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/_pruebas/dominio?codigo=ORDERS_EMPTY"))
                .andReturn();

        JsonNode cuerpo = leer(resultado);

        assertThat(cuerpo.has("timestamp")).isTrue();
        assertThat(cuerpo.path("timestamp").asText()).endsWith("Z");
        assertThat(cuerpo.path("traceId").asText()).isNotBlank();

        // Y el identificador del cuerpo es el mismo que el de la cabecera: si no coincidieran, el
        // usuario podría reportar uno y buscarse otro.
        assertThat(resultado.getResponse().getHeader(TraceIdFilter.HEADER))
                .isEqualTo(cuerpo.path("traceId").asText());
    }

    @Test
    @DisplayName("Un identificador de traza valido se reutiliza en vez de generar otro")
    void unTraceIdValidoSeReutiliza() throws Exception {
        String enviado = "8a1f2c3d-4e5b-6a7c-8d9e-0f1a2b3c4d5e";

        MvcResult resultado = mvc.perform(get("/api/v1/_pruebas/dominio?codigo=ORDERS_EMPTY")
                        .header(TraceIdFilter.REQUEST_HEADER, enviado))
                .andReturn();

        assertThat(resultado.getResponse().getHeader(TraceIdFilter.HEADER)).isEqualTo(enviado);
    }

    @Test
    @DisplayName("Un identificador de traza con formato invalido se descarta y se genera otro")
    void unTraceIdInvalidoSeDescarta() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/_pruebas/dominio?codigo=ORDERS_EMPTY")
                        .header(TraceIdFilter.REQUEST_HEADER, "texto inventado; drop table"))
                .andReturn();

        String recibido = resultado.getResponse().getHeader(TraceIdFilter.HEADER);

        // Aceptar el valor del cliente sin validarlo permitiría inyectar cualquier texto en los logs.
        assertThat(recibido).isNotEqualTo("texto inventado; drop table");
        assertThat(recibido).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("Una ruta inexistente devuelve 404 con el mismo formato y un codigo generico")
    void unaRutaInexistenteDevuelve404() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/no-existe")).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
        assertThat(resultado.getResponse().getContentType())
                .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        // Código genérico, NO uno de dominio: si la URL está mal escrita, decir
        // CATALOG_PIZZA_NOT_FOUND mandaría a buscar un problema de catálogo inexistente.
        assertThat(leer(resultado).path("code").asText()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    // ---------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------

    private int estadoDe(String codigo) throws Exception {
        return mvc.perform(get("/api/v1/_pruebas/dominio").param("codigo", codigo))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode invocar(String url, int estadoEsperado) throws Exception {
        MvcResult resultado = mvc.perform(get(url)).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(estadoEsperado);
        assertThat(resultado.getResponse().getContentType())
                .contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        return leer(resultado);
    }

    private JsonNode invocarJson(String cuerpo, int estadoEsperado) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/_pruebas/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(estadoEsperado);
        return leer(resultado);
    }

    private JsonNode leer(MvcResult resultado) throws Exception {
        return json.readTree(resultado.getResponse().getContentAsString());
    }
}