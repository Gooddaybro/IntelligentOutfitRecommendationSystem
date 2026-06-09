package com.recommendation.intelligentoutfitrecommendationsystem.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推荐候选商品查询参数。
 *
 * 公开商品 API 和 Python internal API 复用同一个 DTO，避免两套入口的筛选语义不一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCandidateQuery {
    private String category;
    private String style;
    private String season;
    private String material;
    private String fit;
    /**
     * 预算上限，对应商品销售价的数据库货币单位。
     */
    private Integer budgetMax;
}
