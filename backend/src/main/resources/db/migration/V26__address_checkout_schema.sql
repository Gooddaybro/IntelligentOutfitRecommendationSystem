CREATE TABLE user_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    recipient_name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_user_address_default (user_id, is_default),
    KEY idx_user_address_updated (user_id, updated_at),
    CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);

CREATE TABLE order_address_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    source_address_id BIGINT NULL,
    recipient_name VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_order_address_snapshot_order (order_id),
    CONSTRAINT fk_order_address_snapshot_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE CASCADE
);
