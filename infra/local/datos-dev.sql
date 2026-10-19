-- =====================================================================================
-- Datos de ejemplo para el entorno local · NO son migraciones
--
-- Un `.env` con la base de datos y `psql` son dos cosas distintas: esto es lo segundo.
-- Carga datos de mentira para poder mirar el catálogo a mano.
--
-- NO va dentro de una migración V*: una migración con datos se ejecuta en producción.
-- Ver backend/src/main/resources/db/migration/README.md §3.
--
-- Cómo usarlo:
--     ./scripts/db.ps1 -Accion seed
-- o a mano:
--     docker compose -f infra/local/docker-compose.yml exec -T db \
--         psql -U pizzeria_dev -d pizzeria < infra/local/datos-dev.sql
--
-- Es idempotente: se puede repetir sin duplicar nada (ON CONFLICT DO NOTHING), así que
-- cargar esto encima de una base con datos reales **no** borra lo que haya, sólo añade
-- pizzas de ejemplo. Para empezar de cero, la accion `reset`.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Pizzas
--
-- Ids correlativos (1…6) porque el catálogo es BIGINT desde la migración V2. Se leen
-- mucho mejor que un UUID al probar la API a mano: /api/v1/pizzas/1.
--
-- Nombres elegidos a propósito: el catálogo público ordena por nombre sin distinguir
-- mayúsculas, así que estos salen en este orden y no en el de inserción.
-- -------------------------------------------------------------------------------------

INSERT INTO pizzas (id, name, description, active) VALUES
    (1, 'Margarita',   'Tomate, mozzarella y albahaca',  true),
    -- Activa pero con su única variante agotada: RN-DS-02 dice que NO aparece en el catálogo
    -- público aunque tenga variantes. Es el caso que la regla existe para evitar.
    (2, 'Bufala',      'Mozzarella, tomate y albahaca',    true),
    (3, 'Diavola',     'Picante con pepperoni',           true),
    (4, 'Capricciosa', 'Cuatro ingredientes clásicos',    true),
    -- Activa y SIN ninguna variante: tampoco aparece. Es el otro extremo de RN-DS-02.
    (5, 'Vegetariana', 'Sin carne, con champiñones',     true),
    -- Dada de baja: tampoco aparece en el catálogo público, pero sí en availableOnly=false.
    (6, 'Retirada',    'Fuera de carta',                 false)
ON CONFLICT DO NOTHING;

-- -------------------------------------------------------------------------------------
-- Variantes
--
-- Precios en COP enteros, sin decimales (RN-IM-05). La columna es NUMERIC(12,0), así que
-- un precio con decimales se redondearía EN SILENCIO: por eso no hay ni uno aquí.
-- -------------------------------------------------------------------------------------

INSERT INTO pizza_variants (id, pizza_id, size, price, available) VALUES
    -- Margarita: los tres tamaños, para comprobar el orden de presentación en el catálogo.
    (1, 1, 'SMALL',   9900, true),
    (2, 1, 'MEDIUM', 12500, true),
    (3, 1, 'LARGE',  18000, true),

    -- Capricciosa: el grande agotado. La pizza sigue apareciendo por el mediano.
    (4, 4, 'MEDIUM', 14500, true),
    (5, 4, 'LARGE',  21000, false),

    -- Diavola: sólo mediano.
    (6, 3, 'MEDIUM', 14000, true),

    -- Bufala: única variante NO disponible. Con RN-DS-02 la pizza NO sale en el catálogo
    -- público, aunque esté activa y tenga una variante. Es el caso más fácil de confundir.
    (9, 2, 'MEDIUM', 13500, false),

    -- Vegetariana: NO tiene ninguna variante. Se deja así a propósito para comprobar el
    -- otro extremo de RN-DS-02.
    -- Retirada: variante disponible, pero la pizza está dada de baja.
    (8, 6, 'MEDIUM', 11000, true)
ON CONFLICT DO NOTHING;

-- -------------------------------------------------------------------------------------
-- Lo que hay que ver después de cargarlo
--
--   SELECT id, name FROM pizzas ORDER BY id;
--     -> 1 Margarita, 2 Bufala, 3 Diavola, 4 Capricciosa, 5 Vegetariana, 6 Retirada.
--        Los ids son correlativos desde la migración V2: el catálogo se identifica con
--        números, así que GET /api/v1/pizzas/1 es la Margarita.
--
--   GET /api/v1/pizzas
--     -> 3 pizzas: Capricciosa, Diavola y Margarita, en ese orden.
--        NO salen Bufala (su única variante está agotada) ni Vegetariana (no tiene ninguna)
--        ni Retirada (está dada de baja). Las variantes agotadas tampoco aparecen.
--
--   GET /api/v1/pizzas?availableOnly=false
--     -> las 6, con todas sus variantes, incluidas las agotadas.
--
-- OJO con el ON CONFLICT DO NOTHING: sólo evita duplicados del MISMO id. Si cambias un
-- identificador de este fichero y lo vuelves a cargar sobre una base que ya tiene la
-- versión anterior, las dos versiones se quedan: empezar de cero con
-- `./scripts/db.ps1 -Accion reset -Force`.
-- -------------------------------------------------------------------------------------

SELECT 'pizzas: ' || count(*) AS resultado FROM pizzas
UNION ALL
SELECT 'variantes: ' || count(*) FROM pizza_variants;