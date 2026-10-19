package com.pizzeria.application.usecase;

import com.pizzeria.domain.model.Pizza;

/** Caso de uso de consulta del detalle de una pizza del catálogo público. */
public interface GetPizzaUseCase {

    /**
     * Una pizza con sus variantes.
     *
     * @throws com.pizzeria.domain.exception.PizzaNotFoundException si no existe o no se puede mostrar
     */
    Pizza obtener(long id);
}