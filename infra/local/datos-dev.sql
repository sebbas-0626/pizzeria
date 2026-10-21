-- =====================================================================================
-- Datos de ejemplo para el entorno local
-- =====================================================================================

INSERT INTO categories (name, description) VALUES
    ('Classic Pizzas', 'Traditional recipes'),
    ('Special Pizzas', 'Chef specials'),
    ('Drinks', 'Beverages')
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (category_id, name, description, price) VALUES
    ((SELECT id FROM categories WHERE name = 'Classic Pizzas'), 'Margarita', 'Tomato, mozzarella, basil', 25000),
    ((SELECT id FROM categories WHERE name = 'Classic Pizzas'), 'Pepperoni', 'Tomato, mozzarella, pepperoni', 32000),
    ((SELECT id FROM categories WHERE name = 'Special Pizzas'), 'Hawaiian', 'Ham, pineapple', 30000)
ON CONFLICT DO NOTHING;
