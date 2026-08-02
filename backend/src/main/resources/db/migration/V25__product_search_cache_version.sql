CREATE TABLE product_search_cache_state (
    id BIGINT PRIMARY KEY,
    generation BIGINT NOT NULL CHECK (generation > 0),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

INSERT INTO product_search_cache_state (id, generation)
VALUES (1, 1);
