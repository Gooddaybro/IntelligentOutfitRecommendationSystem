INSERT INTO category (id, parent_id, name, level, sort_order, status) VALUES
(6, 1, '衬衫', 2, 3, 'active'),
(7, 1, '卫衣', 2, 4, 'active'),
(8, 1, '针织衫', 2, 5, 'active'),
(9, 1, '西装', 2, 6, 'active'),
(10, 1, '羽绒服', 2, 7, 'active'),
(11, 4, '牛仔裤', 2, 2, 'active'),
(12, 4, '休闲裤', 2, 3, 'active'),
(13, 4, '短裤', 2, 4, 'active'),
(14, 4, '半裙', 2, 5, 'active');

INSERT INTO color (id, name, color_family, hex_code) VALUES
(7, '米色', 'neutral', '#E8DCC8'),
(8, '浅蓝色', 'blue', '#8DB7E8'),
(9, '军绿色', 'green', '#4B5D3A'),
(10, '酒红色', 'red', '#7B1E32'),
(11, '粉色', 'pink', '#F4A7B9'),
(12, '棕色', 'brown', '#6B4F3A'),
(13, '牛仔蓝', 'blue', '#2F5D8C'),
(14, '浅灰色', 'gray', '#C9CDD2');

INSERT INTO size_option (id, code, name, sort_order) VALUES
(5, 'XS', 'XS码', 0),
(6, 'XXL', 'XXL码', 5),
(7, '28', '28码', 6),
(8, '30', '30码', 7),
(9, '32', '32码', 8),
(10, '34', '34码', 9);

INSERT INTO fit_type (id, code, name, description) VALUES
(4, 'slim', '修身', '更贴合身体线条，适合正式或精致通勤场景'),
(5, 'oversized', '廓形', '肩线和衣身更宽，适合街头和休闲叠穿'),
(6, 'tapered', '锥形', '上宽下窄的裤装版型，兼顾活动量和利落感'),
(7, 'relaxed', '舒适', '比常规版型更放松，适合旅行和长时间穿着');

INSERT INTO style_tag (id, code, name, description) VALUES
(5, 'formal', '正式', '适合会议、面试和商务正式场景'),
(6, 'street', '街头', '适合年轻化、宽松和层次感穿搭'),
(7, 'outdoor', '户外', '适合防风、防泼水或轻户外活动'),
(8, 'date', '约会', '适合柔和、精致或轻正式的社交场景'),
(9, 'smart_casual', '精致休闲', '介于通勤和休闲之间，适合多场景切换'),
(10, 'travel', '旅行', '强调舒适、耐穿和易打理');

INSERT INTO material (id, name, description) VALUES
(4, '亚麻', '透气、干爽，适合夏季衬衫和短裤'),
(5, '羊毛', '保暖、垂坠感好，适合秋冬针织和外套'),
(6, '羊毛混纺', '兼顾挺括、保暖和易打理，适合西装和大衣'),
(7, '速干聚酯纤维', '轻薄快干，适合运动和通勤内搭'),
(8, '牛仔棉', '耐磨、挺括，适合牛仔裤和休闲外套'),
(9, '尼龙', '轻量、防风，适合户外夹克'),
(10, '羽绒', '轻量保暖，适合冬季外套'),
(11, '棉麻混纺', '兼顾棉的舒适和亚麻的透气'),
(12, '粘纤混纺', '柔软垂顺，适合半裙和轻薄上衣');

INSERT INTO size_rule (id, code, name, category_id, rule_json, description) VALUES
(4, 'default_shirt', '默认衬衫尺码规则', 6, '{"type":"height_weight","ranges":[{"size":"S","heightMin":155,"heightMax":165},{"size":"M","heightMin":165,"heightMax":175},{"size":"L","heightMin":172,"heightMax":182},{"size":"XL","heightMin":180,"heightMax":190}]}', '衬衫基础尺码规则'),
(5, 'default_hoodie', '默认卫衣尺码规则', 7, '{"type":"height_weight","ranges":[{"size":"M","heightMin":160,"heightMax":172},{"size":"L","heightMin":170,"heightMax":182},{"size":"XL","heightMin":178,"heightMax":190}]}', '卫衣基础尺码规则'),
(6, 'default_blazer', '默认西装尺码规则', 9, '{"type":"height_weight","ranges":[{"size":"M","heightMin":165,"heightMax":173},{"size":"L","heightMin":172,"heightMax":180},{"size":"XL","heightMin":178,"heightMax":188}]}', '西装基础尺码规则'),
(7, 'default_jeans', '默认牛仔裤尺码规则', 11, '{"type":"waist","ranges":[{"size":"28","waistMin":68,"waistMax":72},{"size":"30","waistMin":73,"waistMax":78},{"size":"32","waistMin":79,"waistMax":84},{"size":"34","waistMin":85,"waistMax":91}]}', '牛仔裤腰围尺码规则'),
(8, 'default_skirt', '默认半裙尺码规则', 14, '{"type":"waist","ranges":[{"size":"S","waistMin":60,"waistMax":66},{"size":"M","waistMin":66,"waistMax":72},{"size":"L","waistMin":72,"waistMax":78}]}', '半裙基础尺码规则');

INSERT INTO product_spu (id, spu_code, name, category_id, description, main_image_url, fit_type_id, status) VALUES
(1101, 'OXFORD_SHIRT_COMMUTE_001', '男士牛津纺通勤衬衫', 6, '挺括牛津纺衬衫，适合通勤、会议和精致休闲场景。', '/images/products/oxford-shirt-commute-main.jpg', 1, 'on_sale'),
(1102, 'LINEN_SHIRT_SUMMER_001', '夏季亚麻短袖衬衫', 6, '轻薄亚麻短袖衬衫，适合夏季通勤和旅行。', '/images/products/linen-shirt-summer-main.jpg', 2, 'on_sale'),
(1103, 'HOODIE_CASUAL_001', '宽松连帽休闲卫衣', 7, '柔软棉质连帽卫衣，适合日常休闲和叠穿。', '/images/products/hoodie-casual-main.jpg', 2, 'on_sale'),
(1104, 'KNIT_MINIMAL_CARDIGAN_001', '极简针织开衫', 8, '细针针织开衫，适合春秋通勤和室内空调环境。', '/images/products/knit-minimal-cardigan-main.jpg', 1, 'on_sale'),
(1105, 'BLAZER_FORMAL_001', '羊毛混纺修身西装外套', 9, '修身羊毛混纺西装外套，适合正式商务和面试。', '/images/products/blazer-formal-main.jpg', 4, 'on_sale'),
(1106, 'PUFFER_WINTER_LIGHT_001', '轻量保暖羽绒服', 10, '轻量羽绒服，适合冬季通勤和旅行。', '/images/products/puffer-winter-light-main.jpg', 1, 'on_sale'),
(1107, 'JEANS_STRAIGHT_DAILY_001', '直筒日常牛仔裤', 11, '中高腰直筒牛仔裤，适合四季日常穿搭。', '/images/products/jeans-straight-daily-main.jpg', 3, 'on_sale'),
(1108, 'CHINO_COMMUTE_TAPERED_001', '锥形通勤休闲裤', 12, '微锥形休闲裤，兼顾通勤利落感和活动舒适度。', '/images/products/chino-commute-tapered-main.jpg', 6, 'on_sale'),
(1109, 'SHORTS_SUMMER_COTTON_001', '夏季棉质休闲短裤', 13, '透气棉质短裤，适合夏季休闲和旅行。', '/images/products/shorts-summer-cotton-main.jpg', 1, 'on_sale'),
(1110, 'SKIRT_A_LINE_DATE_001', '约会A字半裙', 14, 'A字版型半裙，适合约会、通勤和轻正式场景。', '/images/products/skirt-a-line-date-main.jpg', 1, 'on_sale'),
(1111, 'TSHIRT_SPORT_QUICKDRY_001', '速干运动T恤', 2, '轻薄速干面料，适合运动和夏季通勤内搭。', '/images/products/tshirt-sport-quickdry-main.jpg', 1, 'on_sale'),
(1112, 'JACKET_OUTDOOR_SHELL_001', '户外防风轻壳夹克', 3, '尼龙防风轻壳，适合春秋户外和城市通勤。', '/images/products/jacket-outdoor-shell-main.jpg', 1, 'on_sale'),
(1113, 'SWEATSHIRT_STREET_OVERSIZED_001', '街头廓形圆领卫衣', 7, '廓形圆领卫衣，适合街头休闲和层次穿搭。', '/images/products/sweatshirt-street-oversized-main.jpg', 5, 'on_sale'),
(1114, 'SHIRT_SMART_CASUAL_STRIPE_001', '条纹精致休闲衬衫', 6, '条纹衬衫，适合周五通勤和轻商务场景。', '/images/products/shirt-smart-casual-stripe-main.jpg', 1, 'on_sale'),
(1115, 'PANTS_TRAVEL_RELAXED_001', '旅行舒适休闲裤', 12, '舒适弹力休闲裤，适合长时间通勤和旅行。', '/images/products/pants-travel-relaxed-main.jpg', 7, 'on_sale'),
(1116, 'KNIT_WOOL_WINTER_001', '羊毛保暖针织衫', 8, '羊毛针织衫，适合冬季内搭和轻商务。', '/images/products/knit-wool-winter-main.jpg', 1, 'on_sale'),
(1117, 'BLAZER_SMART_CASUAL_001', '精致休闲西装外套', 9, '微弹西装外套，适合通勤和晚间社交。', '/images/products/blazer-smart-casual-main.jpg', 1, 'on_sale'),
(1118, 'DENIM_JACKET_STREET_001', '街头牛仔夹克', 3, '牛仔面料夹克，适合春秋街头穿搭。', '/images/products/denim-jacket-street-main.jpg', 5, 'on_sale'),
(1119, 'POLO_COMMUTE_MINIMAL_001', '极简通勤Polo衫', 2, '简洁Polo衫，适合夏季通勤和周末休闲。', '/images/products/polo-commute-minimal-main.jpg', 1, 'on_sale'),
(1120, 'SKIRT_COMMUTE_PLEATED_001', '通勤百褶半裙', 14, '垂顺百褶半裙，适合春秋通勤。', '/images/products/skirt-commute-pleated-main.jpg', 1, 'on_sale'),
(1121, 'SHORTS_OUTDOOR_NYLON_001', '户外尼龙短裤', 13, '轻量尼龙短裤，适合旅行和轻户外活动。', '/images/products/shorts-outdoor-nylon-main.jpg', 7, 'on_sale'),
(1122, 'SHIRT_DATE_SOFT_001', '柔软约会衬衫', 6, '柔软垂顺衬衫，适合约会和轻正式社交。', '/images/products/shirt-date-soft-main.jpg', 1, 'on_sale'),
(1123, 'PANTS_FORMAL_SLIM_001', '修身正式西裤', 12, '修身西裤，适合会议和正式通勤。', '/images/products/pants-formal-slim-main.jpg', 4, 'on_sale'),
(1124, 'JACKET_COMMUTE_TRENCH_001', '通勤轻薄风衣', 3, '轻薄风衣，适合春秋通勤和出差。', '/images/products/jacket-commute-trench-main.jpg', 1, 'on_sale'),
(1125, 'HOODIE_TRAVEL_RELAXED_001', '旅行舒适卫衣', 7, '放松版型卫衣，适合旅行和周末休闲。', '/images/products/hoodie-travel-relaxed-main.jpg', 7, 'on_sale'),
(1126, 'JEANS_TAPERED_DARK_001', '深色锥形牛仔裤', 11, '深色锥形牛仔裤，适合通勤和休闲切换。', '/images/products/jeans-tapered-dark-main.jpg', 6, 'on_sale'),
(1127, 'TSHIRT_MINIMAL_HEAVY_001', '重磅极简T恤', 2, '重磅棉质T恤，适合单穿和内搭。', '/images/products/tshirt-minimal-heavy-main.jpg', 1, 'on_sale'),
(1128, 'PUFFER_OUTDOOR_WINTER_001', '户外防寒羽绒服', 10, '高蓬松度羽绒服，适合冬季户外和北方通勤。', '/images/products/puffer-outdoor-winter-main.jpg', 7, 'on_sale'),
(1129, 'CARDIGAN_DATE_SOFT_001', '柔软约会针织开衫', 8, '柔软针织开衫，适合约会和春秋叠穿。', '/images/products/cardigan-date-soft-main.jpg', 1, 'on_sale'),
(1130, 'CHINO_CASUAL_LOOSE_001', '宽松休闲卡其裤', 12, '宽松卡其休闲裤，适合日常休闲。', '/images/products/chino-casual-loose-main.jpg', 2, 'on_sale'),
(1131, 'SHIRT_FORMAL_WHITE_001', '正式白衬衫', 6, '标准白衬衫，适合商务正式场景。', '/images/products/shirt-formal-white-main.jpg', 4, 'on_sale'),
(1132, 'JACKET_SPORT_LIGHT_001', '轻量运动夹克', 3, '轻量运动夹克，适合通勤前后轻运动。', '/images/products/jacket-sport-light-main.jpg', 1, 'on_sale'),
(1133, 'SKIRT_MINIMAL_STRAIGHT_001', '极简直筒半裙', 14, '直筒半裙，适合极简通勤穿搭。', '/images/products/skirt-minimal-straight-main.jpg', 3, 'on_sale'),
(1134, 'JEANS_CASUAL_WIDE_001', '休闲宽腿牛仔裤', 11, '宽腿牛仔裤，适合休闲和街头风格。', '/images/products/jeans-casual-wide-main.jpg', 2, 'on_sale'),
(1135, 'TSHIRT_LOW_STOCK_EDGE_001', '低库存边界T恤', 2, '用于验证低库存仍可推荐但排序靠后的边界商品。', '/images/products/tshirt-low-stock-edge-main.jpg', 1, 'on_sale'),
(1136, 'JACKET_OUT_OF_STOCK_EDGE_001', '无库存边界夹克', 3, '用于验证推荐候选会排除无库存商品。', '/images/products/jacket-out-of-stock-edge-main.jpg', 1, 'on_sale'),
(1137, 'SHIRT_OFFSALE_EDGE_001', '下架边界衬衫', 6, '用于验证推荐候选会排除下架商品。', '/images/products/shirt-offsale-edge-main.jpg', 1, 'off_sale');

INSERT INTO product_sku (id, sku_code, spu_id, color_id, size_id, sale_price, original_price, status)
SELECT
    p.id * 10 + spec.variant_no,
    CONCAT(p.spu_code, '-C', spec.color_id, '-S', spec.size_id),
    p.id,
    spec.color_id,
    spec.size_id,
    CAST((
        CASE p.category_id
            WHEN 2 THEN 99
            WHEN 3 THEN 399
            WHEN 6 THEN 199
            WHEN 7 THEN 239
            WHEN 8 THEN 269
            WHEN 9 THEN 699
            WHEN 10 THEN 899
            WHEN 11 THEN 259
            WHEN 12 THEN 229
            WHEN 13 THEN 129
            WHEN 14 THEN 189
            ELSE 199
        END + MOD(p.id, 5) * 10
    ) AS DECIMAL(10, 2)),
    CAST((
        CASE p.category_id
            WHEN 2 THEN 129
            WHEN 3 THEN 499
            WHEN 6 THEN 259
            WHEN 7 THEN 299
            WHEN 8 THEN 339
            WHEN 9 THEN 899
            WHEN 10 THEN 1199
            WHEN 11 THEN 329
            WHEN 12 THEN 299
            WHEN 13 THEN 169
            WHEN 14 THEN 249
            ELSE 269
        END + MOD(p.id, 5) * 10
    ) AS DECIMAL(10, 2)),
    CASE
        WHEN p.status = 'on_sale' AND spec.variant_no <> 4 THEN 'on_sale'
        ELSE 'off_sale'
    END
FROM product_spu p
JOIN (
    SELECT 1 AS variant_no, 1 AS color_id, 2 AS size_id
    UNION ALL SELECT 2, 1, 3
    UNION ALL SELECT 3, 2, 2
    UNION ALL SELECT 4, 2, 3
    UNION ALL SELECT 5, 7, 2
    UNION ALL SELECT 6, 9, 3
) spec ON 1 = 1
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_image (spu_id, sku_id, image_url, image_type, sort_order)
SELECT id, NULL, main_image_url, 'main', 1
FROM product_spu
WHERE id BETWEEN 1101 AND 1137;

INSERT INTO product_material (spu_id, material_id, percentage)
SELECT
    p.id,
    CASE
        WHEN p.spu_code LIKE '%LINEN%' THEN 4
        WHEN p.spu_code LIKE '%WOOL%' THEN 5
        WHEN p.spu_code LIKE '%BLAZER%' OR p.spu_code LIKE '%FORMAL%' THEN 6
        WHEN p.spu_code LIKE '%SPORT%' OR p.spu_code LIKE '%QUICKDRY%' THEN 7
        WHEN p.spu_code LIKE '%DENIM%' OR p.spu_code LIKE '%JEANS%' THEN 8
        WHEN p.spu_code LIKE '%OUTDOOR%' OR p.spu_code LIKE '%NYLON%' OR p.spu_code LIKE '%SHELL%' THEN 9
        WHEN p.spu_code LIKE '%PUFFER%' THEN 10
        WHEN p.spu_code LIKE '%DATE%' OR p.category_id = 14 THEN 12
        WHEN p.spu_code LIKE '%TRAVEL%' THEN 11
        ELSE 1
    END,
    100
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_season (spu_id, season_id)
SELECT
    p.id,
    CASE
        WHEN p.spu_code LIKE '%SUMMER%' OR p.spu_code LIKE '%SHORTS%' OR p.spu_code LIKE '%QUICKDRY%' THEN 2
        WHEN p.spu_code LIKE '%WINTER%' OR p.spu_code LIKE '%PUFFER%' OR p.spu_code LIKE '%WOOL%' THEN 4
        WHEN p.spu_code LIKE '%TRENCH%' OR p.spu_code LIKE '%CARDIGAN%' OR p.spu_code LIKE '%KNIT%' THEN 1
        ELSE 3
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_season (spu_id, season_id)
SELECT p.id, 5
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND p.category_id IN (2, 6, 11, 12);

INSERT INTO product_style_tag (spu_id, style_tag_id)
SELECT
    p.id,
    CASE
        WHEN p.spu_code LIKE '%FORMAL%' THEN 5
        WHEN p.spu_code LIKE '%STREET%' THEN 6
        WHEN p.spu_code LIKE '%OUTDOOR%' THEN 7
        WHEN p.spu_code LIKE '%DATE%' THEN 8
        WHEN p.spu_code LIKE '%SMART_CASUAL%' THEN 9
        WHEN p.spu_code LIKE '%TRAVEL%' THEN 10
        WHEN p.spu_code LIKE '%SPORT%' THEN 4
        WHEN p.spu_code LIKE '%MINIMAL%' THEN 3
        WHEN p.spu_code LIKE '%COMMUTE%' THEN 1
        ELSE 2
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_style_tag (spu_id, style_tag_id)
SELECT p.id, 1
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND p.category_id IN (6, 8, 9, 12, 14)
  AND p.spu_code NOT LIKE '%COMMUTE%'
  AND p.spu_code NOT LIKE '%FORMAL%';

INSERT INTO product_style_tag (spu_id, style_tag_id)
SELECT p.id, 2
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND p.category_id IN (2, 7, 11, 12, 13)
  AND (
      p.spu_code LIKE '%COMMUTE%'
      OR p.spu_code LIKE '%MINIMAL%'
      OR p.spu_code LIKE '%SPORT%'
      OR p.spu_code LIKE '%STREET%'
      OR p.spu_code LIKE '%OUTDOOR%'
      OR p.spu_code LIKE '%TRAVEL%'
  );

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '适用场景',
    CASE
        WHEN p.spu_code LIKE '%FORMAL%' THEN '正式商务'
        WHEN p.spu_code LIKE '%OUTDOOR%' THEN '轻户外'
        WHEN p.spu_code LIKE '%DATE%' THEN '约会社交'
        WHEN p.spu_code LIKE '%TRAVEL%' THEN '旅行'
        WHEN p.spu_code LIKE '%SPORT%' THEN '运动通勤'
        WHEN p.spu_code LIKE '%COMMUTE%' THEN '通勤'
        ELSE '日常'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '厚度',
    CASE
        WHEN p.category_id = 10 OR p.spu_code LIKE '%WINTER%' THEN '厚款'
        WHEN p.spu_code LIKE '%SUMMER%' OR p.category_id = 13 THEN '薄款'
        ELSE '常规'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    CASE WHEN p.category_id IN (11, 12, 13, 14) THEN '下装版型' ELSE '衣长' END,
    CASE
        WHEN p.category_id = 11 THEN '直筒或宽腿'
        WHEN p.category_id = 12 THEN '锥形或宽松'
        WHEN p.category_id = 14 THEN '中长款'
        WHEN p.fit_type_id = 5 THEN '廓形'
        ELSE '标准'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO inventory (sku_id, available_stock, locked_stock, sold_stock)
SELECT
    sku.id,
    CASE
        WHEN sku.spu_id = 1136 THEN 0
        WHEN sku.spu_id = 1137 THEN 0
        WHEN sku.spu_id = 1135 THEN 1
        WHEN (sku.id = 11052 OR sku.id = 11252 OR sku.id = 11262) THEN 7
        WHEN sku.spu_id = 1107 AND MOD(sku.id, 10) = 2 THEN 8
        WHEN sku.spu_id = 1110 AND MOD(sku.id, 10) = 2 THEN 7
        WHEN sku.status <> 'on_sale' THEN 0
        WHEN MOD(sku.id, 10) = 6 THEN 1
        ELSE 8 + MOD(sku.id, 7)
    END,
    1,
    CASE
        WHEN sku.spu_id = 1107 AND MOD(sku.id, 10) = 2 THEN 2
        WHEN sku.spu_id = 1110 AND MOD(sku.id, 10) = 2 THEN 1
        ELSE 0
    END
FROM product_sku sku
WHERE sku.spu_id BETWEEN 1101 AND 1137;

INSERT INTO user_account (id, username, phone, email, password_hash, status) VALUES
(9001, 'demo_commute_male', '18800009001', 'demo_commute_male@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'active'),
(9002, 'demo_summer_female', '18800009002', 'demo_summer_female@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'active'),
(9003, 'demo_sport_user', '18800009003', 'demo_sport_user@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'active'),
(9004, 'demo_budget_user', '18800009004', 'demo_budget_user@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'active'),
(9005, 'demo_plus_size_user', '18800009005', 'demo_plus_size_user@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'active'),
(9006, 'demo_color_sensitive', '18800009006', 'demo_color_sensitive@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'active'),
(9007, 'demo_empty_profile', '18800009007', 'demo_empty_profile@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'active'),
(9008, 'demo_isolation_user', '18800009008', 'demo_isolation_user@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'active');

INSERT INTO user_role (user_id, role_id) VALUES
(9001, 1),
(9002, 1),
(9003, 1),
(9004, 1),
(9005, 1),
(9006, 1),
(9007, 1),
(9008, 1);

INSERT INTO user_profile (user_id, nickname, avatar_url, gender, birthday) VALUES
(9001, '通勤男用户', '/images/avatars/demo-9001.png', 'male', '1993-05-20'),
(9002, '夏季女用户', '/images/avatars/demo-9002.png', 'female', '1997-08-12'),
(9003, '运动用户', '/images/avatars/demo-9003.png', 'male', '1995-03-18'),
(9004, '低预算用户', '/images/avatars/demo-9004.png', 'female', '2000-11-05'),
(9005, '大码边界用户', '/images/avatars/demo-9005.png', 'male', '1989-01-22'),
(9006, '颜色敏感用户', '/images/avatars/demo-9006.png', 'female', '1994-09-09'),
(9008, '隔离验证用户', '/images/avatars/demo-9008.png', 'male', '1992-12-01');

INSERT INTO user_body_data (user_id, height_cm, weight_kg, gender, shoulder_width_cm, bust_cm, waist_cm, hip_cm, preferred_fit) VALUES
(9001, 175.00, 70.00, 'male', 45.00, 96.00, 82.00, 96.00, 'regular'),
(9002, 164.00, 52.00, 'female', 38.00, 84.00, 66.00, 90.00, 'loose'),
(9003, 178.00, 76.00, 'male', 46.00, 100.00, 84.00, 98.00, 'loose'),
(9004, 160.00, 50.00, 'female', 37.00, 82.00, 64.00, 88.00, 'regular'),
(9005, 188.00, 95.00, 'male', 50.00, 112.00, 100.00, 112.00, 'relaxed'),
(9006, 168.00, 58.00, 'female', 39.00, 88.00, 70.00, 94.00, 'slim'),
(9008, 172.00, 68.00, 'male', 43.00, 94.00, 80.00, 94.00, 'regular');

INSERT INTO user_preferences (user_id, preferred_styles, preferred_colors, disliked_colors, preferred_categories, budget_min, budget_max) VALUES
(9001, '["commute","smart_casual"]', '["黑色","藏青色","白色"]', '[]', '["外套","衬衫","休闲裤"]', 200.00, 800.00),
(9002, '["casual","date"]', '["白色","米色","粉色"]', '["黑色"]', '["衬衫","半裙","短裤"]', 100.00, 350.00),
(9003, '["sport","outdoor"]', '["黑色","军绿色","浅灰色"]', '[]', '["T恤","外套","短裤"]', 100.00, 600.00),
(9004, '["minimal","casual"]', '["白色","浅蓝色"]', '["酒红色"]', '["T恤","短裤"]', 0.00, 150.00),
(9005, '["travel","casual"]', '["黑色","棕色"]', '[]', '["卫衣","休闲裤","羽绒服"]', 200.00, 1000.00),
(9006, '["formal","date"]', '["米色","粉色","酒红色"]', '["黑色","军绿色"]', '["西装","衬衫","半裙"]', 300.00, 900.00),
(9008, '["commute"]', '["黑色"]', '[]', '["外套"]', 200.00, 700.00);

INSERT INTO chat_session (id, thread_id, user_id, title, status, created_at, updated_at, last_message_at) VALUES
(9101, 'th_demo_commute_active', 9001, '秋季通勤穿搭', 'active', '2026-06-01 09:00:00.000000', '2026-06-01 09:08:00.000000', '2026-06-01 09:08:00.000000'),
(9102, 'th_demo_summer_active', 9002, '夏季轻薄推荐', 'active', '2026-06-02 10:00:00.000000', '2026-06-02 10:05:00.000000', '2026-06-02 10:05:00.000000'),
(9103, 'th_demo_archived', 9001, '已归档尺码咨询', 'archived', '2026-05-20 15:00:00.000000', '2026-05-20 15:10:00.000000', '2026-05-20 15:10:00.000000'),
(9104, 'th_demo_isolation_active', 9008, '隔离用户会话', 'active', '2026-06-03 11:00:00.000000', '2026-06-03 11:03:00.000000', '2026-06-03 11:03:00.000000');

INSERT INTO chat_message (session_id, user_id, role, content, message_status, request_id, created_at) VALUES
(9101, 9001, 'user', '我需要一套秋季通勤穿搭，预算800以内。', 'succeeded', 'req-demo-9001-1', '2026-06-01 09:00:00.000000'),
(9101, 9001, 'assistant', '可以优先看羊毛混纺西装外套、牛津纺衬衫和锥形休闲裤。', 'succeeded', 'req-demo-9001-1', '2026-06-01 09:01:00.000000'),
(9101, 9001, 'user', '颜色尽量黑白灰。', 'succeeded', 'req-demo-9001-2', '2026-06-01 09:08:00.000000'),
(9102, 9002, 'user', '夏天想要轻薄一点，不要黑色。', 'succeeded', 'req-demo-9002-1', '2026-06-02 10:00:00.000000'),
(9102, 9002, 'assistant', '可以从亚麻衬衫、棉质短裤和浅色半裙里选。', 'succeeded', 'req-demo-9002-1', '2026-06-02 10:05:00.000000'),
(9103, 9001, 'user', '我175厘米70公斤外套穿什么码？', 'succeeded', 'req-demo-archived-1', '2026-05-20 15:00:00.000000'),
(9103, 9001, 'assistant', '多数常规外套建议先试L码。', 'succeeded', 'req-demo-archived-1', '2026-05-20 15:10:00.000000'),
(9104, 9008, 'user', '帮我推荐外套。', 'succeeded', 'req-demo-9008-1', '2026-06-03 11:00:00.000000');

INSERT INTO cart_item (user_id, sku_id, quantity, created_at, updated_at) VALUES
(9001, 11012, 1, '2026-06-10 09:00:00.000000', '2026-06-10 09:00:00.000000'),
(9001, 11082, 2, '2026-06-10 09:05:00.000000', '2026-06-10 09:05:00.000000'),
(9002, 11022, 1, '2026-06-10 10:00:00.000000', '2026-06-10 10:00:00.000000'),
(9002, 11102, 1, '2026-06-10 10:10:00.000000', '2026-06-10 10:10:00.000000'),
(9003, 11112, 2, '2026-06-11 11:00:00.000000', '2026-06-11 11:00:00.000000'),
(9004, 11352, 1, '2026-06-11 12:00:00.000000', '2026-06-11 12:00:00.000000'),
(9005, 11252, 1, '2026-06-11 13:00:00.000000', '2026-06-11 13:00:00.000000'),
(9008, 11012, 1, '2026-06-12 09:00:00.000000', '2026-06-12 09:00:00.000000');

INSERT INTO sales_order (id, order_no, user_id, total_amount, status, paid_at, closed_at, close_reason, created_at, updated_at) VALUES
(9201, 'ORDDEMO9001UNPAID', 9001, 699.00, 'UNPAID', NULL, NULL, NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
(9202, 'ORDDEMO9001PAID', 9001, 558.00, 'PAID', '2026-06-12 10:10:00.000000', NULL, NULL, '2026-06-12 10:00:00.000000', '2026-06-12 10:10:00.000000'),
(9203, 'ORDDEMO9002CANCEL', 9002, 219.00, 'CANCELLED', NULL, '2026-06-12 11:20:00.000000', 'USER_CANCELLED', '2026-06-12 11:00:00.000000', '2026-06-12 11:20:00.000000'),
(9204, 'ORDDEMO9003CLOSED', 9003, 129.00, 'CLOSED', NULL, '2026-06-12 12:40:00.000000', 'TIMEOUT_UNPAID_30_MINUTES', '2026-06-12 12:00:00.000000', '2026-06-12 12:40:00.000000'),
(9205, 'ORDDEMO9005UNPAID', 9005, 578.00, 'UNPAID', NULL, NULL, NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
(9206, 'ORDDEMO9002PAID', 9002, 199.00, 'PAID', '2026-06-12 14:08:00.000000', NULL, NULL, '2026-06-12 14:00:00.000000', '2026-06-12 14:08:00.000000');

INSERT INTO order_item (order_id, sku_id, spu_id, sku_code, spu_code, product_name, category_name, color, size, sale_price, quantity, line_amount, main_image_url, created_at) VALUES
(9201, 11052, 1105, 'BLAZER_FORMAL_001-C1-S3', 'BLAZER_FORMAL_001', '羊毛混纺修身西装外套', '西装', '黑色', 'L', 699.00, 1, 699.00, '/images/products/blazer-formal-main.jpg', '2026-06-12 09:30:00.000000'),
(9202, 11072, 1107, 'JEANS_STRAIGHT_DAILY_001-C1-S3', 'JEANS_STRAIGHT_DAILY_001', '直筒日常牛仔裤', '牛仔裤', '黑色', 'L', 279.00, 2, 558.00, '/images/products/jeans-straight-daily-main.jpg', '2026-06-12 10:00:00.000000'),
(9203, 11022, 1102, 'LINEN_SHIRT_SUMMER_001-C1-S3', 'LINEN_SHIRT_SUMMER_001', '夏季亚麻短袖衬衫', '衬衫', '黑色', 'L', 219.00, 1, 219.00, '/images/products/linen-shirt-summer-main.jpg', '2026-06-12 11:00:00.000000'),
(9204, 11112, 1111, 'TSHIRT_SPORT_QUICKDRY_001-C1-S3', 'TSHIRT_SPORT_QUICKDRY_001', '速干运动T恤', 'T恤', '黑色', 'L', 109.00, 1, 109.00, '/images/products/tshirt-sport-quickdry-main.jpg', '2026-06-12 12:00:00.000000'),
(9205, 11252, 1125, 'HOODIE_TRAVEL_RELAXED_001-C1-S3', 'HOODIE_TRAVEL_RELAXED_001', '旅行舒适卫衣', '卫衣', '黑色', 'L', 239.00, 1, 239.00, '/images/products/hoodie-travel-relaxed-main.jpg', '2026-06-12 13:00:00.000000'),
(9205, 11262, 1126, 'JEANS_TAPERED_DARK_001-C1-S3', 'JEANS_TAPERED_DARK_001', '深色锥形牛仔裤', '牛仔裤', '黑色', 'L', 269.00, 1, 269.00, '/images/products/jeans-tapered-dark-main.jpg', '2026-06-12 13:00:00.000000'),
(9206, 11102, 1110, 'SKIRT_A_LINE_DATE_001-C1-S3', 'SKIRT_A_LINE_DATE_001', '约会A字半裙', '半裙', '黑色', 'L', 189.00, 1, 189.00, '/images/products/skirt-a-line-date-main.jpg', '2026-06-12 14:00:00.000000');

INSERT INTO payment (id, payment_no, order_id, order_no, user_id, amount, channel, status, transaction_id, paid_at, provider_trade_no, provider_payload, created_at, updated_at) VALUES
(9301, 'PAYDEMO9001PAID', 9202, 'ORDDEMO9001PAID', 9001, 558.00, 'MOCK', 'SUCCESS', 'mock-demo-9001-paid', '2026-06-12 10:10:00.000000', 'mock-trade-9001-paid', '{"channel":"MOCK","scenario":"paid_order"}', '2026-06-12 10:10:00.000000', '2026-06-12 10:10:00.000000'),
(9302, 'PAYDEMO9002PAID', 9206, 'ORDDEMO9002PAID', 9002, 199.00, 'MOCK', 'SUCCESS', 'mock-demo-9002-paid', '2026-06-12 14:08:00.000000', 'mock-trade-9002-paid', '{"channel":"MOCK","scenario":"paid_order"}', '2026-06-12 14:08:00.000000', '2026-06-12 14:08:00.000000'),
(9303, 'PAYDEMO9001PENDING', 9201, 'ORDDEMO9001UNPAID', 9001, 699.00, 'MOCK', 'PENDING', NULL, NULL, 'mock-trade-9001-pending', '{"channel":"MOCK","scenario":"pending_order"}', '2026-06-12 09:31:00.000000', '2026-06-12 09:31:00.000000');

INSERT INTO payment_callback_log (id, channel, payment_no, order_no, provider_trade_no, event_type, raw_body, headers, signature_valid, handled, failure_reason, created_at) VALUES
(9401, 'MOCK', 'PAYDEMO9001PAID', 'ORDDEMO9001PAID', 'mock-trade-9001-paid', 'payment.success', '{"status":"SUCCESS"}', '{"X-Mock-Signature":"valid"}', TRUE, TRUE, NULL, '2026-06-12 10:10:01.000000'),
(9402, 'MOCK', 'PAYDEMO9002PAID', 'ORDDEMO9002PAID', 'mock-trade-9002-paid', 'payment.success', '{"status":"SUCCESS"}', '{"X-Mock-Signature":"valid"}', TRUE, TRUE, NULL, '2026-06-12 14:08:01.000000'),
(9403, 'MOCK', NULL, 'ORDDEMO9001UNPAID', 'mock-trade-invalid', 'payment.success', '{"status":"SUCCESS"}', '{"X-Mock-Signature":"invalid"}', FALSE, FALSE, 'invalid signature', '2026-06-12 15:00:00.000000'),
(9404, 'MOCK', 'PAYDEMO9001PENDING', 'ORDDEMO9001UNPAID', 'mock-trade-9001-pending', 'payment.pending', '{"status":"PENDING"}', '{"X-Mock-Signature":"valid"}', TRUE, FALSE, 'waiting for final provider status', '2026-06-12 15:10:00.000000');
