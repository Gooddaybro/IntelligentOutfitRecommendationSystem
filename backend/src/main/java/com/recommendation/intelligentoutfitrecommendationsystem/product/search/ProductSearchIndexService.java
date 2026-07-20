package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchRebuildCompensator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 从 MySQL 构建新商品索引，验证后原子切换查询别名。
 *
 * <p>重建始终写入新物理索引，失败时旧别名保持不变，因此线上搜索不会读到半成品。</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
public class ProductSearchIndexService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductSearchIndexService.class);
    private static final DateTimeFormatter INDEX_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final ElasticsearchClient client;
    private final ProductMapper productMapper;
    private final ElasticsearchSearchProperties properties;
    private final ProductSearchIndexLifecycleService lifecycleService;
    private final Optional<ProductSearchRebuildCompensator> rebuildCompensator;
    private final Clock clock;
    private final AtomicBoolean rebuilding = new AtomicBoolean();

    @Autowired
    public ProductSearchIndexService(
            ElasticsearchClient client,
            ProductMapper productMapper,
            ElasticsearchSearchProperties properties,
            ProductSearchIndexLifecycleService lifecycleService,
            Optional<ProductSearchRebuildCompensator> rebuildCompensator
    ) {
        this(client, productMapper, properties, lifecycleService, rebuildCompensator, Clock.systemUTC());
    }

    public ProductSearchIndexService(
            ElasticsearchClient client,
            ProductMapper productMapper,
            ElasticsearchSearchProperties properties,
            ProductSearchIndexLifecycleService lifecycleService
    ) {
        this(client, productMapper, properties, lifecycleService, Optional.empty(), Clock.systemUTC());
    }

    ProductSearchIndexService(
            ElasticsearchClient client,
            ProductMapper productMapper,
            ElasticsearchSearchProperties properties,
            ProductSearchIndexLifecycleService lifecycleService,
            Clock clock
    ) {
        this(client, productMapper, properties, lifecycleService, Optional.empty(), clock);
    }

    ProductSearchIndexService(
            ElasticsearchClient client,
            ProductMapper productMapper,
            ElasticsearchSearchProperties properties,
            ProductSearchIndexLifecycleService lifecycleService,
            Optional<ProductSearchRebuildCompensator> rebuildCompensator,
            Clock clock
    ) {
        this.client = client;
        this.productMapper = productMapper;
        this.properties = properties;
        this.lifecycleService = lifecycleService;
        this.rebuildCompensator = rebuildCompensator;
        this.clock = clock;
    }

    /**
     * 执行一次互斥的全量重建。
     *
     * @return 新索引、文档数量和别名
     */
    public ProductSearchRebuildResult rebuild() {
        if (!rebuilding.compareAndSet(false, true)) {
            throw new BadRequestException("商品搜索索引正在重建，请勿重复提交");
        }

        Instant rebuiltAt = clock.instant();
        String indexName = properties.getIndexPrefix() + INDEX_TIME.format(rebuiltAt);
        boolean indexCreated = false;
        try {
            long startWatermark = rebuildCompensator
                    .map(ProductSearchRebuildCompensator::captureWatermark)
                    .orElse(0L);
            List<ProductSearchIndexRow> rows = productMapper.findAllSearchIndexRows();
            createIndex(indexName);
            indexCreated = true;
            bulkIndex(indexName, rows, rebuiltAt);
            client.indices().refresh(request -> request.index(indexName));
            long actualCount = client.count(request -> request.index(indexName)).count();
            if (actualCount != rows.size()) {
                throw new IllegalStateException(
                        "商品索引文档数量不一致，预期 " + rows.size() + "，实际 " + actualCount);
            }
            switchAlias(indexName);
            rebuildCompensator.ifPresent(compensator -> compensator.compensateAfter(startWatermark));
            pruneHistoryWithoutBreakingRebuild();
            return new ProductSearchRebuildResult(indexName, actualCount, properties.getIndexAlias());
        } catch (IOException exception) {
            ProductSearchUnavailableException failure =
                    new ProductSearchUnavailableException("商品搜索索引重建失败", exception);
            cleanupFailedIndex(indexName, indexCreated, failure);
            throw failure;
        } catch (RuntimeException exception) {
            cleanupFailedIndex(indexName, indexCreated, exception);
            throw exception;
        } finally {
            rebuilding.set(false);
        }
    }

    private void cleanupFailedIndex(String indexName, boolean indexCreated, RuntimeException failure) {
        if (!indexCreated) {
            return;
        }
        try {
            lifecycleService.deleteFailedIndex(indexName);
        } catch (RuntimeException cleanupException) {
            // 二次清理失败不能覆盖 Bulk、数量校验等真正的重建失败原因。
            failure.addSuppressed(cleanupException);
            LOGGER.warn("商品索引重建失败后无法删除半成品索引 {}", indexName, cleanupException);
        }
    }

    private void pruneHistoryWithoutBreakingRebuild() {
        try {
            lifecycleService.pruneHistory();
        } catch (RuntimeException cleanupException) {
            // 此时别名已成功切换；清理失败只影响磁盘回收，不应把成功重建报告为失败。
            LOGGER.warn("商品索引已切换，但历史索引清理失败", cleanupException);
        }
    }

    private void createIndex(String indexName) throws IOException {
        ClassPathResource mapping = new ClassPathResource("elasticsearch/product-index.json");
        try (InputStream input = mapping.getInputStream()) {
            client.indices().create(request -> request.index(indexName).withJson(input));
        }
    }

    private void bulkIndex(String indexName, List<ProductSearchIndexRow> rows, Instant rebuiltAt)
            throws IOException {
        int batchSize = properties.getBulkBatchSize();
        for (int start = 0; start < rows.size(); start += batchSize) {
            int end = Math.min(start + batchSize, rows.size());
            BulkRequest.Builder request = new BulkRequest.Builder();
            for (ProductSearchIndexRow row : rows.subList(start, end)) {
                ProductSearchDocument document = row.toDocument(rebuiltAt);
                request.operations(operation -> operation.index(index -> index
                        .index(indexName)
                        .id(String.valueOf(row.spuId()))
                        .document(document)));
            }
            BulkResponse response = client.bulk(request.build());
            if (response.errors()) {
                String reason = response.items().stream()
                        .filter(item -> item.error() != null)
                        .map(item -> item.error().reason())
                        .findFirst()
                        .orElse("未知批量写入错误");
                throw new IllegalStateException("商品索引批量写入失败: " + reason);
            }
        }
    }

    private void switchAlias(String indexName) throws IOException {
        String alias = properties.getIndexAlias();
        // 删除旧指向与添加新指向放在同一个 aliases 请求中，避免查询出现无别名窗口。
        client.indices().updateAliases(request -> request
                .actions(action -> action.remove(remove -> remove
                        .index(properties.getIndexPrefix() + "*")
                        .alias(alias)
                        .mustExist(false)))
                .actions(action -> action.add(add -> add
                        .index(indexName)
                        .alias(alias)
                        .isWriteIndex(true))));
    }
}
