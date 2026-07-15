package com.recommendation.intelligentoutfitrecommendationsystem.aitask.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Python 安全切换索引后的结果契约，taskId 必须与请求一致。
 */
public record RagRebuildResult(
        @JsonProperty("taskId") String taskId,
        @JsonProperty("indexVersion") String indexVersion,
        @JsonProperty("fileCount") int fileCount,
        @JsonProperty("chunkCount") int chunkCount,
        @JsonProperty("contentDigest") String contentDigest,
        boolean replayed
) {
}
