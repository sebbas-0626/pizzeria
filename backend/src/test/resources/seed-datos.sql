-- Datos mínimos para las pruebas de integración del esquema.
--
-- NUNCA va dentro de una migración V*: una migración con datos se ejecuta en producción.
-- Ver db/migration/README.md §3.
--
-- Todo con ON CONFLICT DO NOTHING: @Sql se ejecuta antes de cada test y los tests
-- insertan filas propias que no deben chocar con estas.
--
-- El catálogo usa BIGINT desde la migración V2, así que sus ids son 1 y 2. Clientes y
-- pedidos (que no aparecen aquí) siguen con UUID: son datos que nacen en el servidor.

INSERT INTO customers (id, email, password_hash, full_name, role) VALUES
    ('11111111-1111-1111-1111-111111111111', 'admin@pizzeria.test',   'hash', 'Administrador', 'ADMIN'),
    ('22222222-2222-2222-2222-222222222222', 'cliente@pizzeria.test', 'hash', 'Lucia',        'CUSTOMER')
ON CONFLICT DO NOTHING;

INSERT INTO pizzas (id, name) VALUES
    (1, 'Margarita')
ON CONFLICT DO NOTHING;

INSERT INTO pizza_variants (id, pizza_id, size, price) VALUES
    (2, 1, 'MEDIUM', 12500)
ON CONFLICT DO NOTHING;

-- La secuencia de ids tiene que quedar por encima de lo sembrado aquí: si el siguiente
-- nextval devolviera 1, la pizza que insertase un test de escritura chocaría con la de
-- arriba. El valor se recalcula en cada carga, así que sigue siendo correcto aunque este
-- fichero cambie.
SELECT setval('seq_pizzas_id', GREATEST((SELECT max(id) FROM pizzas), 1) + 1, false);
SELECT setval('seq_pizza_variants_id', GREATEST((SELECT max(id) FROM pizza_variants), 1) + 1, false);