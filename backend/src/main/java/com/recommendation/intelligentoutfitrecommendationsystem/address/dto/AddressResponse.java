package com.recommendation.intelligentoutfitrecommendationsystem.address.dto;

import com.recommendation.intelligentoutfitrecommendationsystem.address.model.UserAddress;

/**
 * 地址簿公开响应，不暴露内部 userId 和持久化时间字段。
 */
public record AddressResponse(
        Long id,
        String recipientName,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        boolean isDefault
) {

    public static AddressResponse from(UserAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getPhone(),
                address.getProvince(),
                address.getCity(),
                address.getDistrict(),
                address.getDetail(),
                Boolean.TRUE.equals(address.getIsDefault())
        );
    }
}
