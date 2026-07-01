INSERT INTO product_attribute (spu_id, attr_name, attr_value)
SELECT
    p.id,
    '适用性别',
    CASE
        WHEN p.spu_code LIKE '%SKIRT%' THEN 'female'
        WHEN p.spu_code LIKE 'OXFORD_SHIRT_%' THEN 'male'
        ELSE 'unisex'
    END
FROM product_spu p
WHERE NOT EXISTS (
    SELECT 1
    FROM product_attribute pa
    WHERE pa.spu_id = p.id
      AND pa.attr_name = '适用性别'
);
