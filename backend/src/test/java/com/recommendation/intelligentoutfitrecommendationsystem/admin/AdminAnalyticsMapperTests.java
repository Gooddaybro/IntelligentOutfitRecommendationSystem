package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAnalyticsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class AdminAnalyticsMapperTests {
    @Autowired
    private AdminAnalyticsMapper mapper;

    @Test
    void readsCountsMoneyTrendAndHotProducts() {
        assertThat(mapper.countOnSaleProducts()).isNotNull();
        assertThat(mapper.countOrders()).isPositive();
        assertThat(mapper.sumPaidAmount()).isNotNull();
        assertThat(mapper.findOrderTrendRows()).isNotEmpty();
        assertThat(mapper.findAnalyticsHotProducts()).isNotNull();
        assertThat(mapper.findCategoryTrend()).isNotNull();
    }

    @Test
    void funnelQueriesReturnZeroInsteadOfNull() {
        assertThat(mapper.countExposureEvents()).isNotNull();
        assertThat(mapper.countClickEvents()).isNotNull();
        assertThat(mapper.countCartEvents()).isNotNull();
        assertThat(mapper.countPurchasedOrders()).isNotNull();
    }
}
