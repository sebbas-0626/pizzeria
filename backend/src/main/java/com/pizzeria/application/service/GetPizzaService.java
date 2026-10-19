package com.pizzeria.application.service;

import com.pizzeria.application.usecase.GetPizzaUseCase;
import com.pizzeria.domain.exception.PizzaNotFoundException;
import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.port.out.PizzaCatalogPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de la consulta del detalle de una pizza.
 *
 * <p>Traduce el «no está» del puerto a la excepción de dominio que la capa web sabe traducir a 404. El
 * caso de uso es quien decide que ausencia es un error de negocio; el puerto sólo devuelve un vacío
 * porque para él un identificador desconocido no es una excepción, es un resultado.
 *
 * <p>La búsqueda va <strong>por identificador, no por propietario</strong>: el catálogo es público. En US-022
 * el filtrado por propietario ocurre en la consulta, pero allí hay un propietario que filtrar; aquí no lo
 * hay y aplicar el mismo precaution por analogía sería inventar un requisito.
 *
 */

@Service
@Transactional(readOnly = true)
public class GetPizzaService implements GetPizzaUseCase {

    private final PizzaCatalogPort puerto;

    public GetPizzaService(PizzaCatalogPort puerto) {
        this.puerto = puerto;
    }

    @Override
    public Pizza obtener(long id) {
        return puerto.buscarPorId(id)
                .orElseThrow(() -> new PizzaNotFoundException(id));
    }
}
