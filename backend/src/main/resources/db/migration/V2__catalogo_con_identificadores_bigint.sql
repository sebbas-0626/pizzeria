-- =====================================================================================
-- V2 · Los identificadores del catálogo pasan de UUID a BIGINT
--
-- POR QUÉ
-- El catálogo se lee y se escribe a mano, con curl y con Postman, y un UUID de 36
-- caracteres hace que cada prueba manual sea copiar y pegar un UUID enorme. `GET
-- /api/v1/pizzas/1` se lee; `GET /api/v1/pizzas/a1000000-0000-0000-0000-000000000001`
-- no. Los pedidos, los clientes y los pagos siguen con UUID: son datos que nacen en
-- el servidor y que nadie teclea.
--
-- Sólo cambian `pizzas`, `pizza_variants` y la referencia que hay a la variante en
-- `order_items`. El resto del esquema es idéntico.
--
-- CÓMO
-- Las filas que ya existen NO se pierden: se numeran con `row_number()` y se traducen
-- con una tabla de equivalencia. La clave es que el paso de UUID a BIGINT no tiene
-- `USING`: PostgreSQL no sabe convertir un UUID en un entero, así que hace falta una
-- columna nueva al lado, rellenarla, tirar la vieja y renombrar.
--
-- En la base vacía de los tests y en el laboratorio esto no tiene nada que traducir,
-- pero elLaboratorio tiene 6 pizzas sembradas y esta migración tiene que saber
-- conservarlas: un `DROP COLUMN` a pelo habría sido más corto y habría borrado el
-- catálogo de quien lo estuviera usando.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1 · Equivalencias
--
-- `row_number()` da 1, 2, 3... sin huecos. Las pizzas se numeran por antigüedad (y por
-- nombre para desempatar, porque `now()` es el mismo instante para todas las filas de una
-- misma transacción) y las variantes agrupadas por pizza, para que los números de una
-- pizza queden juntos y se lean como «la 1, la 2, la 3 de la pizza 1».
-- -------------------------------------------------------------------------------------

CREATE TEMP TABLE mapa_pizzas AS
SELECT id AS id_uuid,
       row_number() OVER (ORDER BY created_at, name) AS id_nuevo
FROM pizzas;

CREATE TEMP TABLE mapa_variantes AS
SELECT pv.id         AS id_uuid,
       pv.pizza_id   AS pizza_uuid,
       mp.id_nuevo   AS pizza_nuevo,
       row_number() OVER (ORDER BY mp.id_nuevo, pv.created_at, pv.size) AS id_nuevo
FROM pizza_variants pv
JOIN mapa_pizzas mp ON mp.id_uuid = pv.pizza_id;

-- -------------------------------------------------------------------------------------
-- 2 · Columnas nuevas y relleno
--
-- Se crean SIN `NOT NULL` porque `order_items.variant_id` es `NOT NULL` y esta tabla
-- puede estar vacía: no se puede añadir una columna `NOT NULL` sin valor por defecto a
-- una tabla con filas. El `NOT NULL` se pone en el paso 5.
-- -------------------------------------------------------------------------------------

ALTER TABLE pizzas         ADD COLUMN id_nuevo         BIGINT;
ALTER TABLE pizza_variants ADD COLUMN id_nuevo         BIGINT;
ALTER TABLE pizza_variants ADD COLUMN pizza_id_nuevo   BIGINT;
ALTER TABLE order_items    ADD COLUMN variant_id_nuevo BIGINT;

UPDATE pizzas p
   SET id_nuevo = m.id_nuevo
  FROM mapa_pizzas m
 WHERE m.id_uuid = p.id;

UPDATE pizza_variants pv
   SET id_nuevo       = m.id_nuevo,
       pizza_id_nuevo = m.pizza_nuevo
  FROM mapa_variantes m
 WHERE m.id_uuid = pv.id;

UPDATE order_items oi
   SET variant_id_nuevo = m.id_nuevo
  FROM mapa_variantes m
 WHERE m.id_uuid = oi.variant_id;

-- -------------------------------------------------------------------------------------
-- 3 · Fuera las claves que hablan de las columnas viejas
--
-- El orden importa: una clave foránea no puede seguir apuntando a una columna que va a
-- desaparecer, y la clave primaria no se puede tirar con las claves foráneas encima.
-- `uq_pizza_variants_pizza_size` es UNIQUE sobre (pizza_id, size), así que también se
-- va con su columna.
-- -------------------------------------------------------------------------------------

ALTER TABLE order_items    DROP CONSTRAINT fk_order_items_variant;
ALTER TABLE pizza_variants DROP CONSTRAINT fk_pizza_variants_pizza;
ALTER TABLE pizza_variants DROP CONSTRAINT uq_pizza_variants_pizza_size;
ALTER TABLE pizza_variants DROP CONSTRAINT pizza_variants_pkey;
ALTER TABLE pizzas         DROP CONSTRAINT pizzas_pkey;

-- -------------------------------------------------------------------------------------
-- 4 · Las columnas viejas fuera, las nuevas con su nombre
--
-- Tirar `order_items.variant_id` se lleva por delante `ix_order_items_variant`, que está
-- sobre esa columna: se vuelve a crear en el paso 6.
-- -------------------------------------------------------------------------------------

ALTER TABLE pizzas         DROP COLUMN id;
ALTER TABLE pizza_variants DROP COLUMN id;
ALTER TABLE pizza_variants DROP COLUMN pizza_id;
ALTER TABLE order_items    DROP COLUMN variant_id;

ALTER TABLE pizzas         RENAME COLUMN id_nuevo TO id;
ALTER TABLE pizza_variants RENAME COLUMN id_nuevo TO id;
ALTER TABLE pizza_variants RENAME COLUMN pizza_id_nuevo TO pizza_id;
ALTER TABLE order_items    RENAME COLUMN variant_id_nuevo TO variant_id;

-- -------------------------------------------------------------------------------------
-- 5 · Las mismas restricciones que tenía V1, ahora sobre BIGINT
-- -------------------------------------------------------------------------------------

ALTER TABLE pizzas         ALTER COLUMN id SET NOT NULL;
ALTER TABLE pizza_variants ALTER COLUMN id SET NOT NULL;
ALTER TABLE pizza_variants ALTER COLUMN pizza_id SET NOT NULL;
ALTER TABLE order_items    ALTER COLUMN variant_id SET NOT NULL;

ALTER TABLE pizzas         ADD CONSTRAINT pizzas_pkey PRIMARY KEY (id);
ALTER TABLE pizza_variants ADD CONSTRAINT pizza_variants_pkey PRIMARY KEY (id);
ALTER TABLE pizza_variants ADD CONSTRAINT fk_pizza_variants_pizza FOREIGN KEY (pizza_id)
    REFERENCES pizzas (id) ON DELETE RESTRICT;
ALTER TABLE pizza_variants ADD CONSTRAINT uq_pizza_variants_pizza_size UNIQUE (pizza_id, size);
ALTER TABLE order_items    ADD CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id)
    REFERENCES pizza_variants (id) ON DELETE RESTRICT;

-- -------------------------------------------------------------------------------------
-- 6 · Índice que se había perdido y secuencias para los ids nuevos
--
-- SECUENCIA y no `GENERATED BY DEFAULT AS IDENTITY` a propósito. El diseño del
-- proyecto es que el id lo genera la APLICACIÓN y existe ANTES de insertar (ver
-- `db/migration/README.md` §4), y eso es justo lo que un agregado con id en el
-- constructor necesita: la pizza existe como objeto antes de tocar la base. Con
-- IDENTITY, el id lo pondría PostgreSQL al insertar y habría que crear la pizza sin él.
-- Con `nextval` en la aplicación, la secuencia es de la base pero la que la consulta
-- es el código.
--
-- La secuencia empieza en el `max(id)` + 1 que han dejado las filas existentes, para
-- que el siguiente `nextval` no choque con ninguna.
-- -------------------------------------------------------------------------------------

CREATE INDEX ix_order_items_variant ON order_items (variant_id);

CREATE SEQUENCE seq_pizzas_id;
CREATE SEQUENCE seq_pizza_variants_id;

SELECT setval('seq_pizzas_id',
              GREATEST(COALESCE((SELECT max(id) FROM pizzas), 0), 0) + 1,
              false);
SELECT setval('seq_pizza_variants_id',
              GREATEST(COALESCE((SELECT max(id) FROM pizza_variants), 0), 0) + 1,
              false);

-- -------------------------------------------------------------------------------------
-- Lo que se puede comprobar después de esta migración
--
--   \d pizzas
--     id | bigint | generated by default as identity | not null
--
--   SELECT id, name FROM pizzas ORDER BY id;
--     -> 1..N, y las que había antes con UUID siguen ahí, renumeradas.
-- -------------------------------------------------------------------------------------