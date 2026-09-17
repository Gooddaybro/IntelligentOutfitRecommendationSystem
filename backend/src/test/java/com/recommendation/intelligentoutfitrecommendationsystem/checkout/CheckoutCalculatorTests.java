package com.recommendation.intelligentoutfitrecommendationsystem.checkout;

import com.recommendation.intelligentoutfitrecommendationsystem.address.service.AddressService;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.mapper.CheckoutMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutFactRow;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.service.CheckoutCalculator;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutCalculatorTests {

    @Mock
    private CheckoutMapper checkoutMapper;

    @Mock
    private AddressService addressService;

    @InjectMocks
    private CheckoutCalculator calculator;

    @Test
    void previewUsesServerQuantityAndCurrentPriceWithMoneyRounding() {
        CheckoutFactRow row = row(2102L, "10.005", 3, "on_sale", "on_sale", 8);
        when(checkoutMapper.findCartFacts(10L, List.of(2102L))).thenReturn(List.of(row));

        var result = calculator.previewCart(10L, List.of(2102L), 31L);

        verify(addressService).requireOwnedAddress(10L, 31L);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualTo(3);
            assertThat(item.salePrice()).isEqualByComparingTo("10.01");
            assertThat(item.lineAmount()).isEqualByComparingTo("30.03");
        });
        assertThat(result.merchandiseAmount()).isEqualByComparingTo("30.03");
        assertThat(result.shippingAmount()).isEqualByComparingTo("0.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.payableAmount()).isEqualByComparingTo("30.03");
        assertThat(result.invalidReasons()).isEmpty();
        verify(checkoutMapper, never()).findCartFactsForUpdate(10L, List.of(2102L));
    }

    @Test
    void orderCalculationReadsTradeFactsWithSingleLockingCurrentQuery() {
        CheckoutFactRow row = row(2102L, "299.00", 1, "on_sale", "on_sale", 10);
        when(checkoutMapper.findCartFactsForUpdate(10L, List.of(2102L))).thenReturn(List.of(row));

        calculator.calculateForOrder(10L, List.of(2102L), 31L);

        verify(checkoutMapper).findCartFactsForUpdate(10L, List.of(2102L));
        verify(checkoutMapper, never()).findCartFacts(10L, List.of(2102L));
    }

    @Test
    void previewExplainsUnavailableProductAndInsufficientStock() {
        CheckoutFactRow unavailable = row(2102L, "299.00", 1, "off_sale", "on_sale", 10);
        CheckoutFactRow insufficient = row(2202L, "199.00", 4, "on_sale", "on_sale", 2);
        when(checkoutMapper.findCartFacts(10L, List.of(2102L, 2202L)))
                .thenReturn(List.of(unavailable, insufficient));

        var result = calculator.previewCart(10L, List.of(2102L, 2202L), 31L);

        assertThat(result.invalidReasons())
                .extracting(reason -> reason.code())
                .containsExactly("SKU_UNAVAILABLE", "INSUFFICIENT_STOCK");
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void orderCalculationRejectsEveryPreviewInvalidReason() {
        List.of(
                row(2202L, "199.00", 4, "on_sale", "on_sale", 2),
                row(2203L, "199.00", 1, "off_sale", "on_sale", 2),
                row(2204L, "199.00", 1, "on_sale", "off_sale", 2)
        ).forEach(invalidRow -> {
            when(checkoutMapper.findCartFactsForUpdate(10L, List.of(invalidRow.getSkuId())))
                    .thenReturn(List.of(invalidRow));

            assertThatThrownBy(() -> calculator.calculateForOrder(
                    10L,
                    List.of(invalidRow.getSkuId()),
                    31L
            ))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining(invalidRow.getSkuId().toString());
        });
    }

    @Test
    void foreignAddressFailsBeforeCartFactsAreRead() {
        when(addressService.requireOwnedAddress(10L, 999L))
                .thenThrow(new ResourceNotFoundException("address not found"));

        assertThatThrownBy(() -> calculator.previewCart(10L, List.of(2102L), 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("address not found");

        verify(checkoutMapper, never()).findCartFacts(10L, List.of(2102L));
    }

    @Test
    void missingSelectedCartItemIsNotTreatedAsAValidEmptyPreview() {
        when(checkoutMapper.findCartFacts(10L, List.of(2102L, 2202L)))
                .thenReturn(List.of(row(2102L, "299.00", 1, "on_sale", "on_sale", 10)));

        assertThatThrownBy(() -> calculator.previewCart(10L, List.of(2102L, 2202L), 31L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("cart item not found");
    }

    @Test
    void duplicateSkuSelectionIsNormalizedBeforeReadingFacts() {
        CheckoutFactRow row = row(2102L, "299.00", 1, "on_sale", "on_sale", 10);
        when(checkoutMapper.findCartFacts(10L, List.of(2102L))).thenReturn(List.of(row));

        var result = calculator.previewCart(10L, List.of(2102L, 2102L), 31L);

        assertThat(result.items()).hasSize(1);
        verify(checkoutMapper).findCartFacts(10L, List.of(2102L));
    }

    private CheckoutFactRow row(
            Long skuId,
            String salePrice,
            int quantity,
            String skuStatus,
            String spuStatus,
            int availableStock
    ) {
        CheckoutFactRow row = new CheckoutFactRow();
        row.setSkuId(skuId);
        row.setSpuId(1000L + skuId);
        row.setSkuCode("SKU-" + skuId);
        row.setSpuCode("SPU-" + skuId);
        row.setProductName("测试商品 " + skuId);
        row.setCategoryName("上装");
        row.setColor("黑色");
        row.setSize("L");
        row.setSalePrice(new BigDecimal(salePrice));
        row.setQuantity(quantity);
        row.setMainImageUrl("/images/" + skuId + ".svg");
        row.setSkuStatus(skuStatus);
        row.setSpuStatus(spuStatus);
        row.setAvailableStock(availableStock);
        return row;
    }
}
