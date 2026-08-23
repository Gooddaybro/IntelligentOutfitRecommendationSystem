package com.recommendation.intelligentoutfitrecommendationsystem.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建或修改收货地址时允许客户端提交的字段。
 *
 * 请求故意不包含 userId 和默认状态：用户归属来自 JWT，默认地址只能通过独立事务接口切换。
 */
public record AddressSaveRequest(
        @NotBlank @Size(max = 64) String recipientName,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 64) String province,
        @NotBlank @Size(max = 64) String city,
        @NotBlank @Size(max = 64) String district,
        @NotBlank @Size(max = 255) String detail
) {
}
