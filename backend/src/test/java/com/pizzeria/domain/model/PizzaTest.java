package com.pizzeria.domain.model;

import com.pizzeria.domain.model.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de la pizza del catálogo y su relación con las variantes.
 *
 * <p>El comportamiento que se prueba aquí es {@link Pizza#esPedible()}, que es RN-DS-02 translated a
 * código: una pizza activa sin ninguna variante disponible no puede aparecer en el catálogo público. Es
 * una regla que se puede equivocar de tres maneras (mirar sólo {@code active}, mirar sólo si la lista está
 * vacía, o no mirar nada) y por eso tiene su propia prueba.
 */
class PizzaTest {

    private static final long ID_PIZZA = 33L;

    @Test
    @DisplayName("Una pizza activa con alguna variante disponible se puede pedir")
    void unaPizzaActivaConVariantesSePuedePedir() {
        Pizza pizza = pizza(true, List.of(variante(true), variante(false)));

        assertThat(pizza.esPedible()).isTrue();
        assertThat(pizza.variantesDisponibles()).hasSize(1);
    }

    @Test
    @DisplayName("Una pizza activa sin ninguna variante disponible NO aparece en el catalogo (RN-DS-02)")
    void unaPizzaSinVariantesDisponiblesNoSePuedePedir() {
        // El caso que la regla existe para evitar: una pizza que se anuncia y que nadie puede pedir.
        Pizza pizza = pizza(true, List.of(variante(false), variante(false)));

        assertThat(pizza.esPedible()).isFalse();
        assertThat(pizza.variantesDisponibles()).isEmpty();
    }

    @Test
    @DisplayName("Una pizza activa sin ninguna variante se puede pedir en el Sentido de que no es un error")
    void unaPizzaSinVariantesEsValidaComoObjeto() {
        // Distinta cosa de que se pueda pedir: una pizza recién creada no tiene variantes y existe.
        // El error sería inventar una pizza vacía al vuelo, no construirla.
        Pizza pizza = pizza(true, List.of());

        assertThat(pizza.variants()).isEmpty();
        assertThat(pizza.esPedible()).isFalse();
    }

    @Test
    @DisplayName("Una pizza inactiva no se puede pedir aunque tenga variantes disponibles")
    void unaPizzaInactivaNoSePuedePedir() {
        Pizza pizza = pizza(false, List.of(variante(true)));

        assertThat(pizza.esPedible()).isFalse();
        assertThat(pizza.variantesDisponibles()).hasSize(1);
    }

    @Test
    @DisplayName("La lista de variantes es una copia defensiva, no la lista del adaptador")
    void laListaDeVariantesEsUnaCopia() {
        List<PizzaVariant> original = new ArrayList<>(List.of(variante(true)));
        Pizza pizza = pizza(true, original);

        original.clear();

        assertThat(pizza.variants()).hasSize(1);
    }

    @Test
    @DisplayName("Un precio con decimales hace fallar la construccion de la variante")
    void unPrecioConDecimalesNoSeAdmite() {
        // El importe no llega al dominio como BigDecimal suelto, y por eso un precio fraccionario
        // falla aquí en vez de llegar redondeado a la base.
        assertThatThrownBy(() -> PizzaVariant.reconstituir(
                44L, ID_PIZZA, VariantSize.MEDIUM,
                Money.of(new BigDecimal("12500.50")), true,
                Instant.now(), Instant.now()))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("Una pizza sin variantes tiene su lista vacia y no nula")
    void unaPizzaSinVariantesTieneListaVacia() {
        assertThat(pizza(true, List.of()).variants()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("La identidad de la pizza es su identificador, no su nombre")
    void laIdentidadEsElIdentificador() {
        Pizza una = pizza(true, List.of());
        Pizza otra = Pizza.reconstituir(99L, una.name(), "Otra descripcion",
                false, null, List.of(variante(true)), Instant.now(), Instant.now());

        // Dos pizzas distintas pueden llamarse igual cuando la primera esta dada de baja: por eso el
        // nombre no puede ser la identidad (índice único parcial sobre pizzas activas).
        assertThat(una).isNotEqualTo(otra);
        assertThat(una.name()).isEqualTo(otra.name());
    }

    @Test
    @DisplayName("Un identificador que no es positivo no construye una pizza")
    void unIdentificadorNoPositivoNoConstruyeUnaPizza() {
        // Con UUID esto no se podia comprobar: un UUID de ceros es un UUID valido. Con BIGINT un 0
        // significa "esta pizza se guardo sin identificador", y el fallo tiene que salir al construirla
        // y no tres meses despues en una linea de pedido que apunta a la pizza equivocada.
        assertThatIllegalArgumentException()
                .as("identificador cero")
                .isThrownBy(() -> Pizza.reconstituir(0L, "Margarita", null, true, null,
                        List.of(), Instant.now(), Instant.now()));

        assertThatIllegalArgumentException()
                .as("identificador negativo")
                .isThrownBy(() -> Pizza.reconstituir(-1L, "Margarita", null, true, null,
                        List.of(), Instant.now(), Instant.now()));
    }

    // ---- ayudantes de construcción -------------------------------------------------------------

    private static Pizza pizza(boolean activa, List<PizzaVariant> variantes) {
        return Pizza.reconstituir(ID_PIZZA, "Margarita", "Tomate y mozzarella", activa, null,
                variantes, Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"));
    }

    private static PizzaVariant variante(boolean disponible) {
        return PizzaVariant.reconstituir(
                45L,
                ID_PIZZA, VariantSize.MEDIUM, Money.of(12500L), disponible,
                Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"));
    }
}