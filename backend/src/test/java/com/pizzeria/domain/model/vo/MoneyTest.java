package com.pizzeria.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del objeto de valor del dinero.
 *
 * <p>La prueba que de verdad importa es la de los decimales. Es la que codifica RN-IM-06 y la que
 * distingue este objeto de un {@code BigDecimal} con un nombre bonito: si alguien cambia
 * {@code RoundingMode.UNNECESSARY} por {@code HALF_UP}, esta prueba falla y el fallo dice exactamente qué
 * regla de negocio se ha roto.
 *
 * <p>Sin Spring: el dominio no lo necesita para nada y una prueba que levanta un contexto para comprobar
 * una resta no está probando el dominio.
 */
class MoneyTest {

    @Nested
    @DisplayName("Construccion")
    class Construccion {

        @Test
        @DisplayName("Un importe entero se guarda con escala 0")
        void unImporteEnteroSeGuardaConEscalaCero() {
            Money dinero = Money.of(12500L);

            assertThat(dinero.importe()).isEqualTo(new BigDecimal("12500"));
            assertThat(dinero.importe().scale()).isZero();
        }

        @Test
        @DisplayName("Un BigDecimal sin decimales se acepta sin tocar su valor")
        void unBigDecimalSinDecimalesSeAcepta() {
            Money dinero = Money.of(new BigDecimal("9900"));

            assertThat(dinero.importe()).isEqualByComparingTo("9900");
            assertThat(dinero.importe().scale()).isZero();
        }

        @Test
        @DisplayName("Cero es un importe valido, no un caso especial")
        void ceroEsUnImporteValido() {
            assertThat(Money.cero().importe()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(Money.cero()).isEqualTo(Money.of(0L));
        }
    }

    @Nested
    @DisplayName("RN-IM-06: un importe fraccionario es un error, no un redondeo")
    class ReglaDeRedondeo {

        @ParameterizedTest(name = "{0} se rechaza en lugar de redondearse")
        @ValueSource(strings = {"12500.50", "0.4", "0.5", "12.505", "0.001"})
        @DisplayName("Rechaza cualquier importe con decimales")
        void rechazaImportesConDecimales(String importe) {
            assertThatThrownBy(() -> Money.of(new BigDecimal(importe)))
                    .isInstanceOf(ArithmeticException.class)
                    .hasMessageContaining("Rounding necessary");
        }

        @Test
        @DisplayName("Rechaza un importe con decimales que ademas es negativo, por el signo")
        void elSignoSeCompruebaAntesQueElRedondeo() {
            // El orden importa en el diagnostico: decir «importe negativo» es mas util que decir
            // «no se puede redondear» cuando lo que pasó es que alguien restó dos precios.
            assertThatThrownBy(() -> Money.of(new BigDecimal("-10.5")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }
    }

    @Nested
    @DisplayName("Invariantes generales de un importe")
    class Invariantes {

        @Test
        @DisplayName("Un importe negativo se rechaza")
        void unImporteNegativoSeRechaza() {
            assertThatThrownBy(() -> Money.of(-1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        @DisplayName("Un importe nulo se rechaza con NullPointerException, no con un error generico")
        void unImporteNuloSeRechaza() {
            assertThatThrownBy(() -> Money.of((BigDecimal) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("esPositivo distingue el cero del precio valido (RN-IM-07 necesita saberlo)")
        void esPositivoDistingueElCero() {
            assertThat(Money.cero().esPositivo()).isFalse();
            assertThat(Money.of(1L).esPositivo()).isTrue();
        }
    }

    @Nested
    @DisplayName("Valor y comparacion")
    class ValorYComparacion {

        @Test
        @DisplayName("Dos importes con el mismo valor son iguales y tienen el mismo hash")
        void igualdadPorValor() {
            Money uno = Money.of(12500L);
            Money otro = Money.of(new BigDecimal("12500"));

            assertThat(uno).isEqualTo(otro).hasSameHashCodeAs(otro);
        }

        @Test
        @DisplayName("Un importe no es igual a un numero cualquiera")
        void noSeComparaConTiposDistintos() {
            assertThat(Money.of(12500L)).isNotEqualTo(12500L);
        }

        @Test
        @DisplayName("compareTo ordena por valor, no por escala ni por representacion")
        void comparaPorValor() {
            assertThat(Money.of(100L)).isLessThan(Money.of(200L));
            assertThat(Money.of(200L)).isGreaterThan(Money.of(100L));
            assertThat(Money.of(100L)).isEqualByComparingTo(Money.of(100L));
        }

        @Test
        @DisplayName("toString dice el importe y la moneda, sin notacion cientifica")
        void imprimeImporteYMoneda() {
            // toPlainString y no toString porque un importe grande en notacion cientifica
            // (1.25E+4) es ilegible en un log y nadie lo comprueba hasta que ocurre.
            assertThat(Money.of(100000000L).toString()).isEqualTo("100000000 COP");
        }

        @Test
        @DisplayName("La moneda del sistema es COP y esta expuesta como constante")
        void laMonedaEsCop() {
            assertThat(Money.MONEDA).isEqualTo("COP");
        }
    }
}