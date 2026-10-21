package com.pizzeria.infrastructure.adapter.out.persistence;

import com.pizzeria.domain.model.Pizza;
import com.pizzeria.domain.port.out.PizzaCatalogPort;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del adaptador de catálogo contra PostgreSQL real.
 *
 * <p>Es un {@code *IT}: la ejecuta Failsafe en la fase {@code verify}, porque necesita Docker. Nunca H2,
 * porque H2 miente sobre tipos numéricos, sobre índices parciales y sobre {@code exists} en subconsultas,
 * que es justo lo que hay que verificar aquí.
 *
 * <p>La prueba que justifica esta clase es
 * {@link #elNumeroDeConsultasNoDependeDelTamanoDeLaPagina()}. El catálogo se lee con pizzas y sus
 * variantes, que es el caso clásico del N+1: la versión que parece natural devuelve cada pizza y deja que
 * sus variantes se carguen solas, y con cinco pizzas son cinco consultas. Con datos de verdad son cientos,
 * y el defecto aparece cuando ya nadie recuerda por qué el listado tarda. Aquí se cuenta el número de
 * sentencias y se falla si crece, para que la vuelta atrás no ocurra en silencio.
 *
 * <p>Los datos salen de {@code /catalogo-datos.sql}, que carga un catálogo con los cuatro casos
 * interesantes: pizza con tres tamaños, pizza sin ninguna variante disponible, pizza con una variante
 * agotada y pizza dada de baja.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Sql(scripts = "/catalogo-datos.sql")
class PizzaCatalogAdapterIT {

    /**
     * Sentencias SQL de un listado, ni una más ni una menos: las pizzas de la ventana y sus variantes.
     *
     * <p>El número está escrito y no se calcula a partir del resultado. Una prueba que dijera «no más de
     * N» seguiría pasando si el adaptador volviese a la carga perezosa, porque el total crecería con el
     * número de pizzas. Sólo un número exacto detecta la vuelta atrás.
     */
    private static final long CONSULTAS_DE_UN_LISTADO = 2;

    @Container
    @ServiceConnection
    // Sin diamante: en Testcontainers 2.0 la clase no es genérica por sí misma.
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private PizzaCatalogPort adapter;

    @Autowired
    private SessionFactory sessionFactory;

    // ---------------------------------------------------------------------------------
    // El catálogo público: sólo lo que se puede pedir
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("El catalogo publico excluye la pizza sin ninguna variante disponible (RN-DS-02)")
    void elCatalogoPublicoExcluyeLasPizzasNoPedibles() {
        List<Pizza> pizzas = adapter.listar(20, 0, true);

        // Bufala está activa, pero su única variante está agotada: mostrarla sería anunciar algo que
        // nadie puede pedir.
        assertThat(nombres(pizzas)).containsExactly("Diavola", "Margarita");
    }

    @Test
    @DisplayName("El catalogo publico excluye las pizzas dadas de baja")
    void elCatalogoPublicoExcluyeLasPizzasInactivas() {
        List<Pizza> pizzas = adapter.listar(20, 0, true);

        // Retirada tiene su variante disponible, así que la única razón para excluirla es `active`.
        assertThat(nombres(pizzas)).doesNotContain("Retirada");
    }

    @Test
    @DisplayName("El catalogo publico no expone las variantes agotadas")
    void elCatalogoPublicoNoExponeLasVariantesAgotadas() {
        Pizza diavola = pizzaConNombre(adapter.listar(20, 0, true), "Diavola");

        // Diavola tiene MEDIUM disponible y LARGE agotado. La agotada no aparece, pero la pizza sigue
        // en el catálogo porque tiene algo que sí se puede pedir.
        assertThat(tamaniosDe(diavola)).containsExactly("MEDIUM");
        assertThat(diavola.variants()).allMatch(variante -> variante.available());
    }

    @Test
    @DisplayName("availableOnly=false trae tambien lo inactivo y lo agotado, para la vista previa")
    void availableOnlyFalseTraeTodoElCatalogo() {
        List<Pizza> pizzas = adapter.listar(20, 0, false);

        assertThat(nombres(pizzas)).containsExactly("Bufala", "Diavola", "Margarita", "Retirada");

        Pizza bufala = pizzaConNombre(pizzas, "Bufala");
        assertThat(bufala.variants()).hasSize(1);
        assertThat(bufala.esPedible()).isFalse();
    }

    @Test
    @DisplayName("El total cuenta exactamente lo mismo que devuelve el listado")
    void elTotalCuentaLoMismoQueDevuelve() {
        // Si el filtro del total y el del listado se desincronizasen, el cliente paginaría sobre una
        // cuenta que no corresponde con lo que ve: páginas vacías al principio y contenido repetido
        // al final.
        assertThat(adapter.contar(true)).isEqualTo(adapter.listar(20, 0, true).size()).isEqualTo(2);
        assertThat(adapter.contar(false)).isEqualTo(adapter.listar(20, 0, false).size()).isEqualTo(4);
    }

    // ---------------------------------------------------------------------------------
    // Orden y paginación
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("El listado ordena por nombre sin distinguir mayusculas, no por orden de insercion")
    void elListadoOrdenaPorNombre() {
        // La carga inserta Retirada, Margarita, Diavola y Bufala. Sin `order by lower(name)` el
        // resultado sería Retirada, Margarita, Diavola, Bufala.
        assertThat(nombres(adapter.listar(20, 0, false)))
                .containsExactly("Bufala", "Diavola", "Margarita", "Retirada");
    }

    @Test
    @DisplayName("Las paginas recorren el catalogo sin repetir ni omitir ninguna pizza")
    void lasPaginasRecorrenElCatalogoCompleto() {
        List<String> todas = new ArrayList<>();
        todas.addAll(nombres(adapter.listar(2, 0, false)));
        todas.addAll(nombres(adapter.listar(2, 2, false)));

        assertThat(todas).containsExactly("Bufala", "Diavola", "Margarita", "Retirada");
    }

    @Test
    @DisplayName("Una ventana mas alla del final devuelve una lista vacia, no un error")
    void unaVentanaMasAllaDelFinalDevuelveVacio() {
        assertThat(adapter.listar(20, 200, true)).isEmpty();
    }

    @Test
    @DisplayName("Las variantes de cada pizza vienen de menor a mayor tamano")
    void lasVariantesVienenEnOrdenDeTamano() {
        Pizza margarita = pizzaConNombre(adapter.listar(20, 0, true), "Margarita");

        // SMALL, MEDIUM, LARGE. Ordenado alfabéticamente sería LARGE, MEDIUM, SMALL, que es el orden
        // equivocado en un catálogo: hace que un cliente pida el grande por error.
        assertThat(tamaniosDe(margarita)).containsExactly("SMALL", "MEDIUM", "LARGE");
    }

    // ---------------------------------------------------------------------------------
    // La prueba que justifica esta clase
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("El numero de consultas no depende del numero de pizzas de la pagina")
    void elNumeroDeConsultasNoDependeDelTamanoDeLaPagina() {
        List<Long> conteos = List.of(
                contarConsultas(() -> adapter.listar(1, 0, true)),
                contarConsultas(() -> adapter.listar(3, 0, true)),
                contarConsultas(() -> adapter.listar(4, 0, false)));

        // Dos consultas fijas para cualquier tamaño. Si el adaptador volviese a la carga perezosa, el
        // conteo de la página de cuatro pizzas sería mayor que el de la de una, y esto falla.
        assertThat(conteos).containsOnly(CONSULTAS_DE_UN_LISTADO);
    }

    @Test
    @DisplayName("El detalle de una pizza no consulta una fila por variante")
    void elDetalleNoConsultaUnaFilaPorVariante() {
        long margarita = 1L;

        // Una pizza con tres variantes: la carga perezosa haría cuatro consultas, no dos.
        long consultas = contarConsultas(() -> adapter.buscarPorId(margarita));

        assertThat(consultas).isEqualTo(CONSULTAS_DE_UN_LISTADO);
    }

    // ---------------------------------------------------------------------------------
    // Tipos monetarios y detalle
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("El precio se lee con escala 0, sin decimales inventados")
    void elPrecioSeLeeConEscalaCero() {
        Pizza margarita = pizzaConNombre(adapter.listar(20, 0, true), "Margarita");

        assertThat(margarita.variants().stream().map(variante -> variante.price().importe()).toList())
                .containsExactly(new BigDecimal("9900"), new BigDecimal("12500"), new BigDecimal("18000"));
        assertThat(margarita.variants())
                .allSatisfy(variante -> assertThat(variante.price().importe().scale()).isZero());
    }

    @Test
    @DisplayName("Una pizza inactiva no se encuentra por identificador")
    void unaPizzaInactivaNoSeEncuentra() {
        long retirada = 4L;

        // El mismo vacío que si no existiera: el catálogo público no enseña lo que está dado de baja,
        // así que confirmarlo por el mismo camino que buscarlo sería filtrar información.
        assertThat(adapter.buscarPorId(retirada)).isEmpty();
    }

    @Test
    @DisplayName("Una pizza sin ninguna variante disponible no se encuentra por identificador")
    void unaPizzaNoPedibleNoSeEncuentra() {
        long bufala = 2L;

        assertThat(adapter.buscarPorId(bufala)).isEmpty();
    }

    @Test
    @DisplayName("El detalle de una pizza pedible trae sus variantes con precio y disponibilidad")
    void elDetalleTraeLasVariantes() {
        long margarita = 1L;

        Pizza pizza = adapter.buscarPorId(margarita).orElseThrow();

        assertThat(pizza.name()).isEqualTo("Margarita");
        assertThat(pizza.variants()).hasSize(3);
        assertThat(pizza.esPedible()).isTrue();
    }

    @Test
    @DisplayName("Una pizza que no existe devuelve vacio, no una excepcion")
    void unaPizzaQueNoExisteDevuelveVacio() {
        // Para el puerto un identificador desconocido no es un error, es un resultado. Que se convierta
        // en un 404 es cosa del caso de uso, y se prueba en GetPizzaServiceTest.
        assertThat(adapter.buscarPorId(999L)).isEmpty();
    }

    // ---------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------

    /**
     * Ejecuta la consulta y cuenta cuántas sentencias SQL se han preparado contra la base de datos.
     *
     * <p>Se mide con las estadísticas de Hibernate, que esta clase activa con
     * {@code generate_statistics}, y no con un datasource-proxy: añadir una dependencia para contar
     * consultas que Hibernate ya cuenta sería una abstracción que no responde a ninguna de las tres
     * preguntas de AGENTS.md §3.3.
     *
     * <p>En Hibernate 7 el método se llama {@code getPrepareStatementCount()}: antes era
     * {@code getStatementCount()}. Si algún día vuelve a cambiar de nombre, el fallo de compilación
     * señala el sitio exacto.
     */
    private long contarConsultas(Runnable operacion) {
        Statistics estadisticas = sessionFactory.getStatistics();
        estadisticas.clear();
        operacion.run();
        return estadisticas.getPrepareStatementCount();
    }

    private static List<String> nombres(List<Pizza> pizzas) {
        return pizzas.stream().map(Pizza::name).toList();
    }

    private static List<String> tamaniosDe(Pizza pizza) {
        return pizza.variants().stream().map(variante -> variante.size().name()).toList();
    }

    private static Pizza pizzaConNombre(List<Pizza> pizzas, String nombre) {
        return pizzas.stream()
                .filter(pizza -> pizza.name().equals(nombre))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No se encontro la pizza " + nombre + " entre " + nombres(pizzas)));
    }
}
