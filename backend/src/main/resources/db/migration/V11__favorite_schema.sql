CREATE TABLE user_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_user_favorite_user_spu (user_id, spu_id),
    KEY idx_user_favorite_user_created (user_id, created_at),
    KEY idx_user_favorite_spu (spu_id),
    CONSTRAINT fk_user_favorite_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_favorite_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id)
);
