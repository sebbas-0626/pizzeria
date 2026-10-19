package com.pizzeria.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Pizza del catálogo, con sus variantes.
 *
 * <p><strong>La baja es lógica y no física (RF-CA-09).</strong> Una pizza dada de baja conserva su
 * historial, y sus variantes siguen siendo legibles dentro de un pedido antiguo. Por eso {@code active} es
 * una propiedad y no una ausencia: desactivada sigue siendo una pizza, y por eso {@code id} es su
 * identidad y no su nombre, que puede reutilizarse una vez dado de baja (índice único parcial sobre pizzas
 * activas).
 *
 * <p>Las variantes llegan ya cargadas en la lista. No es una decisión de comodidad: el catálogo se lee con
 * pizzas y sus variantes, y si el adaptador devolviera la pizza sin ellas, cada listado haría una consulta
 * por pizza —el N+1 clásico del sprint—. El adaptador se encarga de traerlas en un número fijo de
 * consultas; ver {@code PizzaCatalogAdapter}.
 *
 * <p>La lista es <strong>defensa e inmutable</strong>: se copia en la construcción. Una lista mutable
 * compartida con el adaptador permitiría que una consulta posterior cambiara el resultado de una anterior.
 *
 * <p>US-010 es la historia de lectura. Los métodos que construyen y modifican pizzas llegan con US-011, que
 * es quien tiene los criterios de aceptación y los códigos de error de la escritura. Añadirlos aquí sería
 * escribir un contrato de escritura que nadie ha pedido.
 */
public final class Pizza {

    /** Longitud mínima del nombre. Es la misma que valida la API en US-011 y la que comprueba la base. */
    public static final int NOMBRE_LONGITUD_MINIMA = 3;

    /** Longitud máxima del nombre, fijada por el esquema: {@code VARCHAR(80)}. */
    public static final int NOMBRE_LONGITUD_MAXIMA = 80;

    private final long id;
    private final String name;
    private final String description;
    private final boolean active;
    private final String imageKey;
    private final List<PizzaVariant> variants;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Pizza(long id,
                  String name,
                  String description,
                  boolean active,
                  String imageKey,
                  List<PizzaVariant> variants,
                  Instant createdAt,
                  Instant updatedAt) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "El nombre de la pizza es obligatorio");
        this.description = description;
        this.active = active;
        this.imageKey = imageKey;
        this.variants = List.copyOf(variants);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Reconstruye una pizza a partir de lo que hay persistido, con sus variantes ya cargadas.
     *
     * <p>La lista puede venir vacía: una pizza recién creada no tiene ninguna, y una pizza a la que se le
     * retiraron todas sigue existiendo. Que la lista esté vacía no es un error de este método; lo es que no
     * haya ninguna variante disponible, y eso lo decide el catálogo público (RN-DS-02), no el agregado.
     *
     * <p><strong>El identificador tiene que ser un número positivo.</strong> Con UUID era imposible que
     * valiera cero; con BIGINT sí, y un {@code 0} en el id significaría que la pizza se guardó sin
     * identificador. Se comprueba aquí y no en la base porque es un error de construcción del agregado, no
     * un dato que venga sucio de fuera.
     */
    public static Pizza reconstituir(long id,
                                     String name,
                                     String description,
                                     boolean active,
                                     String imageKey,
                                     List<PizzaVariant> variants,
                                     Instant createdAt,
                                     Instant updatedAt) {
        if (id <= 0) {
            throw new IllegalArgumentException("El identificador de la pizza tiene que ser un numero "
                    + "positivo: " + id);
        }
        return new Pizza(id, name, description, active, imageKey, variants, createdAt, updatedAt);
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Descripción opcional. Es {@code null} si la pizza no tiene, nunca una cadena vacía fingiendo que sí. */
    public String description() {
        return description;
    }

    /** Si la pizza se ofrece en el catálogo. Una pizza dada de baja no se borra: se deja de vender. */
    public boolean active() {
        return active;
    }

    /**
     * Clave del objeto de la imagen en el almacenamiento, no una URL.
     *
     * <p>US-050…US-052 son las historias que lo usan. Se conserva aquí porque es parte de la identidad de la
     * fila y no porque hoy se exponga: publicar una URL es una decisión de US-052, que además la prohíbe
     * como dato de entrada del cliente (SSRF).
     */
    public String imageKey() {
        return imageKey;
    }

    /** Todas las variantes, disponibles o no. Es una copia inmutable. */
    public List<PizzaVariant> variants() {
        return variants;
    }

    /**
     * Sólo las variantes que se pueden pedir ahora mismo.
     *
     * <p>Es lo que devuelve el catálogo público. Separar este método de {@link #variants()} evita que cada
     * llamante tenga que acordarse del filtro, y que se olvide en uno de ellos.
     */
    public List<PizzaVariant> variantesDisponibles() {
        return variants.stream()
                .filter(PizzaVariant::available)
                .toList();
    }

    /**
     * Si la pizza puede aparecer en el catálogo público.
     *
     * <p>RN-DS-02: una pizza sin ninguna variante disponible no aparece, aunque esté activa. Mostraría una
     * entrada que nadie puede pedir, que es la peor forma de catálogo: aparente pero inútil.
     */
    public boolean esPedible() {
        return active && !variantesDisponibles().isEmpty();
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
        return objeto instanceof Pizza otra && id == otra.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Pizza[" + name + (active ? "" : " (inactiva)") + ", " + variants.size() + " variantes]";
    }
}