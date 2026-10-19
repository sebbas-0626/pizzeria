package com.pizzeria.infrastructure.adapter.out.persistence.repository;

import com.pizzeria.infrastructure.adapter.out.persistence.entity.PizzaJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
/**
 * Repositorio Spring Data de {@code pizzas}.
 *
 * <p>Es interno del adaptador de persistencia: implementa {@code PizzaCatalogPort}, no lo reemplaza. Nadie
 * fuera de {@code infrastructure.adapter.out.persistence} lo inyecta, porque si el resto del proyecto
 * dependiera de él la frontera dejaría de existir y las reglas de dependencia de ADR-0004 sólo se comprobarían
 * en el papel.
 *
 * <p><strong>El filtro se escribe en tres consultas y tiene que ser idéntico en las tres.</strong> La
 * tercera cuenta, y si su filtro no coincide con el de las dos primeras el cliente recibe un
 * {@code totalElements} que no corresponde con lo que ve. Por eso el predicado está escrito con el mismo
 * formato en cada una; si se cambia uno, hay que cambiar los tres.
 */
public interface PizzaJpaRepository extends JpaRepository<PizzaJpaEntity, Long> {

    /**
     * Pizza activa que además tiene al menos una variante disponible (RN-DS-02).
     *
     * <p>El {@code exists} es una subconsulta correlacionada y no un {@code join}: si se hiciera con un
     * {@code join} sobre variantes, una pizza con tres variantes disponibles aparecería tres veces. Con
     * {@code exists} la fila es una, y {@code limit}/{@code offset} cuentan pizzas, no variantes.
     */
    @Query("""
            select p from PizzaJpaEntity p
            where p.active = true
              and exists (
                  select v from PizzaVariantJpaEntity v
                  where v.pizzaId = p.id and v.available = true
              )
            order by lower(p.name)
            """)
    List<PizzaJpaEntity> findPedibles(Pageable paginable);

    /**
     * Pizza por identificador, sin filtro de visibilidad.
     *
     * <p>El filtro se aplica en memoria, en el adaptador, y no aquí. La razón es que el resultado visible
     * depende de <em>dos</em> condiciones —que esté activa y que tenga alguna variante disponible— y meter
     * la segunda en la consulta exigiría el mismo {@code exists} que en el listado. La fila se lee siempre
     * una sola vez por identificador, así que no hay coste de N+1 en decidirla.
     */
    @Query("select p from PizzaJpaEntity p where p.id = :id")
    Optional<PizzaJpaEntity> findByIdVisible(@Param("id") Long id);

    /** Todas las pizzas, incluidas las dadas de baja. Vista previa de administración. */
    @Query("select p from PizzaJpaEntity p order by lower(p.name)")
    List<PizzaJpaEntity> findTodas(Pageable paginable);

    /** Pizzas pedibles para el total, sin paginar. Mismo filtro que {@link #findPedibles}. */
    @Query("""
            select count(p) from PizzaJpaEntity p
            where p.active = true
              and exists (
                  select v from PizzaVariantJpaEntity v
                  where v.pizzaId = p.id and v.available = true
              )
            """)
    long contarPedibles();

    /** Todas las pizzas para el total, sin paginar. Mismo criterio que {@link #findTodas}. */
    @Query("select count(p) from PizzaJpaEntity p")
    long contarTodas();
}