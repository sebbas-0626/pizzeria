-- Datos mínimos para las pruebas de integración del esquema.
INSERT INTO users (id, email, password_hash, first_name, last_name, role) VALUES
    (1, 'admin@pizzeria.test', 'hash', 'Admin', 'User', 'ADMIN'),
    (2, 'cliente@pizzeria.test', 'hash', 'Lucia', 'Perez', 'CLIENTE')
ON CONFLICT DO NOTHING;

INSERT INTO categories (name, description) VALUES
    ('Classic Pizzas', 'Traditional recipes')
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (category_id, name, description, price) VALUES
    ((SELECT id FROM categories WHERE name = 'Classic Pizzas'), 'Margarita', 'Tomato, mozzarella, basil', 25000)
ON CONFLICT DO NOTHING;
