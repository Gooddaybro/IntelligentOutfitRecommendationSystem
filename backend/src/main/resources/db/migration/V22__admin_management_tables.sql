CREATE TABLE admin_inventory_adjustment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    before_stock INT NOT NULL,
    after_stock INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    adjusted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_admin_inventory_adjustment_sku (sku_id, adjusted_at),
    CONSTRAINT fk_admin_inventory_adjustment_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
);

CREATE TABLE order_shipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    carrier VARCHAR(64) NOT NULL,
    tracking_no VARCHAR(128) NOT NULL,
    shipped_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_order_shipment_order_no (order_no),
    CONSTRAINT fk_order_shipment_order FOREIGN KEY (order_id) REFERENCES sales_order(id)
);

CREATE TABLE admin_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_admin_audit_created (created_at),
    KEY idx_admin_audit_target (target_type, target_id)
);