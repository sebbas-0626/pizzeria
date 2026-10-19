package com.pizzeria.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del enumerado de tamaños.
 *
 * <p>La que importa es {@code de}: es la conversión entre el literal de la base de datos y el tipo del
 * dominio, y si admite un literal que el {@code chk_pizza_variants_size} rechaza —o al revés— el catálogo
 * y la base discreparían justo en la frontera, con un fallo difícil de rastrear.
 */
class VariantSizeTest {

    @ParameterizedTest(name = "el literal {0} del esquema se convierte en el tamano correcto")
    @CsvSource({"SMALL, SMALL", "MEDIUM, MEDIUM", "LARGE, LARGE", "medium, MEDIUM", " Large , LARGE"})
    @DisplayName("Acepta los literales del esquema, con o sin espacios y sin distinguir mayusculas")
    void convierteLosLiteralesDelEsquema(String literal, VariantSize esperado) {
        assertThat(VariantSize.de(literal)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "el literal {0} no corresponde a ningun tamano del sistema")
    @ValueSource(strings = {"XL", "GRANDE", "SMALLL", "MED", ""})
    @DisplayName("Rechaza un tamano que el esquema no admite")
    void rechazaTamanosQueElEsquemaNoAdmite(String literal) {
        assertThatThrownBy(() -> VariantSize.de(literal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tamaño de variante desconocido");
    }

    @Test
    @DisplayName("Rechaza un tamano nulo en lugar de devolver null")
    void rechazaUnTamanoNulo() {
        // Devolver null seria peor que fallar: el null llegaria hasta el mapper y desde ahi hasta el
        // JSON como un campo sin valor, que es un fallo silencioso.
        assertThatThrownBy(() -> VariantSize.de(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El error de tamano desconocido dice que se esperaba")
    void elErrorDiceQueSeEsperaba() {
        // Sin la lista de valores admitidos, quien lee el error tiene que abrir el esquema de la base.
        assertThatThrownBy(() -> VariantSize.de("XL"))
                .hasMessageContaining("SMALL, MEDIUM, LARGE");
    }

    @Test
    @DisplayName("El orden de presentacion es pequeno, mediano, grande; no alfabetico")
    void elOrdenEsElDePresentacion() {
        assertThat(VariantSize.SMALL.orden()).isLessThan(VariantSize.MEDIUM.orden());
        assertThat(VariantSize.MEDIUM.orden()).isLessThan(VariantSize.LARGE.orden());
    }

    @Test
    @DisplayName("El orden natural es el contrario del alfabetico, que es el motivo de que exista orden()")
    void elOrdenNaturalNoEsElAlfabetico() {
        // Esta prueba documenta POR QUE existe orden(). Si alguien lo simplifica a ordinal() y el
        // enumerado deja de estar declarado de menor a mayor, esta prueba avisa.
        String alfabetico = String.join("", java.util.Arrays.stream(VariantSize.values())
                    .map(Enum::name).sorted().toList());

        assertThat(alfabetico).isEqualTo("LARGEMEDIUMSMALL");
    }
}