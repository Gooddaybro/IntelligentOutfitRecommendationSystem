package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品搜索 Outbox 的发布租约、状态和重建水位数据边界。
 */
@Mapper
public interface ProductSearchOutboxMapper {
    int insert(ProductSearchOutboxEvent event);

    List<ProductSearchOutboxEvent> findPublishable(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    int claim(@Param("eventId") String eventId, @Param("claimedBy") String claimedBy,
              @Param("now") LocalDateTime now, @Param("claimUntil") LocalDateTime claimUntil);

    int markPublished(@Param("eventId") String eventId, @Param("claimedBy") String claimedBy,
                      @Param("publishedAt") LocalDateTime publishedAt);

    int releaseClaim(@Param("eventId") String eventId, @Param("claimedBy") String claimedBy,
                     @Param("lastError") String lastError);

    Long findMaxId();

    List<Long> findDistinctSpuIdsInRange(
            @Param("afterId") long afterId, @Param("throughId") long throughId);
}
