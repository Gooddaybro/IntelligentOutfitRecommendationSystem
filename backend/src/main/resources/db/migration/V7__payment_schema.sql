ALTER TABLE sales_order
    ADD COLUMN closed_at DATETIME(6) NULL;

ALTER TABLE sales_order
    ADD COLUMN close_reason VARCHAR(255) NULL;

CREATE TABLE payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(128) NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_payment_order_success (order_id, status),
    KEY idx_payment_order_no (order_no),
    KEY idx_payment_user_created (user_id, created_at),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);
