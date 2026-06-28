-- Standardized recommendation attributes for fuzzy AI preference ranking.
-- These tags are descriptive product facts only; price, stock, SKU, and
-- purchasability stay in their dedicated tables.

INSERT INTO product_attribute (spu_id, attr_name, attr_value) VALUES
(1001, '场景', '校园'),
(1001, '场景', '日常'),
(1001, '风格', '基础款'),
(1001, '风格', '百搭'),
(1001, '上装版型', '常规'),
(1001, '颜色倾向', '基础色'),
(1001, '材质特征', '透气'),
(1001, '搭配难度', '好搭'),

(1002, '场景', '通勤'),
(1002, '场景', '日常'),
(1002, '风格', '简洁'),
(1002, '风格', '百搭'),
(1002, '上装版型', '常规'),
(1002, '厚度', '轻薄'),
(1002, '颜色倾向', '深色'),
(1002, '材质特征', '挺括'),
(1002, '搭配难度', '好搭'),

(1003, '场景', '校园'),
(1003, '场景', '通勤'),
(1003, '场景', '日常'),
(1003, '风格', '基础款'),
(1003, '风格', '百搭'),
(1003, '腰线', '中高腰'),
(1003, '下装版型', '直筒'),
(1003, '视觉效果', '显高'),
(1003, '视觉效果', '显瘦'),
(1003, '视觉效果', '遮肉'),
(1003, '颜色倾向', '深色'),
(1003, '材质特征', '垂顺'),
(1003, '搭配难度', '好搭');

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '场景',
    CASE
        WHEN p.spu_code LIKE '%FORMAL%' THEN '通勤'
        WHEN p.spu_code LIKE '%DATE%' THEN '约会'
        WHEN p.spu_code LIKE '%SPORT%' THEN '运动'
        WHEN p.spu_code LIKE '%TRAVEL%' THEN '旅行'
        WHEN p.spu_code LIKE '%OUTDOOR%' THEN '旅行'
        ELSE '日常'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '风格',
    CASE
        WHEN p.spu_code LIKE '%MINIMAL%' THEN '简洁'
        WHEN p.spu_code LIKE '%FORMAL%' THEN '轻正式'
        WHEN p.spu_code LIKE '%DATE%' THEN '约会'
        WHEN p.spu_code LIKE '%SPORT%' THEN '运动休闲'
        ELSE '基础款'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '搭配难度',
    CASE
        WHEN p.spu_code LIKE '%STREET%' OR p.spu_code LIKE '%DATE%' THEN '强风格'
        ELSE '好搭'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '颜色倾向',
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM product_sku sku
            JOIN color co ON co.id = sku.color_id
            WHERE sku.spu_id = p.id
              AND co.name IN ('黑色', '藏青色', '深蓝色', '灰色')
        ) THEN '深色'
        ELSE '基础色'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT p.id, '腰线', '中高腰'
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND p.category_id IN (11, 12, 14);

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT p.id, '视觉效果', '显高'
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND p.category_id IN (11, 12, 14);

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT p.id, '视觉效果', '显瘦'
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND (
      p.category_id IN (11, 12, 14)
      OR p.spu_code LIKE '%MINIMAL%'
      OR p.spu_code LIKE '%COMMUTE%'
  );

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '下装版型',
    CASE
        WHEN p.spu_code LIKE '%STRAIGHT%' OR p.fit_type_id = 3 THEN '直筒'
        WHEN p.spu_code LIKE '%TAPERED%' OR p.fit_type_id = 6 THEN '锥形'
        WHEN p.spu_code LIKE '%A_LINE%' OR p.category_id = 14 THEN 'A字'
        WHEN p.spu_code LIKE '%RELAXED%' OR p.fit_type_id = 7 THEN '宽松'
        ELSE '常规'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND p.category_id IN (11, 12, 13, 14);

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '上装版型',
    CASE
        WHEN p.fit_type_id = 4 THEN '修身'
        WHEN p.fit_type_id = 5 THEN '廓形'
        WHEN p.fit_type_id = 2 THEN '宽松'
        ELSE '常规'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND p.category_id IN (2, 3, 6, 7, 8, 9, 10);

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '材质特征',
    CASE
        WHEN p.spu_code LIKE '%LINEN%' OR p.spu_code LIKE '%SUMMER%' OR p.spu_code LIKE '%QUICKDRY%' THEN '透气'
        WHEN p.spu_code LIKE '%WOOL%' OR p.spu_code LIKE '%PUFFER%' OR p.spu_code LIKE '%WINTER%' THEN '保暖'
        WHEN p.spu_code LIKE '%FORMAL%' OR p.spu_code LIKE '%BLAZER%' THEN '挺括'
        WHEN p.spu_code LIKE '%SKIRT%' OR p.spu_code LIKE '%RELAXED%' THEN '垂顺'
        ELSE '柔软'
    END
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137;

INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT p.id, '视觉效果', '遮肉'
FROM product_spu p
WHERE p.id BETWEEN 1101 AND 1137
  AND (
      p.category_id IN (11, 12, 14)
      OR p.fit_type_id IN (2, 7)
  );
