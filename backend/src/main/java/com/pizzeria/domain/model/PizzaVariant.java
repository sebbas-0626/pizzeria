package com.pizzeria.domain.model;

import com.pizzeria.domain.model.vo.Money;

import java.time.Instant;
import java.util.Objects;

/**
 * Variante de una pizza: un tamaño con su precio y su disponibilidad.
 *
 * <p>Es el precio <strong>válido</strong> del catálogo, no el que se cobra. El precio que paga un cliente es
 * el del pedido (RN-FH-02): cuando cambia el de aquí, los pedidos ya creados no se tocan.
 *
 * <p><strong>Por qué aquí no se comprueba que el precio sea mayor que cero (RN-IM-07).</strong> Porque el
 * único camino que puede producir un precio inválido es la API de administración, y esa comprobación —
 *junto con el código de error que la traduce— llega con US-012. Esta historia sólo lee, y los datos que lee
 * los garantiza la propia base con {@code chk_pizza_variants_price}. Añadir aquí una validación sin
 * historia que la necesite sería anticipar un contrato de error que nadie ha pedido.
 *
 * <p>Lo que sí se comprueba es lo que {@link Money} garantiza para cualquier importe: nunca negativo y
 * siempre sin decimales.
 *
 * <p>Es inmutable y su {@code pizzaId} es parte de la identidad: dos variantes con el mismo identificador
 * son la misma variante, este objeto o su foto en otra base de datos.
 */
public final class PizzaVariant {

    private final long id;
    private final long pizzaId;
    private final VariantSize size;
    private final Money price;
    private final boolean available;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PizzaVariant(long id,
                         long pizzaId,
                         VariantSize size,
                         Money price,
                         boolean available,
                         Instant createdAt,
                         Instant updatedAt) {
        this.id = id;
        this.pizzaId = pizzaId;
        this.size = Objects.requireNonNull(size, "El tamaño de la variante es obligatorio");
        this.price = Objects.requireNonNull(price, "El precio de la variante es obligatorio");
        this.available = available;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Reconstruye una variante a partir de lo que hay persistido.
     *
     * <p>Los identificadores los genera la aplicación y no la base (ver {@code db/migration/README.md} §4),
     * así que un agregado necesita existir con su id ANTES de insertarse. Este método es el puente desde la
     * fila; no es un constructor público porque el orden de los argumentos no describe lo que significa una
     * variante.
     *
     * <p><strong>Los dos identificadores tienen que ser positivos.</strong> Con UUID eso no se podía
     * comprobar; con BIGINT un {@code 0} significaría una variante guardada sin identificador, o una
     * variante que no pertenece a ninguna pizza. Son invariantes del agregado, así que se comprueban al
     * construirlo y no en la base.
     */
    public static PizzaVariant reconstituir(long id,
                                            long pizzaId,
                                            VariantSize size,
                                            Money price,
                                            boolean available,
                                            Instant createdAt,
                                            Instant updatedAt) {
        if (id <= 0) {
            throw new IllegalArgumentException("El identificador de la variante tiene que ser un numero "
                    + "positivo: " + id);
        }
        if (pizzaId <= 0) {
            throw new IllegalArgumentException("La variante tiene que pertenecer a una pizza: " + pizzaId);
        }
        return new PizzaVariant(id, pizzaId, size, price, available, createdAt, updatedAt);
    }

    public long id() {
        return id;
    }

    public long pizzaId() {
        return pizzaId;
    }

    public VariantSize size() {
        return size;
    }

    public Money price() {
        return price;
    }

    /** Si se puede pedir ahora mismo. Un tamaño agotado sigue existiendo: se deja de vender, no se borra. */
    public boolean available() {
        return available;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        return objeto instanceof PizzaVariant otra && id == otra.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "PizzaVariant[" + size + " " + price + (available ? "" : " (no disponible)") + "]";
    }
}