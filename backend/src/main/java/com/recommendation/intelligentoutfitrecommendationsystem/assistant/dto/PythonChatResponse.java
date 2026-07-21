package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Python `/chat` 第一版响应契约。
 *
 * answer 是自然语言回答，productRefs 是 Python 从 Java 候选池中选中的商品引用。
 */
public record PythonChatResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("answer") String answer,
        @JsonProperty("intent") String intent,
        @JsonProperty("product_refs") List<PythonProductRef> productRefs,
        @JsonProperty("rejected_reasons") List<String> rejectedReasons
) {
    /** Creates a response from the earlier contract that did not expose aggregate rejection reasons. */
    public PythonChatResponse(
            String requestId,
            String answer,
            String intent,
            List<PythonProductRef> productRefs
    ) {
        this(requestId, answer, intent, productRefs, List.of());
    }
}
