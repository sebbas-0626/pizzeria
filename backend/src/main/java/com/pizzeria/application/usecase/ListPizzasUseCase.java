package com.pizzeria.application.usecase;

import com.pizzeria.application.dto.PageRequest;
import com.pizzeria.application.dto.PizzaPage;

/**
 * Caso de uso de listado público del catálogo.
 *
 * <p>Es una interfaz y no una clase porque separa <em>qué</em> se puede hacer del catálogo de <em>cómo</em> lo
 * hace Spring: la capa web depende de esta interfaz y las pruebas pueden sustituirla entera sin arrancar un
 * contexto. La excepción está declarada en ADR-0004 §3 y no se extiende a ningún otro tipo.
 */
public interface ListPizzasUseCase {

    /**
     * Una página del catálogo.
     *
     * @param paginacion                ventana pedida, con sus límites ya verificados
     * @param soloVariantesDisponibles si es {@code true}, sólo lo que se puede pedir ahora mismo (RN-DS-02)
     */
    PizzaPage listar(PageRequest paginacion, boolean soloVariantesDisponibles);
}