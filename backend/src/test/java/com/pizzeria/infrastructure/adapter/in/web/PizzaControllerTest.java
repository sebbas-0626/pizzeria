package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.application.dto.PageRequest;
import com.pizzeria.application.dto.PizzaPage;
import com.pizzeria.application.usecase.GetPizzaUseCase;
import com.pizzeria.application.usecase.ListPizzasUseCase;
import com.pizzeria.domain.exception.PizzaNotFoundException;
import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.model.PizzaVariant;
import com.pizzeria.domain.model.VariantSize;
import com.pizzeria.domain.model.vo.Money;
import com.pizzeria.infrastructure.config.ClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pruebas del contrato HTTP del catálogo público.
 *
 * <p>Lo que se comprueba aquí es el <strong>contrato</strong>: los nombres de los campos del JSON, los
 * valores por defecto de los parámetros, el estado de la validación y el código de error del 404. Ninguna de
 * estas cosas la decide el negocio, y todas son lo que un cliente tiene escrito en su código, así que
 * cambiarlas sin querer es una ruptura de API aunque las pruebas del dominio sigan en verde.
 *
 * <p>Los casos de uso van sueltos con {@code @MockitoBean}, nunca {@code @MockBean}: en Spring Boot 4 el
 * segundo está deprecado.
 *
 * <p>Sin base de datos a propósito: lo que consulta el adaptador se prueba en
 * {@code PizzaCatalogAdapterIT} contra PostgreSQL real. Si esta prueba usara la base, un fallo de SQL y un
 * fallo de contrato se mezclarían en el mismo mensaje.
 */
@WebMvcTest(PizzaController.class)
@Import(ClockConfig.class)
class PizzaControllerTest {

    private static final long ID_PIZZA = 33L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private ListPizzasUseCase listarPizzas;

    @MockitoBean
    private GetPizzaUseCase obtenerPizza;

    // ---- listado -------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /pizzas devuelve 200 con el envoltorio de pagina y sus metadatos")
    void listarDevuelveElEnvoltorioDePagina() throws Exception {
        when(listarPizzas.listar(any(PageRequest.class), eq(true)))
                .thenReturn(new PizzaPage(List.of(pizza()), 0, 20, 1, 1));

        JsonNode cuerpo = invocar("/api/v1/pizzas", 200);

        assertThat(cuerpo.path("content").isArray()).isTrue();
        assertThat(cuerpo.path("content").size()).isEqualTo(1);
        assertThat(cuerpo.path("page").asInt()).isZero();
        assertThat(cuerpo.path("size").asInt()).isEqualTo(20);
        assertThat(cuerpo.path("totalElements").asLong()).isEqualTo(1);
        assertThat(cuerpo.path("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("El catalogo publico muestra por defecto lo vendible (RN-DS-02)")
    void elListadoPideLoVendiblePorDefecto() throws Exception {
        when(listarPizzas.listar(any(PageRequest.class), eq(true)))
                .thenReturn(PizzaPage.vacia(0, 20));

        invocar("/api/v1/pizzas", 200);

        // availableOnly=true es el valor por defecto del catálogo público. Si alguien lo invirtiera,
        // el endpoint por defecto empezaría a filtrar sin que ninguna prueba de negocio lo notase.
        verify(listarPizzas).listar(new PageRequest(0, 20), true);
    }

    @Test
    @DisplayName("availableOnly=false llega intacto al caso de uso, para la vista previa del administrador")
    void availableOnlyFalseSePropaga() throws Exception {
        when(listarPizzas.listar(any(PageRequest.class), eq(false)))
                .thenReturn(PizzaPage.vacia(0, 20));

        invocar("/api/v1/pizzas?availableOnly=false", 200);

        verify(listarPizzas).listar(new PageRequest(0, 20), false);
    }

    @Test
    @DisplayName("page y size se traducen a la paginacion pedida")
    void losParametrosDePaginacionSeTraducen() throws Exception {
        when(listarPizzas.listar(any(PageRequest.class), eq(true)))
                .thenReturn(new PizzaPage(List.of(), 2, 5, 11, 3));

        JsonNode cuerpo = invocar("/api/v1/pizzas?page=2&size=5", 200);

        verify(listarPizzas).listar(new PageRequest(2, 5), true);
        assertThat(cuerpo.path("size").asInt()).isEqualTo(5);
        assertThat(cuerpo.path("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("Un catalogo vacio devuelve 200 con content vacio, no un 404 ni un cuerpo nulo")
    void unCatalogoVacioDevuelve200() throws Exception {
        when(listarPizzas.listar(any(PageRequest.class), eq(true)))
                .thenReturn(PizzaPage.vacia(0, 20));

        JsonNode cuerpo = invocar("/api/v1/pizzas", 200);

        assertThat(cuerpo.path("content").isArray()).isTrue();
        assertThat(cuerpo.path("content").size()).isZero();
        assertThat(cuerpo.path("totalElements").asLong()).isZero();
        assertThat(cuerpo.path("totalPages").asInt()).isZero();
    }

    @Test
    @DisplayName("La pizza expone name, description, price decimal y size del conjunto admitido")
    void laPizzaExponeLosCamposDelContrato() throws Exception {
        when(listarPizzas.listar(any(PageRequest.class), eq(true)))
                .thenReturn(new PizzaPage(List.of(pizza()), 0, 20, 1, 1));

        JsonNode pizza = invocar("/api/v1/pizzas", 200).path("content").get(0);

        assertThat(pizza.path("name").asText()).isEqualTo("Margarita");
        assertThat(pizza.path("description").asText()).isEqualTo("Tomate y mozzarella");

        JsonNode variante = pizza.path("variants").get(0);
        assertThat(variante.path("size").asText()).isEqualTo("MEDIUM");
        assertThat(variante.path("available").asBoolean()).isTrue();
        // El precio es un numero JSON, no texto: 12500 y no "12500". Y sin decimales, porque el COP
        // no los tiene (RN-IM-05).
        assertThat(variante.path("price").isNumber()).isTrue();
        assertThat(variante.path("price").decimalValue()).isEqualByComparingTo(new BigDecimal("12500"));
        assertThat(variante.path("price").decimalValue().scale()).isZero();
    }

    @Test
    @DisplayName("Una pizza sin descripcion la publica como null, no como cadena vacia")
    void unaDescripcionAusenteEsNull() throws Exception {
        when(listarPizzas.listar(any(PageRequest.class), eq(true)))
                .thenReturn(new PizzaPage(List.of(pizzaSinDescripcion()), 0, 20, 1, 1));

        JsonNode pizza = invocar("/api/v1/pizzas", 200).path("content").get(0);

        assertThat(pizza.path("description").isNull()).isTrue();
    }

    // ---- validacion de la paginacion -------------------------------------------------------------

    @Test
    @DisplayName("Un size de 0 responde 400 y no llega al caso de uso")
    void unSizeCeroSeRechaza() throws Exception {
        JsonNode cuerpo = invocar("/api/v1/pizzas?size=0", 400);

        assertThat(cuerpo.path("code").asText()).isEqualTo("REQUEST_VALIDATION_FAILED");
        // La petición no debe llegar a la aplicación: es un error del cliente, no del catálogo.
        verify(listarPizzas, never()).listar(any(PageRequest.class), anyBoolean());
    }

    @Test
    @DisplayName("Un size por encima del maximo responde 400")
    void unSizePorEncimaDelMaximoSeRechaza() throws Exception {
        JsonNode cuerpo = invocar("/api/v1/pizzas?size=" + (PageRequest.TAMANO_MAXIMO + 1), 400);

        assertThat(cuerpo.path("code").asText()).isEqualTo("REQUEST_VALIDATION_FAILED");
    }

    @Test
    @DisplayName("Una page negativa responde 400")
    void unaPageNegativaSeRechaza() throws Exception {
        invocar("/api/v1/pizzas?page=-1", 400);

        verify(listarPizzas, never()).listar(any(PageRequest.class), anyBoolean());
    }

    // ---- detalle -------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /pizzas/{id} devuelve 200 con la pizza y sus variantes")
    void detalleDevuelveLaPizza() throws Exception {
        when(obtenerPizza.obtener(anyLong())).thenReturn(pizza());

        JsonNode cuerpo = invocar("/api/v1/pizzas/" + ID_PIZZA, 200);

        assertThat(cuerpo.path("id").asLong()).isEqualTo(ID_PIZZA);
        assertThat(cuerpo.path("variants").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una pizza inexistente devuelve 404 con CATALOG_PIZZA_NOT_FOUND")
    void unaPizzaInexistenteDevuelve404() throws Exception {
        when(obtenerPizza.obtener(anyLong()))
                .thenThrow(new PizzaNotFoundException(ID_PIZZA));

        JsonNode cuerpo = invocar("/api/v1/pizzas/" + ID_PIZZA, 404);

        assertThat(cuerpo.path("code").asText()).isEqualTo("CATALOG_PIZZA_NOT_FOUND");
        assertThat(cuerpo.path("status").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("Un identificador que no es un número devuelve 400, no un 404 de recurso")
    void unIdentificadorMalFormadoDevuelve400() throws Exception {
        // Un 404 aquí sería mentir: el recurso no es que no exista, es que la dirección está mal
        // escrita. Y un 500 sería un fallo nuestro disfrazado de problema del cliente.
        JsonNode cuerpo = invocar("/api/v1/pizzas/no-es-un-uuid", 400);

        assertThat(cuerpo.path("code").asText()).isEqualTo("MALFORMED_REQUEST");
    }

    // ---- ayudantes -----------------------------------------------------------------------------

    private JsonNode invocar(String ruta, int estadoEsperado) throws Exception {
        MvcResult resultado = mvc.perform(get(ruta)).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(estadoEsperado);
        return json.readTree(resultado.getResponse().getContentAsString());
    }

    private static Pizza pizza() {
        return pizzaConDescripcion("Tomate y mozzarella");
    }

    private static Pizza pizzaSinDescripcion() {
        return pizzaConDescripcion(null);
    }

    private static Pizza pizzaConDescripcion(String descripcion) {
        PizzaVariant variante = PizzaVariant.reconstituir(
                44L, ID_PIZZA,
                VariantSize.MEDIUM, Money.of(12500L), true,
                Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"));

        return Pizza.reconstituir(ID_PIZZA, "Margarita", descripcion, true, null, List.of(variante),
                Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"));
    }
}