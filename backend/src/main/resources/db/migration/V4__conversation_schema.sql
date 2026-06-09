CREATE TABLE chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    thread_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_message_at DATETIME(6) NULL,
    UNIQUE KEY uk_chat_session_thread (thread_id),
    KEY idx_chat_session_user_status (user_id, status),
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);

CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    message_status VARCHAR(32) NOT NULL DEFAULT 'succeeded',
    request_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_chat_message_session_created (session_id, created_at),
    KEY idx_chat_message_user_created (user_id, created_at),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);
