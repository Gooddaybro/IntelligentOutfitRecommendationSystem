INSERT INTO category (id, parent_id, name, level, sort_order, status) VALUES
(1, NULL, '上衣', 1, 1, 'active'),
(2, 1, 'T恤', 2, 1, 'active'),
(3, 1, '外套', 2, 2, 'active'),
(4, NULL, '下装', 1, 2, 'active'),
(5, 4, '长裤', 2, 1, 'active');

INSERT INTO color (id, name, color_family, hex_code) VALUES
(1, '黑色', 'black', '#000000'),
(2, '白色', 'white', '#FFFFFF'),
(3, '灰色', 'gray', '#808080'),
(4, '藏青色', 'blue', '#1F2A44'),
(5, '卡其色', 'khaki', '#C3B091'),
(6, '深蓝色', 'blue', '#003366');

INSERT INTO size_option (id, code, name, sort_order) VALUES
(1, 'S', 'S码', 1),
(2, 'M', 'M码', 2),
(3, 'L', 'L码', 3),
(4, 'XL', 'XL码', 4);

INSERT INTO fit_type (id, code, name, description) VALUES
(1, 'regular', '合身', '常规版型，适合大多数日常场景'),
(2, 'loose', '宽松', '宽松版型，适合偏休闲穿着'),
(3, 'straight', '直筒', '直筒版型，适合裤装');

INSERT INTO season (id, code, name) VALUES
(1, 'spring', '春季'),
(2, 'summer', '夏季'),
(3, 'autumn', '秋季'),
(4, 'winter', '冬季'),
(5, 'all_season', '四季');

INSERT INTO style_tag (id, code, name, description) VALUES
(1, 'commute', '通勤', '适合上班、通勤和半正式场景'),
(2, 'casual', '休闲', '适合日常休闲场景'),
(3, 'minimal', '极简', '配色和设计简洁'),
(4, 'sport', '运动', '适合轻运动或运动休闲');

INSERT INTO material (id, name, description) VALUES
(1, '纯棉', '亲肤、透气，适合基础款上衣'),
(2, '聚酯纤维混纺', '轻薄、抗皱，适合外套'),
(3, '棉涤混纺', '兼顾挺括和舒适，适合裤装');

INSERT INTO size_rule (id, code, name, category_id, rule_json, description) VALUES
(1, 'default_tshirt', '默认T恤尺码规则', 2, '{"type":"height_weight","ranges":[{"size":"M","heightMin":165,"heightMax":175},{"size":"L","heightMin":170,"heightMax":182}]}', 'T恤基础尺码规则'),
(2, 'default_jacket', '默认外套尺码规则', 3, '{"type":"height_weight","ranges":[{"size":"M","heightMin":165,"heightMax":175},{"size":"L","heightMin":172,"heightMax":183}]}', '外套基础尺码规则'),
(3, 'default_pants', '默认长裤尺码规则', 5, '{"type":"height_weight","ranges":[{"size":"M","heightMin":165,"heightMax":175},{"size":"L","heightMin":170,"heightMax":182}]}', '长裤基础尺码规则');

INSERT INTO product_spu (id, spu_code, name, category_id, description, main_image_url, fit_type_id, status) VALUES
(1001, 'TSHIRT_BASIC_001', '基础款纯棉T恤', 2, '100%纯棉基础款T恤，适合日常内搭和单穿。', '/images/products/tshirt-basic-main.jpg', 1, 'on_sale'),
(1002, 'JACKET_COMMUTE_001', '通勤轻薄外套', 3, '轻薄通勤外套，适合春秋通勤和日常外出。', '/images/products/jacket-commute-main.jpg', 1, 'on_sale'),
(1003, 'PANTS_STRAIGHT_001', '直筒休闲长裤', 5, '直筒休闲长裤，适合日常和通勤场景。', '/images/products/pants-straight-main.jpg', 3, 'on_sale');

INSERT INTO product_sku (id, sku_code, spu_id, color_id, size_id, sale_price, original_price, status) VALUES
(2001, 'TS-BASIC-001-BLK-S', 1001, 1, 1, 99.00, 129.00, 'on_sale'),
(2002, 'TS-BASIC-001-BLK-M', 1001, 1, 2, 99.00, 129.00, 'on_sale'),
(2003, 'TS-BASIC-001-BLK-L', 1001, 1, 3, 99.00, 129.00, 'on_sale'),
(2004, 'TS-BASIC-001-BLK-XL', 1001, 1, 4, 99.00, 129.00, 'on_sale'),
(2005, 'TS-BASIC-001-WHT-L', 1001, 2, 3, 99.00, 129.00, 'on_sale'),
(2101, 'JK-COMMUTE-001-BLK-M', 1002, 1, 2, 299.00, 399.00, 'on_sale'),
(2102, 'JK-COMMUTE-001-BLK-L', 1002, 1, 3, 299.00, 399.00, 'on_sale'),
(2103, 'JK-COMMUTE-001-NAVY-L', 1002, 4, 3, 299.00, 399.00, 'on_sale'),
(2201, 'PANTS-STRAIGHT-001-BLK-M', 1003, 1, 2, 199.00, 259.00, 'on_sale'),
(2202, 'PANTS-STRAIGHT-001-BLK-L', 1003, 1, 3, 199.00, 259.00, 'on_sale'),
(2203, 'PANTS-STRAIGHT-001-BLUE-L', 1003, 6, 3, 199.00, 259.00, 'on_sale');

INSERT INTO product_image (spu_id, sku_id, image_url, image_type, sort_order) VALUES
(1001, NULL, '/images/products/tshirt-basic-main.jpg', 'main', 1),
(1002, NULL, '/images/products/jacket-commute-main.jpg', 'main', 1),
(1003, NULL, '/images/products/pants-straight-main.jpg', 'main', 1);

INSERT INTO product_material (spu_id, material_id, percentage) VALUES
(1001, 1, 100),
(1002, 2, 100),
(1003, 3, 100);

INSERT INTO product_season (spu_id, season_id) VALUES
(1001, 2),
(1001, 5),
(1002, 1),
(1002, 3),
(1003, 1),
(1003, 3),
(1003, 5);

INSERT INTO product_style_tag (spu_id, style_tag_id) VALUES
(1001, 2),
(1001, 3),
(1002, 1),
(1002, 3),
(1003, 1),
(1003, 2);

INSERT INTO product_attribute (spu_id, attr_name, attr_value) VALUES
(1001, '厚度', '常规'),
(1001, '弹力', '微弹'),
(1001, '领型', '圆领'),
(1002, '厚度', '薄款'),
(1002, '抗皱', '较好'),
(1002, '适用场景', '通勤'),
(1003, '裤型', '直筒'),
(1003, '厚度', '常规'),
(1003, '适用场景', '通勤');

INSERT INTO inventory (sku_id, available_stock, locked_stock, sold_stock) VALUES
(2001, 6, 0, 0),
(2002, 12, 0, 0),
(2003, 8, 0, 0),
(2004, 0, 0, 0),
(2005, 2, 0, 0),
(2101, 4, 0, 0),
(2102, 7, 0, 0),
(2103, 5, 0, 0),
(2201, 8, 0, 0),
(2202, 6, 0, 0),
(2203, 4, 0, 0);
