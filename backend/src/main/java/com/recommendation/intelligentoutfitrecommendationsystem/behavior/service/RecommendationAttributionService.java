package com.recommendation.intelligentoutfitrecommendationsystem.behavior.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.RecommendationAttributionMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.RecommendationSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 推荐曝光事实的写入边界。
 *
 * 服务在单个事务中保存父记录和候选快照，并只允许 Assistant 已验证过的候选 SKU 被标记为最终选择。
 */
@Service
public class RecommendationAttributionService {

    private static final String RULE_VERSION = "java-rule-reranker-v1";

    private final RecommendationAttributionMapper mapper;
    private final ApplicationMetrics applicationMetrics;

    public RecommendationAttributionService(
            RecommendationAttributionMapper mapper,
            ApplicationMetrics applicationMetrics
    ) {
        this.mapper = mapper;
        this.applicationMetrics = applicationMetrics;
    }

    @Transactional
    public String record(RecommendationRecordCommand command) {
        validate(command);
        String recommendationId = "rec_" + UUID.randomUUID().toString().replace("-", "");
        List<RecommendationSnapshot.Item> items = mergeItems(command);
        RecommendationSnapshot snapshot = new RecommendationSnapshot(
                recommendationId,
                command.userId(),
                command.requestId().trim(),
                command.threadId().trim(),
                command.mode().trim(),
                items.size(),
                RULE_VERSION,
                LocalDateTime.now(),
                items
        );
        mapper.insertRecommendation(snapshot);
        if (!items.isEmpty()) {
            mapper.insertItems(recommendationId, items);
        }
        applicationMetrics.recordRecommendationFunnel("exposure");
        return recommendationId;
    }

    private List<RecommendationSnapshot.Item> mergeItems(RecommendationRecordCommand command) {
        Map<Long, PositionedCandidate> candidates = new LinkedHashMap<>();
        int candidatePosition = 1;
        for (RecommendationRecordCommand.Item candidate : safe(command.candidates())) {
            if (hasProductIdentity(candidate) && !candidates.containsKey(candidate.skuId())) {
                candidates.put(candidate.skuId(), new PositionedCandidate(candidatePosition, candidate));
                candidatePosition++;
            }
        }

        Map<Long, RankedSelection> selections = new LinkedHashMap<>();
        int position = 1;
        for (RecommendationRecordCommand.Item selected : safe(command.selectedItems())) {
            if (hasProductIdentity(selected) && candidates.containsKey(selected.skuId())) {
                selections.putIfAbsent(selected.skuId(), new RankedSelection(position, selected));
                position++;
            }
        }

        return candidates.values().stream()
                .map(candidate -> toSnapshotItem(candidate, selections.get(candidate.item().skuId())))
                .toList();
    }

    private RecommendationSnapshot.Item toSnapshotItem(
            PositionedCandidate candidate,
            RankedSelection selection
    ) {
        return new RecommendationSnapshot.Item(
                candidate.item().spuId(),
                candidate.item().skuId(),
                candidate.position(),
                selection != null,
                selection == null ? null : selection.position(),
                selection == null ? null : selection.item().rankScore()
        );
    }

    /** Returns the original candidate snapshot with current Java price and inventory facts. */
    public List<RecommendationCandidate> getCandidateSnapshot(Long userId, String recommendationId) {
        if (userId == null || userId <= 0 || !StringUtils.hasText(recommendationId)
                || mapper.existsOwnedRecommendation(recommendationId.trim(), userId) == 0) {
            throw new ResourceNotFoundException("recommendation not found");
        }
        return mapper.findOwnedCandidates(recommendationId.trim(), userId);
    }

    private void validate(RecommendationRecordCommand command) {
        if (command == null || command.userId() == null || command.userId() <= 0) {
            throw new BadRequestException("recommendation userId must be positive");
        }
        if (!StringUtils.hasText(command.requestId()) || !StringUtils.hasText(command.threadId())) {
            throw new BadRequestException("recommendation requestId and threadId are required");
        }
        if (!"sync".equals(command.mode()) && !"stream".equals(command.mode())) {
            throw new BadRequestException("recommendation mode is not supported: " + command.mode());
        }
    }

    private boolean hasProductIdentity(RecommendationRecordCommand.Item item) {
        return item != null && item.spuId() != null && item.spuId() > 0
                && item.skuId() != null && item.skuId() > 0;
    }

    private List<RecommendationRecordCommand.Item> safe(List<RecommendationRecordCommand.Item> items) {
        return items == null ? List.of() : items;
    }

    private record RankedSelection(int position, RecommendationRecordCommand.Item item) {
    }

    private record PositionedCandidate(int position, RecommendationRecordCommand.Item item) {
    }
}
