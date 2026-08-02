CREATE TABLE product_search_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    spu_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    schema_version INT NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'NEW',
    available_at DATETIME(6) NOT NULL,
    claimed_by VARCHAR(64) NULL,
    claim_until DATETIME(6) NULL,
    publish_attempts INT NOT NULL DEFAULT 0,
    published_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_product_search_outbox_event (event_id),
    KEY idx_product_search_outbox_publishable (status, available_at, claim_until),
    KEY idx_product_search_outbox_spu_watermark (spu_id, id)
);

CREATE TABLE product_search_inbox (
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    spu_id BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_product_search_inbox_spu (spu_id)
);
