package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderService;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderTimeoutProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderTimeoutScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutSchedulerTests {

    @Mock
    private OrderService orderService;

    @Test
    void closeExpiredOrdersDelegatesEachCandidateWithConfiguredReason() {
        OrderTimeoutProperties properties = new OrderTimeoutProperties();
        properties.setUnpaidTimeoutMinutes(30);
        properties.setTimeoutCloseBatchSize(50);
        OrderTimeoutScheduler scheduler = new OrderTimeoutScheduler(orderService, properties);
        when(orderService.findExpiredUnpaidOrderNos(30, 50)).thenReturn(List.of("ORDTIMEOUT1", "ORDTIMEOUT2"));

        scheduler.closeExpiredOrders();

        verify(orderService).closeExpiredOrder("ORDTIMEOUT1", "TIMEOUT_UNPAID_30_MINUTES");
        verify(orderService).closeExpiredOrder("ORDTIMEOUT2", "TIMEOUT_UNPAID_30_MINUTES");
    }
}
