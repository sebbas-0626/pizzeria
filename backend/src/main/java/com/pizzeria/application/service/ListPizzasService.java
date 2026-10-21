package com.pizzeria.application.service;

import com.pizzeria.application.dto.PageRequest;
import com.pizzeria.application.dto.PizzaPage;
import com.pizzeria.application.usecase.ListPizzasUseCase;
import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.port.out.PizzaCatalogPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementación del listado público del catálogo.
 *
 * <p>Es orquestación pura: pregunta al puerto qué pizzas hay en la ventana pedida, cuánto hay en total y
 * devuelve la página. No decide <em>qué</em> es una pizza ni qué significa que esté disponible; eso vive en
 * el dominio y en el adaptador.
 *
 * <p><strong>Por qué {@code readOnly = true}.</strong> La frontera transaccional está en la capa de
 * aplicación (ADR-0002) y una lectura no debería prometer más de lo que promete. Además {@code readOnly}
 * permite a la base descartar el deshacer, que es una diferencia real cuando el catálogo crece.
 *
 * <p><strong>Por qué una página fuera de rango no es un error.</strong> Un cliente que recorre el catálogo
 * llega a la última página y, si salta una de más, recibe una página vacía con el total correcto. Devolver
 * un 404 obligaría al cliente a distinguir dos casos que desde fuera son el mismo.
 */
@Service
@Transactional(readOnly = true)
public class ListPizzasService implements ListPizzasUseCase {

    private final PizzaCatalogPort puerto;

    public ListPizzasService(PizzaCatalogPort puerto) {
        this.puerto = puerto;
    }

    @Override
    public PizzaPage listar(PageRequest paginacion, boolean soloVariantesDisponibles) {
        List<Pizza> pizzas = puerto.listar(
                paginacion.tamano(),
                paginacion.desplazamiento(),
                soloVariantesDisponibles);

        long total = puerto.contar(soloVariantesDisponibles);

        return new PizzaPage(pizzas, paginacion.pagina(), paginacion.tamano(),
                total, paginacion.totalPaginas(total));
    }
}
