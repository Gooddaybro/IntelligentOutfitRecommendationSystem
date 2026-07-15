CREATE TABLE assistant_recommendation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recommendation_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    thread_id VARCHAR(64) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    candidate_count INT NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    model_version VARCHAR(128) NULL,
    prompt_version VARCHAR(128) NULL,
    rag_index_version VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_assistant_recommendation_id (recommendation_id),
    KEY idx_assistant_recommendation_user_time (user_id, created_at),
    KEY idx_assistant_recommendation_request (request_id),
    CONSTRAINT fk_assistant_recommendation_user
        FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);

CREATE TABLE assistant_recommendation_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recommendation_id VARCHAR(64) NOT NULL,
    spu_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    selected BOOLEAN NOT NULL,
    rank_position INT NULL,
    rank_score DECIMAL(12, 6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_assistant_recommendation_item (recommendation_id, sku_id),
    KEY idx_assistant_recommendation_item_sku (sku_id),
    CONSTRAINT fk_assistant_recommendation_item_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES assistant_recommendation(recommendation_id) ON DELETE CASCADE,
    CONSTRAINT fk_assistant_recommendation_item_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_assistant_recommendation_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
);
