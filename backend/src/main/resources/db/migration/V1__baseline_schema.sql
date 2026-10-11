-- =====================================================================================
-- V1 · Esquema inicial
--
-- ESTA MIGRACIÓN ES INMUTABLE. Una vez aplicada y mergeada no se edita jamás.
-- Para cambiar algo se crea V2, V3… Ver db/migration/README.md
--
-- Fuente de verdad: docs/requirements/requirements-v1.0.md §7 y ADR-0010.
-- Boceto en DBML: docs/architecture/data-model.dbml
--
-- CONVENCIONES APLICADAS (spring-conventions.md §4.4)
--
--   · El identificador UUID lo genera la APLICACIÓN, no la base de datos. Por eso las
--     columnas id NO tienen DEFAULT gen_random_uuid(): el agregado necesita su
--     identificador antes de persistirse (conventions.md §9).
--   · TIMESTAMPTZ en todas las fechas, siempre en UTC.
--   · Importes en COP: NUMERIC(12,0). El peso colombiano no tiene decimales (RN-IM-05).
--     Con escala 0, la igualdad line_total = unit_price * quantity es EXACTA y el motor
--     la usa como verificación de integridad real, no como cortesía.
--   · Los enumerados son CHECK con literales, NO tipos ENUM de PostgreSQL: cambiar un
--     valor no exige ALTER TYPE ni bloquea la tabla.
--   · Las claves foráneas van con RESTRICT. Nunca CASCADE sobre datos de negocio: un
--     borrado accidental no puede arrastrar un pedido.
--   · Índices sólo para las consultas reales, no para todas las columnas.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- customers · clientes registrados
-- -------------------------------------------------------------------------------------
CREATE TABLE customers (
    id              UUID         PRIMARY KEY,
    -- Índice FUNCIONAL en lugar de CITEXT o de un UNIQUE simple: mismo comportamiento
    -- (único sin distinguir mayúsculas) sin añadir una extensión que haya que mantener.
    -- 254 y no 255: un índice btree sobre texto no puede superar ~2704 bytes, y 255
    -- caracteres UTF-8 pueden pasarse. A 255 el índice falla al crearse, en caliente.
    email           VARCHAR(254) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    phone           VARCHAR(30),
    -- Un cliente desactivado conserva su historial de pedidos; no se borra.
    active          BOOLEAN      NOT NULL DEFAULT true,
    -- RN-SE-01: exactamente un rol. Se fuerza en el servidor; nunca llega del cuerpo.
    role            VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_customers_email_not_blank CHECK (length(btrim(email)) > 3),
    CONSTRAINT chk_customers_full_name    CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT chk_customers_role         CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

-- El email único sin distinguir mayúsculas tiene que ser un ÍNDICE FUNCIONAL, no una
-- restricción UNIQUE de tabla: PostgreSQL sólo admite una lista de columnas dentro de
-- UNIQUE y rechaza cualquier expresión (`UNIQUE (lower(email))` es un error de sintaxis).
CREATE UNIQUE INDEX uq_customers_email_lower ON customers (lower(email));

CREATE INDEX ix_customers_active ON customers (active);

-- -------------------------------------------------------------------------------------
-- customer_roles · roles por usuario
--
-- Existe aunque hoy haya un solo rol por cliente (RN-SE-01): deja la puerta abierta a más
-- de uno sin necesitar una migración destructiva más adelante. La clave primaria compuesta
-- es la que garantiza "un rol por cliente" mientras el modelo siga siendo de rol único.
-- -------------------------------------------------------------------------------------
CREATE TABLE customer_roles (
    customer_id  UUID        NOT NULL,
    role         VARCHAR(20) NOT NULL,

    CONSTRAINT pk_customer_roles PRIMARY KEY (customer_id, role),
    CONSTRAINT fk_customer_roles_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_customer_roles_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

-- -------------------------------------------------------------------------------------
-- pizzas · catálogo. Baja lógica, nunca física (RF-CA-09)
-- -------------------------------------------------------------------------------------
CREATE TABLE pizzas (
    id            UUID         PRIMARY KEY,
    -- 80 y no 120: US-011 fija el nombre entre 3 y 80 caracteres.
    name          VARCHAR(80)  NOT NULL,
    description   VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT true,
    -- CLAVE del objeto en el almacenamiento, no una URL.
    -- US-052 prohíbe que el cliente indique una URL arbitraria (evita SSRF) y publica
    -- una URL prefirmada generada por el servidor. Guardar aquí una URL sería el
   -- agujero que esa historia viene a cerrar.
    image_key     VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_pizzas_name_not_blank CHECK (length(btrim(name)) > 0)
);

-- RN-CA-08: el nombre es único entre pizzas ACTIVAS. Un índice funcional con predicado
-- sólo cubre las activas, de modo que reutilizar el nombre de una pizza dada de baja sí
-- se permite y no choca con el histórico.
CREATE UNIQUE INDEX uq_pizzas_name_active ON pizzas (lower(name)) WHERE active;

-- -------------------------------------------------------------------------------------
-- pizza_variants · tamaño, precio y disponibilidad
-- -------------------------------------------------------------------------------------
CREATE TABLE pizza_variants (
    id            UUID          PRIMARY KEY,
    pizza_id      UUID          NOT NULL,
    size          VARCHAR(10)   NOT NULL,
    price         NUMERIC(12,0) NOT NULL,
    available     BOOLEAN       NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_pizza_variants_pizza FOREIGN KEY (pizza_id)
        REFERENCES pizzas (id) ON DELETE RESTRICT,
    -- Una pizza no puede tener dos variantes del mismo tamaño.
    CONSTRAINT uq_pizza_variants_pizza_size UNIQUE (pizza_id, size),
    CONSTRAINT chk_pizza_variants_size  CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE')),
    -- RN-IM-07: el precio es siempre mayor que cero. Con NUMERIC(12,0) el 0 es
    -- representable, así que la comprobación hace falta.
    CONSTRAINT chk_pizza_variants_price CHECK (price > 0)
);

-- -------------------------------------------------------------------------------------
-- orders · cabecera del pedido
--
-- payment_status y payment_method viven AQUÍ, no en una tabla `payments` aparte: el
-- modelo de datos aprobado (requirements-v1.0.md §7) fija 8 tablas y no incluye
-- `payments`, mientras que cash_payments queda como el REGISTRO del cobro efectivo.
-- Ver decisions-pending.md D15.
-- -------------------------------------------------------------------------------------
CREATE TABLE orders (
    id               UUID          PRIMARY KEY,
    -- order_number es lo que se dice por teléfono. UNIQUE aparte del id porque se busca
    -- por él y no es la clave de negocio. US-030 lo devuelve en la respuesta.
    order_number     VARCHAR(20)   NOT NULL,
    customer_id      UUID          NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    payment_status   VARCHAR(20)   NOT NULL,
    payment_method   VARCHAR(20)   NOT NULL,
    -- RF-PE-02 y RF-PA-01: en el sistema sólo existe la recogida en efectivo.
    fulfillment_type VARCHAR(20)   NOT NULL DEFAULT 'PICKUP',
    total_amount     NUMERIC(12,0) NOT NULL,
    currency         CHAR(3)       NOT NULL DEFAULT 'COP',
    placed_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Marcas de tiempo por estado. Guardan el HECHO (no el "updated_at" genérico), que es
    -- lo que permite después medir cuánto tardó un pedido en confirmarse o entregarse.
    confirmed_at     TIMESTAMPTZ,
    cancelled_at     TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    -- Bloqueo optimista: @Version en la entidad JPA. No se sube el nivel de aislamiento
    -- para resolver la concurrencia; el esquema la resuelve.
    version          BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_orders_status          CHECK (status IN ('PENDING', 'CONFIRMED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_orders_payment_status  CHECK (payment_status IN ('UNPAID', 'PAID')),
    CONSTRAINT chk_orders_payment_method  CHECK (payment_method = 'CASH'),
    CONSTRAINT chk_orders_fulfillment     CHECK (fulfillment_type = 'PICKUP'),
    CONSTRAINT chk_orders_currency        CHECK (currency = 'COP'),
    -- Un total negativo sería un pedido a devolver dinero. Se rechaza en la base.
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0),
    -- Coherencia entre las marcas de tiempo y el estado actual. Si algo la rompe, el
    -- error tiene que aparecer al escribir, no tres meses después en un informe.
    CONSTRAINT chk_orders_confirmed CHECK (status NOT IN ('CONFIRMED', 'DELIVERED') OR confirmed_at IS NOT NULL),
    CONSTRAINT chk_orders_delivered CHECK (status <> 'DELIVERED' OR delivered_at IS NOT NULL),
    CONSTRAINT chk_orders_cancelled CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

-- Índices para las consultas REALES, no para todas las columnas:
--   · «mis pedidos», paginado y por fecha descendente
--   · «pedidos por estado» de la pantalla del administrador, del más antiguo primero
CREATE INDEX ix_orders_customer_placed ON orders (customer_id, placed_at DESC);
CREATE INDEX ix_orders_status_placed   ON orders (status, placed_at);

-- -------------------------------------------------------------------------------------
-- order_items · líneas con FOTO HISTÓRICA (RN-FH)
--
-- pizza_name, size y unit_price son una COPIA del catálogo en el momento del pedido.
-- RN-FH-02: cambiar el precio o desactivar la variante NO debe alterar pedidos ya
-- creados, así que aquí no se lee el catálogo: se guarda el snapshot.
--
-- La FK a pizza_variants es RESTRICT y no CASCADE: el catálogo no se borra nunca
-- (RF-CA-09) y, aunque se quisiera, el histórico manda.
-- -------------------------------------------------------------------------------------
CREATE TABLE order_items (
    id           UUID          PRIMARY KEY,
    order_id     UUID          NOT NULL,
    variant_id   UUID          NOT NULL,
    pizza_name   VARCHAR(80)   NOT NULL,
    size         VARCHAR(10)   NOT NULL,
    unit_price   NUMERIC(12,0) NOT NULL,
    quantity     INTEGER       NOT NULL,
    line_total   NUMERIC(12,0) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id)
        REFERENCES pizza_variants (id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_size     CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE')),
    -- RN-IM-07 y RN-DS-04: precio positivo y entre 1 y 20 unidades por línea.
    CONSTRAINT chk_order_items_price    CHECK (unit_price > 0),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0 AND quantity <= 20),
    -- RN-IM-03 y RN-IM-05: la suma de las líneas ES el total. Con NUMERIC(12,0) y sin
    -- decimales esta igualdad es exacta, así que el motor la usa de verdad.
    CONSTRAINT chk_order_items_line_total CHECK (line_total = unit_price * quantity)
);

CREATE INDEX ix_order_items_order   ON order_items (order_id);
CREATE INDEX ix_order_items_variant ON order_items (variant_id);

-- -------------------------------------------------------------------------------------
-- cash_payments · cobro en efectivo
--
-- UNIQUE (order_id) es la restricción que IMPIDE EL DOBLE COBRO (RN-PG-07, RN-PG-08).
-- No es una ayuda: es la garantía. Dos peticiones simultáneas a complete-pickup no pueden
-- insertar dos filas, y la segunda recibe 409 sin que el dominio tenga que acertar.
-- -------------------------------------------------------------------------------------
CREATE TABLE cash_payments (
    id           UUID          PRIMARY KEY,
    order_id     UUID          NOT NULL,
    -- RN-PG-02: el importe cobrado es exactamente el total del pedido.
    amount       NUMERIC(12,0) NOT NULL,
    -- RF-PA-05: el administrador autenticado. Nunca un valor del cuerpo de la petición.
    received_by  UUID          NOT NULL,
    paid_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_cash_payments_order UNIQUE (order_id),
    CONSTRAINT fk_cash_payments_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    -- El cobrador es un administrador, que es un cliente con rol ADMIN.
    CONSTRAINT fk_cash_payments_receiver FOREIGN KEY (received_by)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_cash_payments_amount CHECK (amount > 0)
);

-- -------------------------------------------------------------------------------------
-- order_status_history · auditoría de transiciones (RN-ES-06)
--
-- Inmutable por diseño: no hay endpoint de escritura (RN-FH-03). El historial se escribe
-- dentro de la misma transacción que el cambio de estado, o no se escribe.
-- -------------------------------------------------------------------------------------
CREATE TABLE order_status_history (
    id          UUID        PRIMARY KEY,
    order_id    UUID        NOT NULL,
    -- NULL en la primera entrada: el pedido nace desde la nada, no desde un estado previo.
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    -- Cliente o administrador que cambió el estado.
    changed_by  UUID        NOT NULL,
    reason      VARCHAR(300),
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_status_history_actor FOREIGN KEY (changed_by)
        REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_status_history_from CHECK (
        from_status IS NULL OR from_status IN ('PENDING', 'CONFIRMED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_order_status_history_to CHECK (
        to_status IN ('PENDING', 'CONFIRMED', 'DELIVERED', 'CANCELLED'))
);

-- Índice compuesto: se lee el historial de un pedido ORDENADO por fecha (US-032, US-038).
CREATE INDEX ix_order_status_history_order_changed
    ON order_status_history (order_id, changed_at);

-- =====================================================================================
-- Coherencia de orders.total_amount con la suma de sus líneas (RN-IM-03)
--
-- POR QUÉ NO ES UN CHECK
--
-- La regla «el total es la suma exacta de sus líneas» parece un CHECK:
--     CHECK (total_amount = (SELECT sum(line_total) FROM order_items WHERE order_id = ...))
-- PostgreSQL no lo permite: un CHECK no admite subconsultas ni referencia otras tablas.
--
-- POR QUÉ NO ES UN TRIGGER NORMAL
--
-- Un trigger AFTER de fila sobre order_items fallaría al CREAR el pedido. En la misma
-- transacción se inserta primero la cabecera (con su total) y después las líneas, así que
-- tras la primera línea la suma todavía no cuadra y la comprobación se dispararía antes de
-- tiempo, sobre un pedido perfectamente válido.
--
-- LA SOLUCIÓN: constraint trigger DEFERRABLE INITIALLY DEFERRED
--
-- Se ancla al COMMIT, no a cada fila. Cuando se ejecuta, todas las líneas del pedido ya
-- están escritas y el total es el definitivo: se compara el estado final con el final, que
-- es exactamente lo que la regla significa.
--
-- POR QUÉ LANZA EXCEPCIÓN Y NO REESCRIBE
--
-- Podría recalcular y sobrescribir orders.total_amount. No se hace a propósito: si el
-- dominio calculara un total equivocado, una base que lo «arregla» esconde el bug en lugar
-- de hacerlo visible. El servidor calcula (RN-IM-01) y la base se limita a no dejar pasar una
-- incoherencia. Es una defensa, no un corrector.
-- =====================================================================================

CREATE OR REPLACE FUNCTION verificar_total_pedido() RETURNS TRIGGER AS $$
DECLARE
    id_pedido UUID;
    total    NUMERIC(12,0);
    suma     NUMERIC(12,0);
BEGIN
    -- INSERT trae NEW y DELETE trae OLD: hay que cubrir la cabecera en los dos casos.
    id_pedido := COALESCE(NEW.order_id, OLD.order_id);

    SELECT o.total_amount INTO total
    FROM orders o
    WHERE o.id = id_pedido;

    SELECT COALESCE(SUM(oi.line_total), 0) INTO suma
    FROM order_items oi
    WHERE oi.order_id = id_pedido;

    IF total IS DISTINCT FROM suma THEN
        RAISE EXCEPTION
            'El total del pedido % es % pero la suma de sus lineas es %',
            id_pedido, total, suma
            USING ERRCODE = 'check_violation';
    END IF;

    -- RETURN NULL: es un AFTER trigger, el valor de retorno no se usa.
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_verificar_total_pedido
    AFTER INSERT OR UPDATE OR DELETE ON order_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION verificar_total_pedido();