package com.pizzeria.infrastructure.adapter.in.web.mapper;

import com.pizzeria.application.dto.PizzaPage;
import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.model.PizzaVariant;
import com.pizzeria.infrastructure.adapter.in.web.dto.PageResponse;
import com.pizzeria.infrastructure.adapter.in.web.dto.PizzaResponse;

/**
 * Traduce el modelo de dominio al JSON del contrato.
 *
 * <p>Es el último punto donde el dominio se convierte en algo que se puede publicar por HTTP. Al estar en la
 * capa de entrada, ve los dos lados; el dominio no ve ninguno de los dos, y por eso puede seguir siendo Java
 * sin Enter en su contrato.
 *
 * <p><strong>El tamaño se serializa como texto y no como enumerado.</strong> Es deliberado: si el
 * enumerado llegara tal cual al JSON, su nombre en español o una futura renombrada romperían el contrato
 * sin que nadie se entere. Escribir {@code size.name()} hace que el valor publicado dependa de una sola
 * línea, y esa línea se ve en el diff.
 */
public final class PizzaWebMapper {

    private PizzaWebMapper() {
        // Clase de utilidad: sin estado y sin instancias.
    }

    public static PizzaResponse aRespuesta(Pizza pizza) {
        return new PizzaResponse(
                pizza.id(),
                pizza.name(),
                pizza.description(),
                pizza.variants().stream().map(PizzaWebMapper::aRespuesta).toList());
    }

    public static PizzaResponse.VariantResponse aRespuesta(PizzaVariant variante) {
        return new PizzaResponse.VariantResponse(
                variante.id(),
                variante.size().name(),
                variante.price().importe(),
                variante.available());
    }

    public static PageResponse<PizzaResponse> aPagina(PizzaPage pagina) {
        return new PageResponse<>(
                pagina.pizzas().stream().map(PizzaWebMapper::aRespuesta).toList(),
                pagina.pagina(),
                pagina.tamano(),
                pagina.totalElementos(),
                pagina.totalPaginas());
    }
}