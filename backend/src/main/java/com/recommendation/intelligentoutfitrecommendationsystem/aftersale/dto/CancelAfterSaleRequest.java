package com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto;

import jakarta.validation.constraints.Size;

/**
 * 用户撤销售后申请的请求契约。
 *
 * 该字段只作为撤销说明，不参与权限判断；售后单归属仍由 JWT userId 和 requestNo 共同限定。
 */
public record CancelAfterSaleRequest(
        @Size(max = 255) String reason
) {
}
