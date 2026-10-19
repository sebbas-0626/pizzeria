package com.pizzeria.infrastructure.adapter.out.persistence.mapper;

import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.model.PizzaVariant;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.PizzaJpaEntity;

import java.util.List;

/**
 * Convierte filas de {@code pizzas} en pizzas del dominio, con sus variantes ya unidas.
 *
 * <p>Recibe las variantes <strong>de esa pizza concreta</strong>, no todas las de la consulta. Quien llama
 * ya las ha agrupado, y mantener el agrupamiento fuera del mapper deja aquí una función con una sola
 * responsabilidad: unir dos filas en un objeto.
 *
 * <p>La lista se entrega tal cual porque {@link Pizza} ya la copia en su construcción. Duplicar la copia
 * aquí sería gastar memoria en una instancia que nadie va a mutar.
 */
public final class PizzaMapper {

    private PizzaMapper() {
        // Clase de utilidad: sin estado y sin instancias.
    }

    public static Pizza aDominio(PizzaJpaEntity entidad, List<PizzaVariant> variantes) {
        return Pizza.reconstituir(
                entidad.getId(),
                entidad.getName(),
                entidad.getDescription(),
                entidad.isActive(),
                entidad.getImageKey(),
                variantes,
                entidad.getCreatedAt(),
                entidad.getUpdatedAt());
    }
}