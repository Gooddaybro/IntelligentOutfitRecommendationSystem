package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

import java.util.ArrayList;
import java.util.List;

/**
 * 绑定可选 Elasticsearch 商品搜索副本的连接、索引和批处理约束。
 *
 * <p>关闭开关时应用只使用 MySQL，保证没有 Elasticsearch 的测试和部署环境仍能启动。</p>
 */
@ConfigurationProperties(prefix = "app.elasticsearch")
@Validated
public class ElasticsearchSearchProperties {
    private boolean enabled;
    private List<String> uris = new ArrayList<>(List.of("http://localhost:9200"));
    private String indexAlias = "product_current";
    private String indexPrefix = "product_";
    private int connectTimeoutMs = 1000;
    private int socketTimeoutMs = 2000;
    private int searchLimit = 500;
    private int bulkBatchSize = 200;
    @Min(0)
    private int retainedHistoryCount = 2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getUris() {
        return List.copyOf(uris);
    }

    public void setUris(List<String> uris) {
        this.uris = new ArrayList<>(uris);
    }

    public String getIndexAlias() {
        return indexAlias;
    }

    public void setIndexAlias(String indexAlias) {
        this.indexAlias = indexAlias;
    }

    public String getIndexPrefix() {
        return indexPrefix;
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        this.socketTimeoutMs = socketTimeoutMs;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    public int getBulkBatchSize() {
        return bulkBatchSize;
    }

    public void setBulkBatchSize(int bulkBatchSize) {
        this.bulkBatchSize = bulkBatchSize;
    }

    public int getRetainedHistoryCount() {
        return retainedHistoryCount;
    }

    public void setRetainedHistoryCount(int retainedHistoryCount) {
        this.retainedHistoryCount = retainedHistoryCount;
    }
}
