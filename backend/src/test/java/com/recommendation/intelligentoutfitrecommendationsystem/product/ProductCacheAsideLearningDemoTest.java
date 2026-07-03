package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Disabled("Learning demo: implement studentGetProductDetail, then remove @Disabled.")
class ProductCacheAsideLearningDemoTest {

    @Autowired
    private ProductMapper productMapper;

    @Test
    void productDetailCacheAsideUsesRealProductData() {
        var cache = new LearningProductCache();
        var dataSource = new PreparedProductDataSource(productMapper);

        ProductDetail first = studentGetProductDetail(1001L, cache, dataSource);
        ProductDetail second = studentGetProductDetail(1001L, cache, dataSource);

        assertThat(first.getSpuCode()).isEqualTo("TSHIRT_BASIC_001");
        assertThat(second.getSpuCode()).isEqualTo("TSHIRT_BASIC_001");
        assertThat(first.getMaterials()).isNotEmpty();
        assertThat(first.getStyleTags()).contains("casual");
        assertThat(cache.keys()).containsExactly("product:detail:1001");
        assertThat(dataSource.databaseReadCount()).isEqualTo(1);
    }

    private ProductDetail studentGetProductDetail(
            Long spuId,
            LearningProductCache cache,
            PreparedProductDataSource dataSource
    ) {
        String key=LearningProductCache.productDetailKey(spuId);
        Optional<ProductDetail> productDetail = cache.get(key);
        if (productDetail.isPresent()) {
            return productDetail.get();
        }
        ProductDetail productDetail1 = dataSource.findProductDetail(spuId);
        cache.put(key, productDetail1);

        return productDetail1;
    }

    private static final class LearningProductCache {

        private final Map<String, ProductDetail> values = new LinkedHashMap<>();

        static String productDetailKey(Long spuId) {
            return "product:detail:" + spuId;
        }

        Optional<ProductDetail> get(String key) {
            return Optional.ofNullable(values.get(key));
        }

        void put(String key, ProductDetail detail) {
            values.put(key, detail);
        }

        Iterable<String> keys() {
            return values.keySet();
        }
    }

    private static final class PreparedProductDataSource {

        private final ProductMapper productMapper;
        private int databaseReadCount;

        private PreparedProductDataSource(ProductMapper productMapper) {
            this.productMapper = productMapper;
        }

        ProductDetail findProductDetail(Long spuId) {
            databaseReadCount++;
            ProductDetail detail = productMapper.findProductDetailBase(spuId);
            if (detail == null) {
                throw new ResourceNotFoundException("product not found: " + spuId);
            }
            detail.setMaterials(productMapper.findMaterials(spuId));
            detail.setSeasons(productMapper.findSeasons(spuId));
            detail.setStyleTags(productMapper.findStyleTags(spuId));
            detail.setAttributes(toAttributesMap(productMapper.findAttributes(spuId)));
            return detail;
        }

        int databaseReadCount() {
            return databaseReadCount;
        }

        private Map<String, String> toAttributesMap(Iterable<ProductAttributeItem> attributes) {
            Map<String, String> result = new LinkedHashMap<>();
            for (ProductAttributeItem attribute : attributes) {
                result.put(attribute.getAttrName(), attribute.getAttrValue());
            }
            return result;
        }
    }
}
