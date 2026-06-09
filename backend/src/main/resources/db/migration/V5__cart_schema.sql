CREATE TABLE cart_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_cart_item_user_sku (user_id, sku_id),
    KEY idx_cart_item_user_updated (user_id, updated_at),
    CONSTRAINT fk_cart_item_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id),
    CONSTRAINT ck_cart_item_quantity_positive CHECK (quantity > 0)
);
