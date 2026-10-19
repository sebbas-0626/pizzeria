package com.pizzeria.infrastructure.adapter.out.persistence.repository;

import com.pizzeria.infrastructure.adapter.out.persistence.entity.PizzaVariantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
/**
 * Repositorio Spring Data de {@code pizza_variants}.
 *
 * <p>Interno del adaptador, por la misma razón que {@link PizzaJpaRepository}.
 *
 * <p>Sólo se usa para cargar <strong>las variantes de un conjunto de pizzas ya identificado</strong>. Esa es
 * la consulta que evita el N+1: en lugar de una consulta por pizza, una consulta con un {@code in} sobre
 * todos los identificadores de la página.
 */
public interface PizzaVariantJpaRepository extends JpaRepository<PizzaVariantJpaEntity, Long> {

    /**
     * Variantes de las pizzas indicadas, sólo las disponibles si se pide así.
     *
     * <p>El filtro de disponibilidad se aplica <strong>en la consulta</strong> y no después. Filtrar en
     * memoria significaría traer también las agotadas, y el listado público las expondría si alguien
     * olvidase el filtro al montar la respuesta.
     *
     * <p>La consulta no lleva {@code order by} a propósito. Ordenar por el enumerado desde JPQL ordenaría
     * alfabéticamente —LARGE, MEDIUM, SMALL—, que es justo el orden contrario al que espera un cliente que
     * elige un tamaño. El orden real se aplica en memoria con {@link com.pizzeria.domain.model.VariantSize#orden()},
     * donde es explícito y se puede probar; agrupar por pizza tampoco lo necesita, porque el adaptador usa
     * un mapa.
     */
    @Query("""
            select v from PizzaVariantJpaEntity v
            where v.pizzaId in :pizzaIds
              and (:soloDisponibles = false or v.available = true)
            """)
    List<PizzaVariantJpaEntity> findDePizzas(@Param("pizzaIds") List<Long> pizzaIds,
                                              @Param("soloDisponibles") boolean soloDisponibles);

    /** Variantes de una pizza. Se usa en el detalle, donde hay un solo identificador. */
    @Query("""
            select v from PizzaVariantJpaEntity v
            where v.pizzaId = :pizzaId
              and (:soloDisponibles = false or v.available = true)
            """)
    List<PizzaVariantJpaEntity> findDePizza(@Param("pizzaId") Long pizzaId,
                                            @Param("soloDisponibles") boolean soloDisponibles);
}