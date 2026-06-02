CREATE TABLE sales_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    paid_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_sales_order_no (order_no),
    KEY idx_sales_order_user_created (user_id, created_at),
    KEY idx_sales_order_user_status (user_id, status),
    CONSTRAINT fk_sales_order_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);

CREATE TABLE order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    spu_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    category_name VARCHAR(64) NOT NULL,
    color VARCHAR(32) NOT NULL,
    size VARCHAR(16) NOT NULL,
    sale_price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(10,2) NOT NULL,
    main_image_url VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_order_item_order (order_id),
    KEY idx_order_item_sku (sku_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE CASCADE
);
