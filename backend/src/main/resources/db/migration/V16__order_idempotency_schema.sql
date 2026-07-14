CREATE TABLE order_idempotency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    order_id BIGINT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_order_idempotency UNIQUE (user_id, operation, idempotency_key),
    KEY idx_order_idempotency_expires (expires_at),
    CONSTRAINT fk_order_idempotency_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_order_idempotency_order FOREIGN KEY (order_id) REFERENCES sales_order(id)
);
