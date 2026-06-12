ALTER TABLE payment
    MODIFY COLUMN transaction_id VARCHAR(128) NULL;

ALTER TABLE payment
    MODIFY COLUMN paid_at DATETIME(6) NULL;

ALTER TABLE payment
    ADD COLUMN provider_trade_no VARCHAR(128) NULL;

ALTER TABLE payment
    ADD COLUMN provider_payload TEXT NULL;

CREATE TABLE payment_callback_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel VARCHAR(32) NOT NULL,
    payment_no VARCHAR(64) NULL,
    order_no VARCHAR(64) NULL,
    provider_trade_no VARCHAR(128) NULL,
    event_type VARCHAR(64) NOT NULL,
    raw_body TEXT NOT NULL,
    headers TEXT NULL,
    signature_valid BOOLEAN NOT NULL,
    handled BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_payment_callback_payment_no (payment_no),
    KEY idx_payment_callback_order_no (order_no),
    KEY idx_payment_callback_provider_trade_no (provider_trade_no),
    KEY idx_payment_callback_created_at (created_at)
);
