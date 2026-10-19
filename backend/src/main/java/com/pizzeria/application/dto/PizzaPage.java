package com.pizzeria.application.dto;

import com.pizzeria.domain.model.Pizza;

import java.util.List;

/**
 * Una ventana del catálogo: qué página se pidió, cuál se devolvió y cuántas hay en total.
 *
 * <p>Es una envolvente de paginación, no una copia de los datos. Las pizzas que transporta son el
 * <strong>modelo de dominio</strong>, y la capa web las convierte en el DTO de transporte. Duplicar aquí
 * cada pizza como otro record sería mantener dos representaciones de lo mismo que habría que cambiar en
 * dos sitios cada vez que cambie el dominio.
 *
 * <p>Los metadatos de paginación viven en esta capa y no en el dominio porque son una forma de transportar
 * el resultado, no una regla del negocio. Nadie decide que «la página 3 de un catálogo» sea una cosa de
 * pizzas.
 *
 * @param pizzas         contenido de la página pedida, ya filtrado
 * @param pagina         índice de la página devuelta, empezando en 0
 * @param tamano         tamaño con el que se pidió la página
 * @param totalElementos pizzas que hay en total con ese filtro, no las de esta página
 * @param totalPaginas   cuántas páginas hay en total; 0 si el catálogo está vacío
 */
public record PizzaPage(List<Pizza> pizzas,
                        int pagina,
                        int tamano,
                        long totalElementos,
                        int totalPaginas) {

    /**
     * Copia la lista en la construcción. Sin esto el record sería sólo inmutable en su referencia, y quien
     * lo construyese podría mutar la lista después.
     */
    public PizzaPage {
        pizzas = List.copyOf(pizzas);
    }

    /** Catálogo vacío: una respuesta válida con cero pizzas, no un error ni un cuerpo nulo. */
    public static PizzaPage vacia(int pagina, int tamano) {
        return new PizzaPage(List.of(), pagina, tamano, 0, 0);
    }
}