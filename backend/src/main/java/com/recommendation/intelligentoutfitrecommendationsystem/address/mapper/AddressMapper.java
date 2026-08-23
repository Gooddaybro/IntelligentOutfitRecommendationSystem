package com.recommendation.intelligentoutfitrecommendationsystem.address.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.address.model.UserAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户地址簿的数据访问边界。
 *
 * 单条读取和所有写操作都以 addressId + userId 限定所有权；写事务先锁定用户行，
 * 将同一用户的首地址创建、默认切换和默认补位串行化。
 */
@Mapper
public interface AddressMapper {

    /**
     * 锁定稳定存在的父用户行，作为该用户全部地址写事务的串行化边界。
     *
     * @return 已锁定的用户 ID；用户不存在时返回 {@code null}
     */
    Long lockUserById(@Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);

    void insert(UserAddress address);

    List<UserAddress> findAllByUserId(@Param("userId") Long userId);

    UserAddress findByIdAndUserId(@Param("addressId") Long addressId, @Param("userId") Long userId);

    int updateByIdAndUserId(UserAddress address);

    int deleteByIdAndUserId(@Param("addressId") Long addressId, @Param("userId") Long userId);

    int clearDefaultByUserId(@Param("userId") Long userId);

    int setDefaultByIdAndUserId(@Param("addressId") Long addressId, @Param("userId") Long userId);

    /**
     * 按更新时间和 ID 的稳定倒序选择删除默认地址后的候补项。
     *
     * @return 候补地址；地址簿已空时返回 {@code null}
     */
    UserAddress findReplacementByUserId(@Param("userId") Long userId);
}
