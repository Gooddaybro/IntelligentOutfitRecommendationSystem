package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

/**
 * 表示主搜索引擎暂时不可用、允许回退到 MySQL 的异常。
 *
 * <p>查询语法或映射错误不应包装成此异常，否则真实缺陷会被降级逻辑掩盖。</p>
 */
public class ProductSearchUnavailableException extends RuntimeException {

    public ProductSearchUnavailableException(String message) {
        super(message);
    }

    public ProductSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
