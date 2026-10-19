package com.pizzeria.domain.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * Tamaño de una variante de pizza.
 *
 * <p>Es un tipo cerrado porque el esquema lo restringe: {@code chk_pizza_variants_size} sólo admite
 * {@code SMALL}, {@code MEDIUM} y {@code LARGE}. El enum y la restricción cuentan la misma regla en dos
 * sitios, y por eso sus literales <strong>tienen</strong> que coincidir: si alguien añade un valor aquí y
 * no lo añade en la migración, el dominio aceptaría un tamaño que la base rechazaría.
 *
 * <p>Por eso el nombre del enumerado <strong>es</strong> el valor persistido y no hay tabla de traducción:
 * una copia de la definición de la base en el código es una fuente más que puede divergir.
 */
public enum VariantSize {

    SMALL,
    MEDIUM,
    LARGE;

    /** Tamaño por defecto cuando el cuerpo no lo especifica. */
    public static final VariantSize POR_DEFECTO = MEDIUM;

    /**
     * Posición del tamaño en el orden en que se presenta al cliente: pequeño, mediano, grande.
     *
     * <p>Existe porque el orden natural <strong>no es el alfabético</strong>: ordenado por nombre, el
     * catálogo mostraría LARGE antes que MEDIUM y antes que SMALL. No se puede resolver con un
     * {@code order by} en JPQL sobre el enumerado —ordena por el literal—, así que el orden se aplica en
     * memoria, y para eso hace falta un número.
     *
     * <p>Es el número de declaración del enumerado, del primero al último. Añadir un tamaño nuevo lo
     * coloca automáticamente al final, que es lo correcto mientras los tres sean una progresión de menor a
     * mayor.
     */
    public int orden() {
        return ordinal();
    }

    /**
     * Convierte el literal de la base de datos en el enumerado.
     *
     * <p>Acepta minúsculas porque el dominio no debería ser más estricto que la API: normalizar la
     * entrada es trabajo de la capa que la recibe, no una sorpresa en el núcleo.
     *
     * @throws IllegalArgumentException si el literal no corresponde a ningún tamaño del sistema
     */
    public static VariantSize de(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El tamaño de la variante no puede ser nulo");
        }
        return Arrays.stream(values())
                .filter(tamano -> tamano.name().equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tamaño de variante desconocido: " + valor
                        + ". Admitidos: " + valoresAdmitidos()));
    }

    /**
     * Los tamaños admitidos, para mensajes de error que digan qué se esperaba. Sin este método, un rechazo
     * obliga a quien lee el log a abrir el esquema de la base de datos.
     */
    public static String valoresAdmitidos() {
        return Arrays.stream(values())
                .map(Enum::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("")
                .toUpperCase(Locale.ROOT);
    }
}