package com.recommendation.intelligentoutfitrecommendationsystem.aftersale;

import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.AfterSaleResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.CancelAfterSaleRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.CreateAfterSaleRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.mapper.AfterSaleMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.model.AfterSaleRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.service.AfterSaleService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService.OrderView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AfterSaleServiceTests {

    @Mock
    private AfterSaleMapper afterSaleMapper;

    @Mock
    private OrderApplicationService orderApplicationService;

    @InjectMocks
    private AfterSaleService service;

    @Test
    void createRequestForPaidOrderUsesJavaOwnedOrderAmount() {
        OrderView order = order(88L, 10L, "ORDPAID1", "PAID");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDPAID1")).thenReturn(order);
        when(afterSaleMapper.findOpenByOrderId(88L)).thenReturn(null);

        AfterSaleResponse response = service.create(10L, new CreateAfterSaleRequest(
                "ORDPAID1",
                "REFUND",
                "尺码不合适"
        ));

        ArgumentCaptor<AfterSaleRequest> requestCaptor = ArgumentCaptor.forClass(AfterSaleRequest.class);
        verify(afterSaleMapper).insert(requestCaptor.capture());
        AfterSaleRequest saved = requestCaptor.getValue();
        assertThat(saved.getRequestNo()).startsWith("AS");
        assertThat(saved.getOrderId()).isEqualTo(88L);
        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getRefundAmount()).isEqualByComparingTo("299.00");
        assertThat(saved.getStatus()).isEqualTo("REQUESTED");
        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.refundAmount()).isEqualByComparingTo("299.00");
    }

    @Test
    void createRequestRejectsUnpaidOrder() {
        OrderView order = order(88L, 10L, "ORDUNPAID1", "UNPAID");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDUNPAID1")).thenReturn(order);

        assertThatThrownBy(() -> service.create(10L, new CreateAfterSaleRequest(
                "ORDUNPAID1",
                "REFUND",
                "还没支付"
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("only paid orders can request after-sale service: ORDUNPAID1");
    }

    @Test
    void createRequestRejectsDuplicateOpenRequest() {
        OrderView order = order(88L, 10L, "ORDPAID1", "PAID");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDPAID1")).thenReturn(order);
        when(afterSaleMapper.findOpenByOrderId(88L)).thenReturn(afterSale(1L, 88L, 10L, "ORDPAID1", "REQUESTED"));

        assertThatThrownBy(() -> service.create(10L, new CreateAfterSaleRequest(
                "ORDPAID1",
                "REFUND",
                "重复申请"
        )))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("after-sale request already exists for order: ORDPAID1");
    }

    @Test
    void cancelRequestedAfterSaleOwnedByCurrentUser() {
        AfterSaleRequest request = afterSale(1L, 88L, 10L, "ORDPAID1", "REQUESTED");
        when(afterSaleMapper.findByUserIdAndRequestNoForUpdate(10L, "AS1")).thenReturn(request);
        when(afterSaleMapper.updateStatus(1L, "CANCELLED", "用户撤销")).thenReturn(1);

        AfterSaleResponse response = service.cancel(10L, "AS1", new CancelAfterSaleRequest("用户撤销"));

        verify(afterSaleMapper).updateStatus(1L, "CANCELLED", "用户撤销");
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.handlerNote()).isEqualTo("用户撤销");
    }

    @Test
    void cancelRejectsAnotherUsersRequest() {
        when(afterSaleMapper.findByUserIdAndRequestNoForUpdate(11L, "AS1")).thenReturn(null);

        assertThatThrownBy(() -> service.cancel(11L, "AS1", new CancelAfterSaleRequest("不是我的")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("after-sale request not found: AS1");
    }

    @Test
    void listReturnsCurrentUsersRequestsOnly() {
        when(afterSaleMapper.findByUserId(10L)).thenReturn(List.of(afterSale(1L, 88L, 10L, "ORDPAID1", "REQUESTED")));

        List<AfterSaleResponse> responses = service.list(10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).orderNo()).isEqualTo("ORDPAID1");
    }

    private OrderView order(Long id, Long userId, String orderNo, String status) {
        return new OrderView(id, userId, orderNo, new BigDecimal("299.00"), status);
    }

    private AfterSaleRequest afterSale(Long id, Long orderId, Long userId, String orderNo, String status) {
        AfterSaleRequest request = new AfterSaleRequest();
        request.setId(id);
        request.setRequestNo("AS1");
        request.setOrderId(orderId);
        request.setOrderNo(orderNo);
        request.setUserId(userId);
        request.setType("REFUND");
        request.setReason("尺码不合适");
        request.setStatus(status);
        request.setRefundAmount(new BigDecimal("299.00"));
        request.setCreatedAt(LocalDateTime.of(2026, 7, 5, 21, 40));
        return request;
    }
}
