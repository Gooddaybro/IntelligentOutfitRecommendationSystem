UPDATE product_spu
SET main_image_url = '/images/products/tshirt-basic-main.svg'
WHERE id = 1001;

UPDATE product_spu
SET main_image_url = '/images/products/jacket-commute-main.svg'
WHERE id = 1002;

UPDATE product_spu
SET main_image_url = '/images/products/pants-straight-main.svg'
WHERE id = 1003;

UPDATE product_image
SET image_url = '/images/products/tshirt-basic-main.svg'
WHERE spu_id = 1001 AND sku_id IS NULL AND image_type = 'main';

UPDATE product_image
SET image_url = '/images/products/jacket-commute-main.svg'
WHERE spu_id = 1002 AND sku_id IS NULL AND image_type = 'main';

UPDATE product_image
SET image_url = '/images/products/pants-straight-main.svg'
WHERE spu_id = 1003 AND sku_id IS NULL AND image_type = 'main';
