CREATE TABLE ai_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    active_slot VARCHAR(64) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    worker_id VARCHAR(64) NULL,
    lease_until DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64) NULL,
    failure_summary VARCHAR(500) NULL,
    result_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_ai_task_task_id (task_id),
    UNIQUE KEY uk_ai_task_active_slot (active_slot),
    KEY idx_ai_task_type_status (task_type, status),
    KEY idx_ai_task_lease (lease_until)
);

CREATE TABLE outbox_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
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
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_publishable (status, available_at, claim_until)
);

CREATE TABLE consumer_inbox (
    consumer_name VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (consumer_name, event_id),
    KEY idx_consumer_inbox_task (task_id)
);

CREATE TABLE ai_task_redrive_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    previous_event_id VARCHAR(64) NULL,
    new_event_id VARCHAR(64) NOT NULL,
    redriven_by BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_redrive_task_created (task_id, created_at)
);
