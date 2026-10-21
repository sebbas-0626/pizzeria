CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE categories (
                            id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            name        VARCHAR(100) NOT NULL UNIQUE,
                            description TEXT,
                            active      BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                            updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
                          id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          category_id UUID NOT NULL REFERENCES categories(id),
                          name        VARCHAR(150) NOT NULL,
                          description TEXT,
                          price       NUMERIC(10,2) NOT NULL,
                          image_url   VARCHAR(500),
                          available   BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_available ON products(available);
