package com.pizzeria.application.service;

import com.pizzeria.application.dto.PageRequest;
import com.pizzeria.application.dto.PizzaPage;
import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.port.out.PizzaCatalogPort;
import com.pizzeria.domain.model.PizzaVariant;
import com.pizzeria.domain.model.VariantSize;
import com.pizzeria.domain.model.vo.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas del caso de uso de listado, con doble del puerto.
 *
 * <p>Sin Spring y sin base de datos: este caso de uso orquesta, no consulta. Lo que se comprueba aquí es que
 * traduce una petición de página en una llamada al puerto y devuelve los metadatos correctos; lo que el
 * puerto hace con esos números se prueba contra PostgreSQL real en {@code PizzaCatalogAdapterIT}. Esa
 * separación es la que permite que un fallo diga si el problema está en la orquestación o en la consulta.
 *
 * <p>Un doble y no un {@code @Mock} de todo: si el servicio dependiera de más de un puerto, el doble
 * devolvería silenciosamente una lista vacía y la prueba pasaría sin comprobar nada. Con un solo colaborador
 * verificado, cada llamada esperada está escrita.
 */
@ExtendWith(MockitoExtension.class)
class ListPizzasServiceTest {

    @Mock
    private PizzaCatalogPort puerto;

    // Tipo implementación y no interfaz: @InjectMocks construye lo que encuentra en el campo, y una
    // interfaz no se puede instanciar. La interfaz existe para que la capa web dependa de ella; la
    // prueba necesita el objeto que tiene comportamiento.
    @InjectMocks
    private ListPizzasService servicio;

    @Test
    @DisplayName("Devuelve las pizzas del puerto con los metadatos de la pagina pedida")
    void devuelveLasPizzasDelPuerto() {
        PageRequest paginacion = new PageRequest(0, 20);
        Pizza margarita = pizza("Margarita");
        when(puerto.listar(20, 0, true)).thenReturn(List.of(margarita));
        when(puerto.contar(true)).thenReturn(1L);

        PizzaPage pagina = servicio.listar(paginacion, true);

        assertThat(pagina.pizzas()).containsExactly(margarita);
        assertThat(pagina.pagina()).isZero();
        assertThat(pagina.tamano()).isEqualTo(20);
        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.totalPaginas()).isEqualTo(1);
    }

    @Test
    @DisplayName("Traduce la pagina pedida a limite y desplazamiento para el puerto")
    void traduceLaPaginaALimiteYDesplazamiento() {
        // El contrato con el puerto es limite/desplazamiento, no pagina/tamano. Si el calculo del
        // desplazamiento se rompiese, el listado devolveria siempre la primera pagina y esta prueba
        // seguira pasandose con pagina 0; por eso se verifica con una pagina que no es la primera.
        when(puerto.listar(10, 30, true)).thenReturn(List.of());
        when(puerto.contar(true)).thenReturn(45L);

        PizzaPage pagina = servicio.listar(new PageRequest(3, 10), true);

        verify(puerto).listar(10, 30, true);
        assertThat(pagina.totalPaginas()).isEqualTo(5);
    }

    @Test
    @DisplayName("Cuenta con el mismo filtro con el que lista")
    void cuentaConElMismoFiltroQueLista() {
        // Si contar usara otro filtro, el totalElements describiría un catalogo distinto del que el
        // cliente ve. Es el error más caro de esta pantalla y el más difícil de notar a ojo.
        when(puerto.listar(20, 0, false)).thenReturn(List.of());
        when(puerto.contar(false)).thenReturn(7L);

        PizzaPage pagina = servicio.listar(PageRequest.porDefecto(), false);

        verify(puerto).listar(20, 0, false);
        verify(puerto).contar(false);
        assertThat(pagina.totalElementos()).isEqualTo(7);
    }

    @Test
    @DisplayName("El filtro de disponibilidad viaja intacto hasta el puerto")
    void elFiltroDeDisponibilidadViajaIntacto() {
        when(puerto.listar(anyInt(), anyInt(), eq(true))).thenReturn(List.of());
        when(puerto.contar(true)).thenReturn(0L);

        servicio.listar(PageRequest.porDefecto(), true);

        verify(puerto).contar(true);
        verifyNoMoreInteractions(puerto);
    }

    @Test
    @DisplayName("Un catalogo vacio devuelve una pagina vacia con cero paginas, no un error")
    void unCatalogoVacioDevuelvePaginaVacia() {
        when(puerto.listar(20, 0, true)).thenReturn(List.of());
        when(puerto.contar(true)).thenReturn(0L);

        PizzaPage pagina = servicio.listar(PageRequest.porDefecto(), true);

        assertThat(pagina.pizzas()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        assertThat(pagina.totalPaginas()).isZero();
    }

    @Test
    @DisplayName("Una pagina mas alla del final es una pagina vacia con el total correcto")
    void unaPaginaMasAllaDelFinalNoFalla() {
        when(puerto.listar(20, 200, true)).thenReturn(List.of());
        when(puerto.contar(true)).thenReturn(25L);

        PizzaPage pagina = servicio.listar(new PageRequest(10, 20), true);

        assertThat(pagina.pizzas()).isEmpty();
        assertThat(pagina.totalElementos()).isEqualTo(25);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    // ---- ayudantes de construcción -------------------------------------------------------------

    private static Pizza pizza(String nombre) {
        return Pizza.reconstituir(
                1L, nombre, "descripcion", true, null,
                List.of(PizzaVariant.reconstituir(
                        1L, 1L, VariantSize.MEDIUM,
                        Money.of(12500L), true,
                        Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"))),
                Instant.parse("2026-10-11T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"));
    }
}
