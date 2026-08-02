package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache.ProductSearchCacheVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class ProductSearchCacheVersionPersistenceTests {
    @Autowired
    private ProductSearchCacheVersionService versionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void restoreVersionState() {
        jdbcTemplate.update("DELETE FROM product_search_cache_state");
        jdbcTemplate.update("INSERT INTO product_search_cache_state (id, generation) VALUES (1, 1)");
    }

    @Test
    void startsAtVersionOne() {
        assertThat(versionService.currentVersion()).isEqualTo(1L);
    }

    @Test
    void incrementsVersionConsecutively() {
        versionService.incrementVersion();
        versionService.incrementVersion();

        assertThat(versionService.currentVersion()).isEqualTo(3L);
    }

    @Test
    void rejectsMissingVersionState() {
        jdbcTemplate.update("DELETE FROM product_search_cache_state");

        assertThatThrownBy(versionService::currentVersion)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cache version");
    }

    @Test
    void rejectsIncrementWhenVersionStateIsMissing() {
        jdbcTemplate.update("DELETE FROM product_search_cache_state");

        assertThatThrownBy(versionService::incrementVersion)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cache version");
    }
}
