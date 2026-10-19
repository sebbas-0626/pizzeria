package com.pizzeria.infrastructure.adapter.in.web.dto;

import java.util.List;

/**
 * Envoltorio de las respuestas paginadas.
 *
 * <p>Los nombres de los campos son <strong>parte del contrato público</strong> y no se cambian por gusto:
 * quien integra contra la API los tiene escritos en su cliente. `content`, `page`, `size`,
 * `totalElements` y `totalPages` son los que usa US-031 para el listado de pedidos, así que fijarlos aquí
 * significa que los dos listados hablan el mismo idioma.
 *
 * @param content       elementos de la página pedida
 * @param page          índice de la página devuelta, empezando en 0
 * @param size          tamaño con el que se pidió la página
 * @param totalElements elementos que hay en total, no los de esta página
 * @param totalPages    cuántas páginas hay; 0 si el resultado está vacío
 * @param <T>           tipo del contenido
 */
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }
}