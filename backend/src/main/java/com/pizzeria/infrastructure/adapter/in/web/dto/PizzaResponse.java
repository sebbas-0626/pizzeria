package com.pizzeria.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;
/**
 * Cuerpo JSON de una pizza del catálogo.
 *
 * <p>Es un DTO de <strong>transporte</strong>: existe porque el contrato HTTP no puede exponer el modelo de
 * dominio tal cual. Se cambia cuando cambia el contrato, no cuando cambia el negocio, y por eso no lleva
 * invariantes ni métodos de negocio.
 *
 * <p><strong>Por qué {@code price} es {@code BigDecimal} y no texto.</strong> Porque es un número, y un
 * cliente tiene que poder sumar. El texto obligaría a cada integrante a escribir su propio parseo y a
 * acordarse de que en COP no hay decimales. El valor llega con escala 0 desde {@link
 * com.pizzeria.domain.model.vo.Money}, así que el JSON es {@code 12500} y no {@code 12500.00}.
 *
 * <p><strong>Por qué no hay {@code imageUrl} todavía.</strong> US-052 es la historia que publica la imagen,
 * y publica una URL prefirmada generada por el servidor, no la clave de almacenamiento que hay en la fila.
 * Añadir aquí un campo que todavía no tiene un valor real sería parte del contrato de una historia futura.
 *
 * @param id          identificador de la pizza
 * @param name        nombre, único entre pizzas activas
 * @param description descripción opcional; {@code null} si la pizza no tiene
 * @param variants    variantes, de menor a mayor tamaño
 */
public record PizzaResponse(long id,
                            String name,
                            String description,
                            List<VariantResponse> variants) {

    /**
     * @param id        identificador de la variante
     * @param size      tamaño, uno de SMALL, MEDIUM o LARGE
     * @param price     precio actual, en COP y sin decimales
     * @param available si se puede pedir ahora mismo
     */
    public record VariantResponse(long id, String size, BigDecimal price, boolean available) {
    }
}