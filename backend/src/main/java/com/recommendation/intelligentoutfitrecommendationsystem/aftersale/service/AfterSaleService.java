package com.recommendation.intelligentoutfitrecommendationsystem.aftersale.service;

import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.AfterSaleResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.CancelAfterSaleRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto.CreateAfterSaleRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.mapper.AfterSaleMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.model.AfterSaleRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService.OrderView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 当前用户售后申请服务。
 *
 * 本阶段只建立售后状态边界，不执行真实退款；所有申请必须绑定当前用户自己的已支付订单，
 * 退款金额从订单快照读取，不能由前端提交。
 */
@Service
public class AfterSaleService {

    private static final String PAID_STATUS = "PAID";

    private static final String REQUESTED_STATUS = "REQUESTED";

    private static final String CANCELLED_STATUS = "CANCELLED";

    private static final String DEFAULT_CANCEL_REASON = "USER_CANCELLED";

    private static final int MAX_REASON_LENGTH = 255;

    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AfterSaleMapper afterSaleMapper;

    private final OrderApplicationService orderApplicationService;

    public AfterSaleService(AfterSaleMapper afterSaleMapper, OrderApplicationService orderApplicationService) {
        this.afterSaleMapper = afterSaleMapper;
        this.orderApplicationService = orderApplicationService;
    }

    @Transactional
    public AfterSaleResponse create(Long userId, CreateAfterSaleRequest request) {
        validateUserId(userId);
        String orderNo = normalizeOrderNo(request == null ? null : request.orderNo());
        String type = normalizeType(request == null ? null : request.type());
        String reason = normalizeRequiredText(request == null ? null : request.reason(), "reason");

        OrderView order = orderApplicationService.lockOwnedOrder(userId, orderNo);
        if (order == null) {
            throw new ResourceNotFoundException("order not found: " + orderNo);
        }
        if (!PAID_STATUS.equals(order.status())) {
            throw new BadRequestException("only paid orders can request after-sale service: " + orderNo);
        }
        if (afterSaleMapper.findOpenByOrderId(order.id()) != null) {
            throw new BadRequestException("after-sale request already exists for order: " + orderNo);
        }

        AfterSaleRequest afterSale = new AfterSaleRequest();
        afterSale.setRequestNo(generateRequestNo());
        afterSale.setOrderId(order.id());
        afterSale.setOrderNo(order.orderNo());
        afterSale.setUserId(order.userId());
        afterSale.setType(type);
        afterSale.setReason(reason);
        afterSale.setStatus(REQUESTED_STATUS);
        afterSale.setRefundAmount(order.totalAmount());
        afterSaleMapper.insert(afterSale);
        return toResponse(afterSale);
    }

    @Transactional(readOnly = true)
    public List<AfterSaleResponse> list(Long userId) {
        validateUserId(userId);
        return afterSaleMapper.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AfterSaleResponse cancel(Long userId, String requestNo, CancelAfterSaleRequest request) {
        validateUserId(userId);
        String normalizedRequestNo = normalizeRequestNo(requestNo);
        AfterSaleRequest afterSale = afterSaleMapper.findByUserIdAndRequestNoForUpdate(userId, normalizedRequestNo);
        if (afterSale == null) {
            throw new ResourceNotFoundException("after-sale request not found: " + normalizedRequestNo);
        }
        if (CANCELLED_STATUS.equals(afterSale.getStatus())) {
            return toResponse(afterSale);
        }
        if (!REQUESTED_STATUS.equals(afterSale.getStatus())) {
            throw new BadRequestException("after-sale request cannot be cancelled: " + normalizedRequestNo);
        }

        String reason = normalizeOptionalText(request == null ? null : request.reason(), DEFAULT_CANCEL_REASON);
        int updatedRows = afterSaleMapper.updateStatus(afterSale.getId(), CANCELLED_STATUS, reason);
        if (updatedRows == 0) {
            throw new BadRequestException("after-sale status changed before cancel: " + normalizedRequestNo);
        }
        afterSale.setStatus(CANCELLED_STATUS);
        afterSale.setHandlerNote(reason);
        afterSale.setHandledAt(LocalDateTime.now());
        return toResponse(afterSale);
    }

    private AfterSaleResponse toResponse(AfterSaleRequest request) {
        return new AfterSaleResponse(
                request.getRequestNo(),
                request.getOrderNo(),
                request.getType(),
                request.getReason(),
                request.getStatus(),
                request.getRefundAmount(),
                request.getHandlerNote(),
                request.getHandledAt(),
                request.getCreatedAt()
        );
    }

    private String generateRequestNo() {
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "AS" + LocalDateTime.now().format(REQUEST_TIME_FORMATTER) + suffix;
    }

    private String normalizeOrderNo(String orderNo) {
        return normalizeRequiredText(orderNo, "orderNo");
    }

    private String normalizeRequestNo(String requestNo) {
        return normalizeRequiredText(requestNo, "requestNo");
    }

    private String normalizeType(String type) {
        String normalized = normalizeRequiredText(type, "type").toUpperCase(Locale.ROOT);
        if (!"REFUND".equals(normalized) && !"RETURN_REFUND".equals(normalized)) {
            throw new BadRequestException("after-sale type is not supported: " + normalized);
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException(fieldName + " is too long");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim();
        return normalized.length() > MAX_REASON_LENGTH ? normalized.substring(0, MAX_REASON_LENGTH) : normalized;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }
}
