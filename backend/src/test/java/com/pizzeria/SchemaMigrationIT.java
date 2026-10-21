package com.pizzeria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que el esquema declarado en las migraciones de Flyway existe de verdad y que sus
 * restricciones se comportan como dice el modelo de datos.
 *
 * <p>Es una prueba de integración ({@code *IT}) y la ejecuta Failsafe en la fase verify.
 * Necesita Docker y un PostgreSQL real: {@code H2} miente sobre tipos numéricos y
 * restricciones, que es exactamente lo que hay que verificar aquí.
 *
 * <p>Esta clase no sustituye al código de negocio: comprueba que la BASE DE DATOS no deja
 * pasar lo que no debe, que es la última línea de defensa cuando el dominio se equivoca.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/seed-datos.sql")
class SchemaMigrationIT {

    @Container
    @ServiceConnection
    // Sin diamante: en Testcontainers 2.0 la clase no es genérica por sí misma,
    // se especializa mediante JdbcDatabaseContainer<PostgreSQLContainer>.
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ---------------------------------------------------------------------------------
    // El esquema se aplica desde cero
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("Flyway aplica el esquema completo sobre una base vacia")
    void elEsquemaSeAplicaDesdeCero() {
        List<String> tablas = jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                order by table_name
                """, String.class);

        assertThat(tablas).containsExactlyInAnyOrder(
                "cash_payments",
                "categories",
                "customer_roles",
                "customers",
                "flyway_schema_history",
                "order_items",
                "order_status_history",
                "orders",
                "pizza_variants",
                "pizzas",
                "products");
    }

    @Test
    @DisplayName("Las migraciones quedan registradas como aplicadas correctamente")
    void lasMigracionesQuedanRegistradas() {
        List<String> historial = jdbc.queryForList("""
                select version || '|' || description || '|' || success
                from flyway_schema_history
                order by installed_rank
                """, String.class);

        // V1 crea el esquema y V2 pasa el catálogo a BIGINT. Las dos, en orden y con éxito: una
        // migración a medias deja el esquema en un estado que nadie pidió.
        assertThat(historial).containsExactly(
                "1|baseline schema|true",
                "2|catalogo con identificadores bigint|true",
                "4|categories and products|true");
    }

    @Test
    @DisplayName("El catalogo se identifica con BIGINT y los pedidos con UUID")
    void elCatalogoUsaBigintYLosPedidosUuid() {
        // Es la diferencia que introduces V2 y la que se puede volver a perder sin querer: que un
        // cambio de tipos se aplique a medio catálogo, o al revés, se ve aquí y no leyendo el código.
        assertThat(tipoColumna("pizzas", "id")).isEqualTo("bigint");
        assertThat(tipoColumna("pizza_variants", "id")).isEqualTo("bigint");
        assertThat(tipoColumna("pizza_variants", "pizza_id")).isEqualTo("bigint");
        assertThat(tipoColumna("order_items", "variant_id")).isEqualTo("bigint");

        // Lo que sigue con UUID no se ha tocado: son datos que nacen en el servidor y nadie teclea.
        assertThat(tipoColumna("orders", "id")).isEqualTo("uuid");
        assertThat(tipoColumna("order_items", "id")).isEqualTo("uuid");
        assertThat(tipoColumna("customers", "id")).isEqualTo("uuid");
    }

    private String tipoColumna(String tabla, String columna) {
        return jdbc.queryForObject("""
                select data_type
                from information_schema.columns
                where table_name = ? and column_name = ?
                """, String.class, tabla, columna);
    }

    @Test
    @DisplayName("No hay tipos ENUM de PostgreSQL: los enumerados son CHECK con literales")
    void noHayTiposEnum() {
        // spring-conventions.md 4.4 lo exige: alterar un ENUM exige ALTER TYPE y bloquea la
        // tabla; un CHECK no tiene nada que alterar.
        Integer enums = jdbc.queryForObject("""
                select count(*)
                from pg_type t
                join pg_namespace n on n.oid = t.typnamespace
                where t.typtype = 'e' and n.nspname = 'public'
                """, Integer.class);

        assertThat(enums).isZero();
    }

    @Test
    @DisplayName("Todas las claves foraneas son RESTRICT: un borrado accidental no arrastra pedidos")
    void todasLasClavesForaneasSonRestrict() {
        List<String> reglas = jdbc.queryForList("""
                select distinct delete_rule
                from information_schema.referential_constraints
                where constraint_schema = 'public'
                """, String.class);

        assertThat(reglas).containsExactly("RESTRICT");
    }

    // ---------------------------------------------------------------------------------
    // Unicidad
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("El email es unico sin distinguir mayusculas")
    void elEmailEsUnicoSinDistinguirMayusculas() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into customers (id, email, password_hash, full_name, role) values (?,?,?,?,?)",
                id, "Lucia@Pizzeria.co", "hash", "Lucia", "CUSTOMER");

        // El indice funcional es UNIQUE(lower(email)): cambiar las mayusculas no abre una cuenta nueva.
        assertThatThrownBy(() -> jdbc.update(
                "insert into customers (id, email, password_hash, full_name, role) values (?,?,?,?,?)",
                UUID.randomUUID(), "lucia@pizzeria.co", "hash", "Otra Lucia", "CUSTOMER"))
                .hasMessageContaining("uq_customers_email_lower");
    }

    @Test
    @DisplayName("Un pedido no puede tener dos cobros: es lo que impide el doble cobro")
    void unPedidoNoPuedeCobrarseDosVeces() {
        UUID pedido = pedidoPendiente("PED-DOBLE", 25000);

        jdbc.update("insert into cash_payments (id, order_id, amount, received_by) values (?,?,?,?)",
                UUID.randomUUID(), pedido, new BigDecimal("25000"), adminId());

        // RN-PG-07 y RN-PG-08. Dos peticiones simultaneas NO pueden insertar dos filas: la
        // segunda recibe 409 sin que el dominio tenga que acertar. Esta es la garantia real.
        assertThatThrownBy(() -> jdbc.update(
                "insert into cash_payments (id, order_id, amount, received_by) values (?,?,?,?)",
                UUID.randomUUID(), pedido, new BigDecimal("25000"), adminId()))
                .hasMessageContaining("uq_cash_payments_order");
    }

    @Test
    @DisplayName("Una pizza no puede tener dos variantes del mismo tamano")
    void unaPizzaNoTieneDosVariantesDelMismoTamano() {
        long pizza = pizza();

        // La primera variante del tamaño es legítima: la restricción no es "una pizza no
        // puede tener variantes", es "no puede tener DOS DEL MISMO TAMAÑO".
        jdbc.update("insert into pizza_variants (id, pizza_id, size, price) values (?,?,?,?)",
                varianteDe(), pizza, "MEDIUM", new BigDecimal("13000"));

        assertThatThrownBy(() -> jdbc.update(
                "insert into pizza_variants (id, pizza_id, size, price) values (?,?,?,?)",
                varianteDe(), pizza, "MEDIUM", new BigDecimal("14000")))
                .hasMessageContaining("uq_pizza_variants_pizza_size");
    }

    // ---------------------------------------------------------------------------------
    // Integridad de importes
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("El precio de una variante es siempre mayor que cero")
    void elPrecioEsMayorQueCero() {
        long pizza = pizza();

        assertThatThrownBy(() -> jdbc.update(
                "insert into pizza_variants (id, pizza_id, size, price) values (?,?,?,?)",
                varianteDe(), pizza, "LARGE", BigDecimal.ZERO))
                .hasMessageContaining("chk_pizza_variants_price");
    }

    @Test
    @DisplayName("La linea guarda la suma exacta de precio por cantidad")
    void laLineaGuardaLaSumaExacta() {
        UUID pedido = pedidoPendiente("PED-SUMA", 25000);

        assertThatThrownBy(() -> jdbc.update("""
                insert into order_items
                    (id, order_id, variant_id, pizza_name, size, unit_price, quantity, line_total)
                values (?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), pedido, variante(), "Margarita", "MEDIUM",
                new BigDecimal("12500"), 2, new BigDecimal("99999")))
                .hasMessageContaining("chk_order_items_line_total");
    }

    @Test
    @DisplayName("Una cantidad fuera del rango 1-20 se rechaza")
    void laCantidadEstaEnRango() {
        UUID pedido = pedidoPendiente("PED-RANGO", 312500);

        assertThatThrownBy(() -> jdbc.update("""
                insert into order_items
                    (id, order_id, variant_id, pizza_name, size, unit_price, quantity, line_total)
                values (?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), pedido, variante(), "Margarita", "MEDIUM",
                new BigDecimal("12500"), 25, new BigDecimal("312500")))
                .hasMessageContaining("chk_order_items_quantity");
    }

    @Test
    @DisplayName("LIMITE CONOCIDO: PostgreSQL redondea al escalar, la proteccion real esta en el dominio")
    void postgresRedondeaAlEscalarYPorEsoLaProteccionEstaEnElDominio() {
        UUID pedido = pedidoPendiente("PED-REDONDEO", 12501);

        // RN-IM-06 dice que un importe fraccionario es un ERROR DE PROGRAMACION y debe
        // rechazarse, nunca redondearse. Este test documenta que la BASE DE DATOS no puede
        // cumplir esa regla: al convertir a NUMERIC(12,0), PostgreSQL redondea en silencio.
        //   12500.50 -> 12501    0.4 -> 0    0.5 -> 1
        //
        // Por eso la garantia NO esta aqui: esta en el objeto de valor Money del dominio, que
        // usa RoundingMode.UNNECESSARY y lanza excepcion ANTES de que el importe llegue a la
        // base (conventions.md §4). Ninguna restricción SQL podría evitarlo.
        //
        // Si alguna vez se llegara a insertar un importe fraccionario, este test lo documenta
        // como un defecto a arreglar en el dominio, no como un redondeo aceptable.
        jdbc.update("""
                insert into order_items
                    (id, order_id, variant_id, pizza_name, size, unit_price, quantity, line_total)
                values (?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), pedido, variante(), "Margarita", "MEDIUM",
                new BigDecimal("12500.50"), 1, new BigDecimal("12501"));

        BigDecimal guardado = jdbc.queryForObject("""
                select unit_price from order_items where order_id = ?
                """, BigDecimal.class, pedido);

        assertThat(guardado).isEqualByComparingTo("12501");
    }

    // ---------------------------------------------------------------------------------
    // Coherencia del total
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("Un pedido cuyo total no sea la suma de sus lineas se rechaza al confirmar")
    void unTotalIncoherenteSeRechaza() {
        UUID pedido = UUID.randomUUID();
        UUID cliente = jdbc.queryForObject(
                "select id from customers where role = 'CUSTOMER' limit 1", UUID.class);

        // Se controla la transacción a mano porque la comprobación ocurre en el COMMIT.
        // JdbcTemplate sin @Transactional hace commit en cada sentencia, así que el trigger
        // no se vería nunca; y con @Transactional sobre el test Spring haría rollback y un
        // trigger diferido no se dispara. Sólo un commit explícito lo prueba de verdad.
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(estado -> {
            jdbc.update("""
                    insert into orders
                        (id, order_number, customer_id, status, payment_status, payment_method, total_amount)
                    values (?,?,?,'PENDING','UNPAID','CASH',?)
                    """, pedido, "PED-INCOHERENTE", cliente, new BigDecimal("99999"));

            jdbc.update("""
                    insert into order_items
                        (id, order_id, variant_id, pizza_name, size, unit_price, quantity, line_total)
                    values (?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), pedido, variante(), "Margarita", "MEDIUM",
                    new BigDecimal("12500"), 2, new BigDecimal("25000"));
        })).hasMessageContaining("la suma de sus lineas es");

        // El fallo se lleva la transaccion ENTERA: no queda un pedido sin lineas colgando.
        Integer pedidos = jdbc.queryForObject(
                "select count(*) from orders where id = ?", Integer.class, pedido);
        assertThat(pedidos).isZero();
    }

    @Test
    @DisplayName("Un pedido coherente se acepta: cabecera y lineas en la misma transaccion")
    void unPedidoCoherenteSeAcepta() {
        UUID pedido = pedidoPendiente("PED-COHERENTE", 25000);

        // Este es el caso que un trigger normal habria roto: la cabecera se inserta con su
        // total y las lineas van despues. Al ser DEFERRABLE, la comprobacion ocurre cuando
        // todo esta escrito y el resultado es correcto.
        jdbc.update("""
                insert into order_items
                    (id, order_id, variant_id, pizza_name, size, unit_price, quantity, line_total)
                values (?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), pedido, variante(), "Margarita", "MEDIUM",
                new BigDecimal("12500"), 2, new BigDecimal("25000"));

        BigDecimal total = jdbc.queryForObject(
                "select total_amount from orders where id = ?", BigDecimal.class, pedido);
        assertThat(total).isEqualByComparingTo("25000");
    }

    // ---------------------------------------------------------------------------------
    // Cohérence entre estado y marcas de tiempo
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("Un pedido DELIVERED exige delivered_at")
    void entregadoExigeMarcaDeTiempo() {
        UUID pedido = pedidoPendiente("PED-SIN-FECHA", 12500);
        jdbc.update("""
                insert into order_items
                    (id, order_id, variant_id, pizza_name, size, unit_price, quantity, line_total)
                values (?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(), pedido, variante(), "Margarita", "MEDIUM",
                new BigDecimal("12500"), 1, new BigDecimal("12500"));

        assertThatThrownBy(() -> jdbc.update(
                "update orders set status = 'DELIVERED' where id = ?", pedido))
                .hasMessageContaining("chk_orders_");

        // El error lo lanza chk_orders_confirmed y no chk_orders_delivered porque
        // PostgreSQL evalua las restricciones en un orden interno que no se controla.
        // El efecto es el correcto: la incoherencia no pasa. El mensaje, no, y queda anotado
        // en la historia US-037, que es quien fija el codigo HTTP de este caso.
    }

    // ---------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------

    private UUID adminId() {
        return jdbc.queryForObject("select id from customers where role = 'ADMIN' limit 1", UUID.class);
    }

    private long variante() {
        // BIGINT desde la migración V2: el catálogo se identifica con números.
        return jdbc.queryForObject("select id from pizza_variants limit 1", Long.class);
    }

    private long pizza() {
        // Nombre único: pizzas.name es único entre las activas, y los tests comparten base.
        // La columna id es BIGINT y NO tiene valor por defecto: el id lo pide la aplicación con
        // `nextval`, que es lo que harían los casos de uso de escritura. El nombre lleva un UUID para
        // que no choque con el de otro test.
        return jdbc.queryForObject(
                "insert into pizzas (id, name) values (nextval('seq_pizzas_id'), ?) returning id",
                Long.class, "Pizza-" + UUID.randomUUID());
    }

    private long varianteDe() {
        return jdbc.queryForObject("select nextval('seq_pizza_variants_id')", Long.class);
    }

    private UUID pedidoPendiente(String numero, long total) {
        return jdbc.queryForObject("""
                insert into orders
                    (id, order_number, customer_id, status, payment_status, payment_method, total_amount)
                values (?,?,?,'PENDING','UNPAID','CASH',?)
                returning id
                """,
                UUID.class, UUID.randomUUID(), numero,
                jdbc.queryForObject("select id from customers where role = 'CUSTOMER' limit 1", UUID.class),
                new BigDecimal(total));
    }
}