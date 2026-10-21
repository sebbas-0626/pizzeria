package com.pizzeria.infrastructure.adapter.out.persistence;

import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.model.PizzaVariant;
import com.pizzeria.domain.port.out.PizzaCatalogPort;
import com.pizzeria.infrastructure.adapter.out.persistence.entity.PizzaJpaEntity;
import com.pizzeria.infrastructure.adapter.out.persistence.mapper.PizzaMapper;
import com.pizzeria.infrastructure.adapter.out.persistence.mapper.PizzaVariantMapper;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.PizzaJpaRepository;
import com.pizzeria.infrastructure.adapter.out.persistence.repository.PizzaVariantJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;import java.util.stream.Collectors;

/**
 * Adaptador de salida que lee el catálogo de pizzas en PostgreSQL.
 *
 * <p>Es la única clase del proyecto que sabe que el catálogo vive en dos tablas y que hay que unirlas. El
 * dominio pide pizzas con variantes y recibe pizzas con variantes; si esta clase desapareciera, el dominio
 * no se enteraría.
 *
 * <p><strong>El número de consultas es fijo y no depende del número de pizzas.</strong> Un listado son
 * exactamente tres sentencias —las pizzas de la ventana, las variantes de esas pizzas y el total— sea cual
 * sea el tamaño de la página. La alternativa «devolver las pizzas y que cada variante se cargue sola» es el
 * N+1 clásico: con cinco pizzas son cinco consultas, con quinientas son quinientas, y el defecto aparece
 * cuando ya hay datos reales y nadie sabe de dónde salió. {@code PizzaCatalogAdapterIT} cuenta las
 * sentencias precisamente para que esa vuelta atrás no pase en silencio.
 *
 * <p><strong>Por qué el desplazamiento se traduce a página.</strong> Spring Data pagina por índice de página
 * y la base pagina por desplazamiento. La conversión está en un solo sitio, y {@link PageRequest} (el de
 * aplicación) es el que calcula el desplazamiento, de modo que el número que el cliente envía y el número
 * que llega al SQL salen del mismo sitio.
 *
 * <p>No lleva {@code @Transactional}: la transacción es del caso de uso, que es donde vive la frontera
 * (ADR-0002). Anotarlo aquí además abriría transacciones no previstas cuando alguien use el adaptador
 * directamente.
 */
@Repository
public class PizzaCatalogAdapter implements PizzaCatalogPort {

    private final PizzaJpaRepository pizzas;
    private final PizzaVariantJpaRepository variantes;

    public PizzaCatalogAdapter(PizzaJpaRepository pizzas, PizzaVariantJpaRepository variantes) {
        this.pizzas = pizzas;
        this.variantes = variantes;
    }

    @Override
    public Optional<Pizza> buscarPorId(long id) {
        Optional<PizzaJpaEntity> entidad = pizzas.findByIdVisible(id);
        if (entidad.isEmpty()) {
            return Optional.empty();
        }

        PizzaJpaEntity pizza = entidad.get();
        List<PizzaVariant> susVariantes = PizzaVariantMapper.aDominio(variantes.findDePizza(id, false));

        // Misma regla que el listado, pero en memoria: aquí sólo hay una fila, así que no hay coste de
        // consultas y la condición puede vivir donde se lee.
        if (!pizza.isActive() || susVariantes.stream().noneMatch(PizzaVariant::available)) {
            return Optional.empty();
        }

        return Optional.of(PizzaMapper.aDominio(pizza, susVariantes));
    }

    @Override
    public List<Pizza> listar(int limite, int desplazamiento, boolean soloVariantesDisponibles) {
        List<PizzaJpaEntity> ventana = soloVariantesDisponibles
                ? pizzas.findPedibles(PageRequest.of(desplazamiento / limite, limite))
                : pizzas.findTodas(PageRequest.of(desplazamiento / limite, limite));

        if (ventana.isEmpty()) {
            // Consulta de menos: no se lanza un `in ()` contra una lista vacía. Sin esto, una página fuera
            // de rango añadiría una consulta inútil en cada listado.
            return List.of();
        }

        List<Long> ids = ventana.stream().map(PizzaJpaEntity::getId).toList();

        Map<Long, List<PizzaVariant>> porPizza = variantes.findDePizzas(ids, soloVariantesDisponibles)
                .stream()
                .map(PizzaVariantMapper::aDominio)
                .collect(Collectors.groupingBy(PizzaVariant::pizzaId));

        List<Pizza> resultado = new ArrayList<>(ventana.size());
        for (PizzaJpaEntity entidad : ventana) {
            // El orden por tamaño se aplica DESPUÉS de agrupar: la consulta no lo pide y el agrupado
            // por mapa no conserva el orden de llegada. Ordenarlo aquí es lo que garantiza que el
            // catálogo muestre SMALL, MEDIUM, LARGE y no lo que PostgreSQL devuelva.
            List<PizzaVariant> variantesDeEstaPizza = PizzaVariantMapper.ordenarPorTamano(
                    porPizza.getOrDefault(entidad.getId(), List.of()));

            resultado.add(PizzaMapper.aDominio(entidad, variantesDeEstaPizza));
        }
        return List.copyOf(resultado);
    }

    @Override
    public long contar(boolean soloVariantesDisponibles) {
        return soloVariantesDisponibles ? pizzas.contarPedibles() : pizzas.contarTodas();
    }
}
