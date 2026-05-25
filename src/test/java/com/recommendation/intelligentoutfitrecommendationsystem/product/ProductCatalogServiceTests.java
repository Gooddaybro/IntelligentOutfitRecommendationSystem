package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.repository.ProductQueryRepository;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTests {

    @Mock
    private ProductQueryRepository productQueryRepository;

    @InjectMocks
    private ProductCatalogService service;

    @Test
    void findSkuNormalizesSizeBeforeQueryingRepository() {
        when(productQueryRepository.findSku(1001L, "黑色", "L"))
                .thenReturn(Optional.of(new SkuSearchItem(2003L, "TS-BASIC-001-BLK-L", 1001L, "基础款纯棉T恤", "黑色", "L", BigDecimal.valueOf(99), "on_sale")));

        var sku = service.findSku(1001L, "黑色", " l ");

        assertThat(sku.skuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        verify(productQueryRepository).findSku(1001L, "黑色", "L");
    }

    @Test
    void findSkuRejectsBlankColor() {
        assertThatThrownBy(() -> service.findSku(1001L, " ", "L"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("color must not be blank");
    }
}
