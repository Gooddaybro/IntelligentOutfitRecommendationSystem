package com.recommendation.intelligentoutfitrecommendationsystem.address.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户地址簿中的可变地址记录。
 *
 * 该模型只代表当前地址簿事实；订单创建时必须复制为不可变地址快照，不能让历史订单
 * 动态引用这条可能被修改或删除的记录。
 */
@Data
public class UserAddress {

    private Long id;

    private Long userId;

    private String recipientName;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String detail;

    private Boolean isDefault;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
