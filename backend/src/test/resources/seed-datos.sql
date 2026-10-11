-- Datos mínimos para las pruebas de integración del esquema.
--
-- NUNCA va dentro de una migración V*: una migración con datos se ejecuta en producción.
-- Ver db/migration/README.md §3.
--
-- Todo con ON CONFLICT DO NOTHING: @Sql se ejecuta antes de cada test y los tests
-- insertan filas propias que no deben chocar con estas.

INSERT INTO customers (id, email, password_hash, full_name, role) VALUES
    ('11111111-1111-1111-1111-111111111111', 'admin@pizzeria.test',   'hash', 'Administrador', 'ADMIN'),
    ('22222222-2222-2222-2222-222222222222', 'cliente@pizzeria.test', 'hash', 'Lucia',        'CUSTOMER')
ON CONFLICT DO NOTHING;

INSERT INTO pizzas (id, name) VALUES
    ('33333333-3333-3333-3333-333333333333', 'Margarita')
ON CONFLICT DO NOTHING;

INSERT INTO pizza_variants (id, pizza_id, size, price) VALUES
    ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 'MEDIUM', 12500)
ON CONFLICT DO NOTHING;