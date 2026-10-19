package com.pizzeria.application.service;

import com.pizzeria.domain.exception.DomainException;
import com.pizzeria.domain.exception.ErrorCode;
import com.pizzeria.domain.exception.PizzaNotFoundException;
import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.model.PizzaVariant;
import com.pizzeria.domain.model.VariantSize;
import com.pizzeria.domain.model.vo.Money;
import com.pizzeria.domain.port.out.PizzaCatalogPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas del caso de uso de detalle.
 *
 * <p>Lo que se decide aquí es una sola cosa: qué significa que una pizza no exista. El caso de uso es quien
 * convierte el vacío del puerto en una excepción de dominio, y esa excepción es la que la capa web traduce
 * a 404 con el código {@code CATALOG_PIZZA_NOT_FOUND}. Si este comportamiento se moviera al controlador, el
 * 404 se decidiría en la capa equivocada y el dominio dejaría de saber que le Falta algo.
 */
@ExtendWith(MockitoExtension.class)
class GetPizzaServiceTest {

    @Mock
    private PizzaCatalogPort puerto;

    // El campo es de tipo implementación, no de interfaz: @InjectMocks construye lo que encuentra en
    // el campo, y una interfaz no se puede instanciar. Es la razón por la que la prueba apunta a
    // GetPizzaService y no a GetPizzaUseCase: la interfaz no tiene comportamiento que probar.
    @InjectMocks
    private GetPizzaService servicio;

    @Test
    @DisplayName("Devuelve la pizza que el puerto encuentra")
    void devuelveLaPizzaEncontrada() {
        Pizza margarita = pizza();
        when(puerto.buscarPorId(margarita.id())).thenReturn(Optional.of(margarita));

        assertThat(servicio.obtener(margarita.id())).isSameAs(margarita);

        verifyNoMoreInteractions(puerto);
    }

    @Test
    @DisplayName("Una pizza inexistente produce CATALOG_PIZZA_NOT_FOUND")
    void unaPizzaInexistenteProduceElCodigoDeNoEncontrada() {
        long inexistente = 999L;
        when(puerto.buscarPorId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(inexistente))
                .isInstanceOf(PizzaNotFoundException.class)
                .isInstanceOf(DomainException.class)
                .extracting(excepcion -> ((DomainException) excepcion).code())
                .isEqualTo(ErrorCode.CATALOG_PIZZA_NOT_FOUND);
    }

    @Test
    @DisplayName("El mensaje de la excepcion menciona el identificador pedido")
    void elMensajeMencionaElIdentificador() {
        // Un 404 sin decir qué se buscó es un 404 que obliga a repetir la petición para entenderla.
        long inexistente = 999L;
        when(puerto.buscarPorId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(inexistente))
                .hasMessageContaining(String.valueOf(inexistente));
    }

    private static Pizza pizza() {
        long idPizza = 33L;
        return Pizza.reconstituir(idPizza, "Margarita", "Tomate y mozzarella", true, null,
                List.of(PizzaVariant.reconstituir(
                        44L, idPizza,
                        VariantSize.MEDIUM, Money.of(12500L), true,
                        Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"))),
                Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"));
    }
}