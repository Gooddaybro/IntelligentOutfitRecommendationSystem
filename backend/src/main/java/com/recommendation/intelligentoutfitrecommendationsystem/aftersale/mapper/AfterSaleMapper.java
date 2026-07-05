package com.recommendation.intelligentoutfitrecommendationsystem.aftersale.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.aftersale.model.AfterSaleRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 售后申请数据访问入口。
 *
 * 公开查询和取消入口必须同时绑定 userId，避免用户通过猜测 requestNo 横向读取或撤销
 * 他人的售后申请。
 */
@Mapper
public interface AfterSaleMapper {

    void insert(AfterSaleRequest request);

    AfterSaleRequest findOpenByOrderId(@Param("orderId") Long orderId);

    List<AfterSaleRequest> findByUserId(@Param("userId") Long userId);

    AfterSaleRequest findByUserIdAndRequestNoForUpdate(
            @Param("userId") Long userId,
            @Param("requestNo") String requestNo
    );

    int updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("handlerNote") String handlerNote
    );
}
