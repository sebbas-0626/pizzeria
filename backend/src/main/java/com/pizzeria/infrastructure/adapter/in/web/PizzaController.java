package com.pizzeria.infrastructure.adapter.in.web;

import com.pizzeria.application.dto.PageRequest;
import com.pizzeria.application.usecase.GetPizzaUseCase;
import com.pizzeria.application.usecase.ListPizzasUseCase;
import com.pizzeria.domain.model.Pizza;
import com.pizzeria.infrastructure.adapter.in.web.dto.PageResponse;
import com.pizzeria.infrastructure.adapter.in.web.dto.PizzaResponse;
import com.pizzeria.infrastructure.adapter.in.web.mapper.PizzaWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo público de pizzas.
 *
 * <p>Las dos rutas son públicas por requisito (RF-CA-01, RF-CA-02) y por eso <strong>no llevan
 * anotación de autorización</strong>. Cuando US-021 añada Spring Security, esta clase seguirá igual: lo
 * protegido en US-011…US-013 es {@code /admin/**}, no esto. Poner aquí un {@code @PreAuthorize} «público»
 * sería una afirmación sobre un sistema de seguridad que todavía no existe.
 *
 * <p>El controlador no contiene reglas de negocio: traduce parámetros de consulta a un
 * {@link PageRequest}, delega en el caso de uso y devuelve el resultado. El filtro de disponibilidad, el
 * orden y la paginación los decide el catálogo, no esta clase.
 *
 * <p><strong>Por qué los límites de página están en las anotaciones y no en el código.</strong> Porque son
 * parte del contrato: {@code size=0} o {@code size=1000} son peticiones mal formadas y tienen que
 * responder 400 con el detalle del campo, no una excepción de Java. Al declararlos aquí los valida Spring y
 * los traduce {@code GlobalExceptionHandler}. El {@link PageRequest} vuelve a comprobarlos porque es
 * alcanzable desde código que no pasa por el controlador.
 *
 * <p><strong>Por qué hay anotaciones de OpenAPI.</strong> Sin ellas springdoc publica la ruta, los
 * parámetros y el cuerpo, pero deja el 404 y el 400 sin documentar y llama a la operación {@code listar},
 * que es un nombre interno en un identificador que se usa para generar clientes. Lo que se documenta es
 * exactamente lo que está en {@code api-contract.md} §4.1: si divergen, es un bug.
 */
@RestController
@RequestMapping(path = "/api/v1/pizzas", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Catálogo", description = """
        Catálogo público de pizzas. No requiere autenticación.

        Los importes son pesos colombianos (COP), enteros y **sin decimales**: `12500`, nunca `12500.50`.
        El servidor calcula todos los importes (RN-IM-01).
        """)
public class PizzaController {

    private final ListPizzasUseCase listarPizzas;
    private final GetPizzaUseCase obtenerPizza;

    public PizzaController(ListPizzasUseCase listarPizzas, GetPizzaUseCase obtenerPizza) {
        this.listarPizzas = listarPizzas;
        this.obtenerPizza = obtenerPizza;
    }

    /**
     * Página del catálogo.
     *
     * @param availableOnly si es {@code true}, sólo pizzas que se pueden pedir ahora mismo. Por defecto
     *                      {@code true}: el catálogo público muestra lo vendible, no el inventario entero
     *                      (RN-DS-02). El valor {@code false} existe para la vista previa del
     *                      administrador y trae también lo inactivo.
     * @param page          índice de página, desde 0
     * @param size          filas por página, entre 1 y 100
     */
    @Operation(
            operationId = "listPizzas",
            summary = "Lista el catálogo de pizzas en páginas",
            description = """
                    Ordenado por nombre, sin distinguir mayúsculas. Una pizza **sin ninguna variante
                    disponible no aparece**, aunque esté activa (RN-DS-02): anunciarla sería una entrada que
                    nadie puede convertir en pedido.

                    Un catálogo vacío responde `200` con `content: []`, nunca `404`. Una página más allá de
                    la última también responde `200` vacía, con el total correcto.
                    """)
    @ApiResponses({
            // Sin `implementation` en el 200 a propósito: springdoc lo deduce del tipo de retorno, y al
            // declararlo a mano se pierde el parámetro genérico y el esquema sale como `PageResponse`
            // en vez de `PageResponsePizzaResponse`, sin saber qué contiene.
            @ApiResponse(responseCode = "200", description = "Página del catálogo"),
            @ApiResponse(responseCode = "400", description = """
                    `page` negativo o `size` fuera del rango 1–100.
                    `code`: `REQUEST_VALIDATION_FAILED`.
                    """,
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public PageResponse<PizzaResponse> listar(
            @RequestParam(name = "availableOnly", defaultValue = "true")
            @Parameter(description = """
                    Por defecto `true`: sólo pizzas que se pueden pedir ahora mismo. Con `false` se ve
                    también lo dado de baja y lo agotado (vista previa del administrador).
                    """)
            boolean availableOnly,
            @RequestParam(name = "page", defaultValue = "0")
            @Parameter(description = "Índice de página. Empieza en 0.")
            @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20")
            @Parameter(description = "Filas por página. Entre 1 y 100.")
            @Min(PageRequest.TAMANO_MINIMO) @Max(PageRequest.TAMANO_MAXIMO) int size) {

        PageRequest paginacion = new PageRequest(page, size);

        return PizzaWebMapper.aPagina(listarPizzas.listar(paginacion, availableOnly));
    }

    /**
     * Detalle de una pizza con sus variantes.
     *
     * @throws com.pizzeria.domain.exception.PizzaNotFoundException si no existe o no se puede mostrar, que
     *                                                            el manejador traduce a 404
     *                                                            {@code CATALOG_PIZZA_NOT_FOUND}
     */
    @Operation(
            operationId = "getPizza",
            summary = "Detalle de una pizza con sus variantes",
            description = """
                    Las variantes vienen de menor a mayor tamaño: `SMALL`, `MEDIUM`, `LARGE`.

                    «No existe», «está dada de baja» y «no tiene ninguna variante disponible» devuelven
                    **el mismo 404** a propósito: el catálogo público no enseña lo dado de baja, así que
                    confirmarlo por el mismo camino que buscarlo sería filtrar información.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La pizza y sus variantes"),
            @ApiResponse(responseCode = "404", description = """
                    No existe, está dada de baja o no tiene ninguna variante disponible.
                    `code`: `CATALOG_PIZZA_NOT_FOUND`.
                    """,
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "400", description = """
                    El identificador no es un número entero.
                    `code`: `MALFORMED_REQUEST`.
                    """,
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{pizzaId}")
    public PizzaResponse obtener(
            @Parameter(description = "Identificador de la pizza. Es un número entero: 1, 2, 3...")
            @PathVariable("pizzaId") long pizzaId) {
        Pizza pizza = obtenerPizza.obtener(pizzaId);
        return PizzaWebMapper.aRespuesta(pizza);
    }
}