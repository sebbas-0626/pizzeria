package com.pizzeria.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de los límites de paginación, que son contrato público.
 *
 * <p>Los valores verificados aquí aparecen en {@code api-contract.md} y en las anotaciones del
 * controlador. Si alguien cambia el máximo de 100 a 500 sin avisar, estas pruebas fallan, que es
 * exactamente lo que se busca: un cambio de contrato que no se puede hacer en silencio.
 */
class PageRequestTest {

    @Test
    @DisplayName("La pagina por defecto es la primera, con veinte filas")
    void laPaginaPorDefecto() {
        PageRequest paginacion = PageRequest.porDefecto();

        assertThat(paginacion.pagina()).isEqualTo(0);
        assertThat(paginacion.tamano()).isEqualTo(20);
        assertThat(paginacion.desplazamiento()).isZero();
    }

    @ParameterizedTest(name = "la pagina {0} salta {1} filas")
    @CsvSource({"0, 0", "1, 20", "2, 40", "10, 200", "100, 2000"})
    @DisplayName("El desplazamiento es la pagina por el tamano")
    void elDesplazamientoEsPaginaPorTamano(int pagina, int esperado) {
        assertThat(new PageRequest(pagina, 20).desplazamiento()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("El desplazamiento respeta el tamano pedido, no el de por defecto")
    void elDesplazamientoRespetaElTamanoPedido() {
        // Con tamano 5, la pagina 3 es la fila 15, no la 60. Multiplicar siempre por 20 daria una
        // paginacion que parece funcionar y devuelve filas equivocadas.
        assertThat(new PageRequest(3, 5).desplazamiento()).isEqualTo(15);
    }

    @ParameterizedTest(name = "{0} elementos en paginas de tamano 10 son {1} paginas")
    @CsvSource({"0, 0", "1, 1", "10, 1", "11, 2", "20, 2", "21, 3", "100, 10"})
    @DisplayName("El total de paginas redondea hacia arriba")
    void elTotalDePaginasRedondeaHaciaArriba(long totalElementos, int esperado) {
        assertThat(new PageRequest(0, 10).totalPaginas(totalElementos)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Un catalogo vacio tiene cero paginas, no una")
    void unCatalogoVacioTieneCeroPaginas() {
        // Un totalPages: 1 con content: [] le diria al cliente que hay una pagina mas que ver.
        assertThat(new PageRequest(0, 10).totalPaginas(0)).isZero();
    }

    @ParameterizedTest(name = "una pagina {0} se rechaza")
    @ValueSource(ints = {-1, -100})
    @DisplayName("Una pagina negativa se rechaza")
    void unaPaginaNegativaSeRechaza(int pagina) {
        assertThatThrownBy(() -> new PageRequest(pagina, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pagina");
    }

    @ParameterizedTest(name = "un tamano de {0} se rechaza")
    @ValueSource(ints = {0, -1, 101, 1000})
    @DisplayName("Un tamano fuera de los limites del contrato se rechaza")
    void unTamanoFueraDeLosLimitesSeRechaza(int tamano) {
        assertThatThrownBy(() -> new PageRequest(0, tamano))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tamano de pagina");
    }

    @Test
    @DisplayName("Los limites del contrato se pueden usar como constantes")
    void losLimitesSonAccesibles() {
        // El controlador los usa en sus anotaciones de validacion. Si dejaran de ser publicos,
        // el 1 y el 100 estarian duplicados en dos sitios, que es como se desincroniza un contrato.
        assertThat(PageRequest.TAMANO_MINIMO).isEqualTo(1);
        assertThat(PageRequest.TAMANO_MAXIMO).isEqualTo(100);
        assertThat(PageRequest.TAMANO_POR_DEFECTO).isEqualTo(20);
    }

    @Test
    @DisplayName("Una pagina fuera de rango es valida: se pide una ventana que no existe")
    void unaPaginaFueraDeRangoEsValida() {
        // Devolver un 404 obligaria al cliente a distinguir dos casos indistinguibles desde fuera.
        // Aqui sólo se comprueba el constructor: que la pagina 999 sea valida de construir.
        assertThat(new PageRequest(999, 20).desplazamiento()).isEqualTo(19980);
    }
}