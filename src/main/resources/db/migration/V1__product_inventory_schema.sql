CREATE TABLE category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NULL,
    name VARCHAR(64) NOT NULL,
    level INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE color (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(32) NOT NULL,
    color_family VARCHAR(32) NOT NULL,
    hex_code VARCHAR(16) NOT NULL,
    UNIQUE KEY uk_color_name (name)
);

CREATE TABLE size_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_size_code (code)
);

CREATE TABLE fit_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(32) NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_fit_type_code (code)
);

CREATE TABLE season (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_season_code (code)
);

CREATE TABLE style_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(32) NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_style_tag_code (code)
);

CREATE TABLE material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_material_name (name)
);

CREATE TABLE size_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    category_id BIGINT NOT NULL,
    rule_json TEXT NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_size_rule_code (code),
    CONSTRAINT fk_size_rule_category FOREIGN KEY (category_id) REFERENCES category(id)
);

CREATE TABLE product_spu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category_id BIGINT NOT NULL,
    brand_id BIGINT NULL,
    description TEXT NULL,
    main_image_url VARCHAR(512) NULL,
    fit_type_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_product_spu_code (spu_code),
    KEY idx_product_spu_name (name),
    KEY idx_product_spu_status (status),
    CONSTRAINT fk_product_spu_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_product_spu_fit_type FOREIGN KEY (fit_type_id) REFERENCES fit_type(id)
);

CREATE TABLE product_sku (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_code VARCHAR(64) NOT NULL,
    spu_id BIGINT NOT NULL,
    color_id BIGINT NOT NULL,
    size_id BIGINT NOT NULL,
    sale_price DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_product_sku_code (sku_code),
    UNIQUE KEY uk_product_sku_spec (spu_id, color_id, size_id),
    KEY idx_product_sku_spu (spu_id),
    CONSTRAINT fk_product_sku_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_sku_color FOREIGN KEY (color_id) REFERENCES color(id),
    CONSTRAINT fk_product_sku_size FOREIGN KEY (size_id) REFERENCES size_option(id)
);

CREATE TABLE product_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    sku_id BIGINT NULL,
    image_url VARCHAR(512) NOT NULL,
    image_type VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_image_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_image_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
);

CREATE TABLE product_material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    percentage INT NULL,
    UNIQUE KEY uk_product_material (spu_id, material_id),
    CONSTRAINT fk_product_material_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_material_material FOREIGN KEY (material_id) REFERENCES material(id)
);

CREATE TABLE product_season (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    UNIQUE KEY uk_product_season (spu_id, season_id),
    CONSTRAINT fk_product_season_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_season_season FOREIGN KEY (season_id) REFERENCES season(id)
);

CREATE TABLE product_style_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    style_tag_id BIGINT NOT NULL,
    UNIQUE KEY uk_product_style_tag (spu_id, style_tag_id),
    CONSTRAINT fk_product_style_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_style_tag FOREIGN KEY (style_tag_id) REFERENCES style_tag(id)
);

CREATE TABLE product_attribute (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    attr_name VARCHAR(64) NOT NULL,
    attr_value VARCHAR(128) NOT NULL,
    KEY idx_product_attribute_name_value (attr_name, attr_value),
    CONSTRAINT fk_product_attribute_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id)
);

CREATE TABLE inventory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    available_stock INT NOT NULL,
    locked_stock INT NOT NULL DEFAULT 0,
    sold_stock INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_inventory_sku (sku_id),
    CONSTRAINT fk_inventory_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
);
