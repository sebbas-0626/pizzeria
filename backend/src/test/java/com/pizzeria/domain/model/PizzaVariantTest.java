package com.pizzeria.domain.model;

import com.pizzeria.domain.model.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * La variante: su identidad, su precio y a qué pizza pertenece.
 *
 * <p>Existe como fichero propio porque desde la migración V2 su identificador es un
 * <strong>número</strong>, y con un número hay invariantes que con un UUID no se podían comprobar: un
 * identificador de cero o negativo significa una variante guardada sin identificador, o una variante
 * colgando de una pizza que no existe. Con UUID, un id de ceros era un id perfectamente válido y el
 * error se habría escondido hasta que alguien lo leyera en una línea de pedido.
 */
class PizzaVariantTest {

    private static final Instant INSTANTE = Instant.parse("2026-10-11T10:00:00Z");

    @Test
    @DisplayName("Una variante guardada se reconstituye tal cual")
    void seReconstituyeTalCual() {
        PizzaVariant variante = PizzaVariant.reconstituir(
                7L, 1L, VariantSize.LARGE, Money.of(18000L), false, INSTANTE, INSTANTE);

        assertThat(variante.id()).isEqualTo(7L);
        assertThat(variante.pizzaId()).isEqualTo(1L);
        assertThat(variante.size()).isEqualTo(VariantSize.LARGE);
        assertThat(variante.price()).isEqualTo(Money.of(18000L));
        assertThat(variante.available()).isFalse();
    }

    @Test
    @DisplayName("La identidad de la variante es su identificador, no su tamaño ni su precio")
    void laIdentidadEsElIdentificador() {
        // Mismo tamaño y mismo precio, y sigue siendo otra variante: el precio de una variante cambia
        // sin que deje de ser la misma (RN-FH-02), así que no puede formar parte de la identidad.
        PizzaVariant una = variante(7L, VariantSize.MEDIUM, 12500L);
        PizzaVariant otra = variante(8L, VariantSize.MEDIUM, 12500L);

        assertThat(una).isNotEqualTo(otra);
        assertThat(una).isEqualTo(variante(7L, VariantSize.MEDIUM, 12500L));
        assertThat(una.hashCode()).isEqualTo(variante(7L, VariantSize.MEDIUM, 12500L).hashCode());
    }

    @Test
    @DisplayName("Un identificador que no es positivo no construye una variante")
    void unIdentificadorNoPositivoNoConstruyeUnaVariante() {
        assertThatIllegalArgumentException()
                .as("identificador cero")
                .isThrownBy(() -> PizzaVariant.reconstituir(
                        0L, 1L, VariantSize.MEDIUM, Money.of(12500L), true, INSTANTE, INSTANTE));

        assertThatIllegalArgumentException()
                .as("identificador negativo")
                .isThrownBy(() -> PizzaVariant.reconstituir(
                        -3L, 1L, VariantSize.MEDIUM, Money.of(12500L), true, INSTANTE, INSTANTE));
    }

    @Test
    @DisplayName("Una variante sin pizza no se puede construir")
    void unaVarianteSinPizzaNoSePuedeConstruir() {
        // Una variante suelta no tiene sentido en este catálogo: el precio de una variante sólo significa
        // algo junto a la pizza a la que pertenece. Con UUID, un pizzaId nulo no podia ocurrir porque el
        // constructor lo rechazaba; con long hay que comprobarlo porque 0 no es "ninguno".
        assertThatIllegalArgumentException()
                .as("pizza cero")
                .isThrownBy(() -> PizzaVariant.reconstituir(
                        7L, 0L, VariantSize.MEDIUM, Money.of(12500L), true, INSTANTE, INSTANTE));

        assertThatIllegalArgumentException()
                .as("pizza negativa")
                .isThrownBy(() -> PizzaVariant.reconstituir(
                        7L, -1L, VariantSize.MEDIUM, Money.of(12500L), true, INSTANTE, INSTANTE));
    }

    private static PizzaVariant variante(long id, VariantSize size, long precio) {
        return PizzaVariant.reconstituir(id, 1L, size, Money.of(precio), true, INSTANTE, INSTANTE);
    }
}