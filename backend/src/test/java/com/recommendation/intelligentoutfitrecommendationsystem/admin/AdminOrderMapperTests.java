package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminOrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderRow;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class AdminOrderMapperTests {

    @Autowired
    private AdminOrderMapper mapper;

    @Test
    @Transactional
    void onlyPaidOrderCanTransitionToShipped() {
        AdminOrderState paid = mapper.findOrderStateByOrderNo("ORDDEMO9001PAID");
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(mapper.markOrderShipped(paid.orderId())).isEqualTo(1);
        assertThat(mapper.markOrderShipped(paid.orderId())).isZero();
    }

    @Test
    void mapsShipmentAndPaymentProjection() {
        AdminOrderRow row = mapper.findOrderByOrderNo("ORDDEMO9001PAID");
        assertThat(row.orderNo()).isEqualTo("ORDDEMO9001PAID");
        assertThat(row.paymentStatus()).isEqualTo("PAID");
        assertThat(row.carrier()).isNull();
        assertThat(row.trackingNo()).isNull();
    }
}
