CREATE SEQUENCE seq_categories_id START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE seq_products_id START WITH 1 INCREMENT BY 1;

CREATE TABLE categories (
                            id          BIGINT PRIMARY KEY,
                            name        VARCHAR(100) NOT NULL UNIQUE,
                            description TEXT,
                            active      BOOLEAN NOT NULL DEFAULT TRUE,
                            created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                            updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
                          id           BIGINT PRIMARY KEY,
                          category_id  BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
                          name         VARCHAR(150) NOT NULL,
                          description  TEXT,
                          price        NUMERIC(10,2) NOT NULL,
                          image_url    VARCHAR(500),
                          available    BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_available ON products(available);