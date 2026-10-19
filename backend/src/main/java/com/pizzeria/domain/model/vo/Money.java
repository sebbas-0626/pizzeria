package com.pizzeria.domain.model.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Importe monetario del sistema, en pesos colombianos.
 *
 * <p>Existe para que <strong>ningún importe entre en el dominio como {@code BigDecimal} suelto</strong>. Un
 * {@code BigDecimal} no sabe si es un peso o un céntimo ni con qué regla se ha redondeado; este objeto sí, y
 * por eso se puede aplicar RN-IM-06 en un solo sitio.
 *
 * <p><strong>Por qué escala 0 y no 2.</strong> El COP no tiene subdivisión decimal en el uso comercial
 * (RN-IM-05). Guardar decimales en un valor que no los tiene no es «precisión extra»: es la puerta por la
 * que un importe fraccionario llega a la base de datos y PostgreSQL lo redondea <em>en silencio</em>. La
 * escala 0 convierte ese fallo invisible en un fallo visible. Ver `db/migration/README.md` §4 bis.
 *
 * <p><strong>Por qué {@link RoundingMode#UNNECESSARY} y no {@code HALF_UP}.</strong> En una moneda sin
 * decimales no existe ninguna regla de negocio que decida cómo redondear: el resultado correcto siempre es
 * un entero. Redondear escondería un error —una división que no debería ocurrir, un precio mal calculado— y
 * convertir el redondeo en excepción lo convierte en un fallo visible, que es exactamente lo que exige
 * RN-IM-06.
 *
 * <p><strong>Por qué no lleva la moneda dentro.</strong> La columna {@code currency} existe en el esquema
 * por si algún día hay más de una, pero está restringida a {@code COP} con un {@code CHECK} y el sistema
 * tiene un solo método de pago (RF-PA-01). Añadir una enumeración de monedas para un valor único sería
 * abstraer una decisión que todavía no existe (AGENTS.md §3.3). El día que haya una segunda moneda, esta
 * clase cambia.
 *
 * <p>Es inmutable y compara por valor: dos importes con el mismo número de {@code BigDecimal} son iguales.
 */
public final class Money implements Comparable<Money> {

    /** Moneda del sistema. Constante y no campo: es única e inmutable. */
    public static final String MONEDA = "COP";

    /**
     * Escala del importe. Constante con nombre y no un {@code 0} suelto: aparece en la comparación y en la
     * construcción, y un número mágico en el dominio es un número que alguien va a cambiar sin permiso.
     */
    private static final int ESCALA = 0;

    private static final Money CERO = new Money(BigDecimal.ZERO.setScale(ESCALA));

    private final BigDecimal importe;

    private Money(BigDecimal importe) {
        this.importe = importe;
    }

    /**
     * Cero en la moneda del sistema.
     *
     * <p>Existe porque el cero es un valor legítimo (un total a 0 es representable) y repetir
     * {@code Money.of(BigDecimal.ZERO)} en varios sitios crea objetos iguales en lugar de uno.
     */
    public static Money cero() {
        return CERO;
    }

    /**
     * Construye un importe desde su valor exacto.
     *
     * <p><strong>Falla si el valor trae decimales.</strong> No es una validación defensiva: es la
     * implementación de RN-IM-06. Un importe fraccionario significa que alguien ha calculado mal antes de
     * llegar aquí, y la respuesta correcta es detenerse, no guardar un número redondeado en silencio.
     *
     * @param importe valor exacto, sin redondear
     * @throws NullPointerException     si el importe es {@code null}
     * @throws IllegalArgumentException si el importe es negativo
     * @throws ArithmeticException      si el importe tiene decimales no nulos (RN-IM-06)
     */
    public static Money of(BigDecimal importe) {
        Objects.requireNonNull(importe, "El importe no puede ser nulo");
        if (importe.signum() < 0) {
            throw new IllegalArgumentException("Un importe no puede ser negativo: " + importe);
        }
        return new Money(importe.setScale(ESCALA, RoundingMode.UNNECESSARY));
    }

    /**
     * Construye un importe a partir de un entero.
     *
     * <p>Atajo para los casos en que el valor no tiene decimales por construcción —los precios de las
     * pizzas, por ejemplo— y la escala se fija sin riesgo.
     */
    public static Money of(long importe) {
        if (importe < 0) {
            throw new IllegalArgumentException("Un importe no puede ser negativo: " + importe);
        }
        return new Money(BigDecimal.valueOf(importe, ESCALA));
    }

    /**
     * El importe exacto, con la escala del sistema. Se devuelve el valor, no una copia defensiva:
     * {@link BigDecimal} ya es inmutable.
     */
    public BigDecimal importe() {
        return importe;
    }

    /**
     * Si el importe es estrictamente mayor que cero (RN-IM-07). El cero es un importe válido; lo que no es
     * válido es un precio de cero, y es la regla del precio la que decide, no la del dinero.
     */
    public boolean esPositivo() {
        return importe.signum() > 0;
    }

    @Override
    public int compareTo(Money otro) {
        return importe.compareTo(otro.importe);
    }

    @Override
    public boolean equals(Object objeto) {
        if (this == objeto) {
            return true;
        }
        return objeto instanceof Money otro && importe.equals(otro.importe);
    }

    @Override
    public int hashCode() {
        return importe.hashCode();
    }

    @Override
    public String toString() {
        return importe.toPlainString() + " " + MONEDA;
    }
}