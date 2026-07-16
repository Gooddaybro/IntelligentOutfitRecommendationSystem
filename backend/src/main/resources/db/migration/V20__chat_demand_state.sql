CREATE TABLE chat_demand_state (
    session_id BIGINT PRIMARY KEY,
    state_version BIGINT NOT NULL DEFAULT 0,
    effective_intent_json LONGTEXT NOT NULL,
    last_request_id VARCHAR(64) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_chat_demand_state_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id)
);

CREATE TABLE chat_demand_transition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    message_id BIGINT NULL,
    request_id VARCHAR(64) NOT NULL,
    action VARCHAR(16) NOT NULL,
    patch_json LONGTEXT NOT NULL,
    effective_intent_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_chat_demand_transition_session
        FOREIGN KEY (session_id) REFERENCES chat_session(id),
    CONSTRAINT fk_chat_demand_transition_message
        FOREIGN KEY (message_id) REFERENCES chat_message(id),
    UNIQUE KEY uk_chat_demand_transition_request (session_id, request_id),
    KEY idx_chat_demand_transition_session_created (session_id, created_at)
);
