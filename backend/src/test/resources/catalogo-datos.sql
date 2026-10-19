-- =====================================================================================
-- Catálogo de pruebas de integración · US-010
--
-- NUNCA va dentro de una migración V*: una migración con datos se ejecuta en producción.
-- Ver db/migration/README.md §3.
--
-- Carga determinista y completa para probar el catálogo público contra PostgreSQL real.
-- El script empieza borrando las dos tablas del catálogo para que cada test parta del
-- mismo punto: sin esto, las filas que inserta un test contaminarían el siguiente.
--
-- La baja de variantes va antes que la de pizzas por la FK RESTRICT de order_items.
-- =====================================================================================

DELETE FROM pizza_variants;
DELETE FROM pizzas;

-- -------------------------------------------------------------------------------------
-- Pizzas
--
-- Nombres pensados para que el orden alfabético NO coincida con el de inserción:
--   orden por lower(name): Bufala, Diavola, Margarita, Retirada
--   orden de inserción:     Retirada, Margarita, Diavola, Bufala
-- Si la consulta no ordenase, el listado devolvería Retirada primero y estas pruebas lo
-- detectarían.
-- -------------------------------------------------------------------------------------

INSERT INTO pizzas (id, name, description, active) VALUES
    (1, 'Margarita', 'Tomate, mozzarella y albahaca', true),
    -- Activa pero su única variante está agotada: RN-DS-02 dice que NO aparece en el catálogo
    -- público aunque esté activa. Es el caso que la regla existe para evitar.
    (2, 'Bufala',   'Mozzarella, tomate y albahaca',   true),
    (3, 'Diavola',   'Picante con pepperoni',          true),
    -- Dada de baja: no aparece con availableOnly=true, pero sí en la vista previa.
    (4, 'Retirada',  'Última de la carta',            false)
ON CONFLICT DO NOTHING;

-- -------------------------------------------------------------------------------------
-- Variantes
--
-- Margarita: los tres tamaños, para comprobar el orden de presentación.
-- Bufala:    una sola variante y no disponible.
-- Diavola:   dos tamaños, el grande agotado.
-- Retirada:  una variante disponible, para probar que el filtro de la pizza inactiva la
--            deja fuera del catálogo público aunque su variante sí esté disponible.
-- -------------------------------------------------------------------------------------

INSERT INTO pizza_variants (id, pizza_id, size, price, available) VALUES
    (1, 1, 'MEDIUM', 12500, true),
    (2, 1, 'SMALL',  9900,  true),
    (3, 1, 'LARGE',  18000, true),
    (4, 2, 'MEDIUM', 15000, false),
    (5, 3, 'MEDIUM', 14000, true),
    (6, 3, 'LARGE',  19500, false),
    (7, 4, 'MEDIUM', 11000, true)
ON CONFLICT DO NOTHING;