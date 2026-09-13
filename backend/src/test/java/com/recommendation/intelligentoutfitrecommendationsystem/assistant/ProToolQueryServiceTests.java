package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProToolQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductFactsQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProToolQueryServiceTests {
    private final ProRunRegistry registry = mock(ProRunRegistry.class);
    private final RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
    private final ProductFactsQuery catalog = mock(ProductFactsQuery.class);
    private final InventoryQueryService inventory = mock(InventoryQueryService.class);
    private final ProToolQueryService service = new ProToolQueryService(registry, candidates, catalog, inventory);

    @Test
    void ceilingRecallThenExactDecimalFilterRegistersOnlyReturnedPairs() {
        var below = candidate(1, "299.90");
        var above = candidate(2, "300.01");
        when(candidates.findCandidates(any())).thenAnswer(call -> {
            assertThat(((RecommendationCandidateQuery) call.getArgument(0)).getBudgetMax()).isEqualTo(300);
            return List.of(below, above);
        });
        for (String budget : List.of("299.90", "300.00")) {
            var result = service.execute("run", "secret", "search_products", Map.of("budget_max", budget));
            assertThat(result.status()).isEqualTo("ok");
            assertThat((List<?>) result.data()).hasSize(1);
            assertThat(((Map<?, ?>) ((List<?>) result.data()).getFirst()).get("sale_price")).isEqualTo("299.90");
        }
        verify(registry, org.mockito.Mockito.times(2)).registerCandidates("run", "secret", List.of(below));
    }

    @Test
    void searchLimitIsAppliedBeforeRegistration() {
        var all = LongStream.rangeClosed(1, 25).mapToObj(id -> candidate(id, "10.00")).toList();
        when(candidates.findCandidates(any())).thenReturn(all);
        var result = service.execute("run", "secret", "search_products", Map.of());
        assertThat((List<?>) result.data()).hasSize(20);
        verify(registry).registerCandidates("run", "secret", all.subList(0, 20));
    }

    @Test
    void rejectsUnknownToolsFieldsAndInvalidTypedArgumentsBeforeDataAccess() {
        for (Map<String, Object> args : List.<Map<String, Object>>of(Map.of("user_id", 99),
                Map.of("url", "https://other"), Map.of("color", "black"), Map.of("budget_max", 300),
                Map.of("budget_max", "2147483647.01"), Map.of("budget_max", "-1"),
                Map.of("budget_max", "3e2"), Map.of("category", List.of("coat")))) {
            assertThrows(BadRequestException.class,
                    () -> service.execute("run", "secret", "search_products", args));
        }
        assertThrows(BadRequestException.class,
                () -> service.execute("run", "secret", "run_sql", Map.of()));
        assertThrows(BadRequestException.class,
                () -> service.execute("run", "secret", "get_product_detail", Map.of("spu_id", "10")));
        assertThrows(BadRequestException.class,
                () -> service.execute("run", "secret", "get_product_detail", Map.of("spu_id", 1.5)));
        verifyNoInteractions(candidates, catalog, inventory);
    }

    @Test
    void invalidRunFailsBeforeCatalogAccess() {
        doThrow(new ProRunRegistry.RegistryException(ProRunRegistry.Failure.FORBIDDEN))
                .when(registry).authorize("run", "secret");
        assertThrows(ProRunRegistry.RegistryException.class,
                () -> service.execute("run", "secret", "search_products", Map.of()));
        verifyNoInteractions(candidates, catalog, inventory);
    }

    @Test
    void detailRegistersOnlySpuAndPriceRangeIsDecimal() {
        var detail = new ProductDetail();
        detail.setSpuId(10L);
        detail.setName("Jacket");
        detail.setMinPrice(new BigDecimal("299.90"));
        detail.setMaxPrice(new BigDecimal("399.00"));
        when(catalog.getProductDetail(10L)).thenReturn(detail);
        var result = service.execute("run", "secret", "get_product_detail", Map.of("spu_id", 10));
        assertThat(((Map<?, ?>) result.data()).get("min_price")).isEqualTo("299.90");
        verify(registry).registerDetail("run", "secret", 10L);
        verify(registry, never()).registerCandidates(any(), any(), any());
    }

    @Test
    void zeroStockIsSuccessfulAndRegistersResolvedPair() {
        when(catalog.findSku(10L, "black", "L")).thenReturn(
                new SkuSearchItem(20L, "SKU", 10L, "Jacket", "black", "L", new BigDecimal("299.90"), "active"));
        when(inventory.getInventoryBySkuId(20L)).thenReturn(
                new InventoryView(20L, "SKU", 10L, "Jacket", "black", "L", 0, 0, 0, false));
        var result = service.execute("run", "secret", "check_availability",
                Map.of("spu_id", 10, "color", "black", "size", "L"));
        assertThat(result.status()).isEqualTo("ok");
        assertThat(((Map<?, ?>) result.data()).get("available_stock")).isEqualTo(0);
        verify(registry).registerCandidates(eq("run"), eq("secret"), any());
    }

    @Test
    void dependencyFailureIsUnavailableWithoutRegistration() {
        when(candidates.findCandidates(any())).thenThrow(new IllegalStateException("private dependency details"));
        var result = service.execute("run", "secret", "search_products", Map.of());
        assertThat(result.status()).isEqualTo("unavailable");
        assertThat(result.data()).isNull();
        assertThat(result.toString()).doesNotContain("private dependency");
        verify(registry, never()).registerCandidates(any(), any(), any());
    }

    @Test
    void revokedRunAfterQueryCannotReturnUnregisteredFacts() {
        var found = candidate(1, "10");
        when(candidates.findCandidates(any())).thenReturn(List.of(found));
        doThrow(new ProRunRegistry.RegistryException(ProRunRegistry.Failure.FORBIDDEN))
                .when(registry).registerCandidates("run", "secret", List.of(found));
        var failure = assertThrows(ProRunRegistry.RegistryException.class,
                () -> service.execute("run", "secret", "search_products", Map.of()));
        assertThat(failure.failure()).isEqualTo(ProRunRegistry.Failure.FORBIDDEN);
    }

    @Test
    void missingSkuIsEmptyButMissingInventoryIsUnavailable() {
        when(catalog.findSku(10L, "black", "L")).thenThrow(new ResourceNotFoundException("missing"));
        assertThat(service.execute("run", "secret", "check_availability",
                Map.of("spu_id", 10, "color", "black", "size", "L")).status()).isEqualTo("empty");
        doReturn(new SkuSearchItem(20L, "SKU", 10L, "Jacket", "black", "L", BigDecimal.TEN, "active"))
                .when(catalog).findSku(10L, "black", "L");
        when(inventory.getInventoryBySkuId(20L)).thenThrow(new ResourceNotFoundException("missing"));
        assertThat(service.execute("run", "secret", "check_availability",
                Map.of("spu_id", 10, "color", "black", "size", "L")).status()).isEqualTo("unavailable");
        verify(registry, never()).registerCandidates(any(), any(), any());
    }

    static RecommendationCandidate candidate(long sku, String price) {
        var result = new RecommendationCandidate();
        result.setSpuId(10L);
        result.setSkuId(sku);
        result.setSalePrice(new BigDecimal(price));
        result.setName("Jacket");
        result.setColor("black");
        result.setSize("L");
        result.setAvailableStock(2);
        return result;
    }
}
