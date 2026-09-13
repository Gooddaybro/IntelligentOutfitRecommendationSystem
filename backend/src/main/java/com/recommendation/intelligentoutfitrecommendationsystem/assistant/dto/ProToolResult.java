package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/** Internal v2 tool envelope; all facts and decimal strings are selected by Java. */
public record ProToolResult(String status, Object data,
                            @JsonProperty("evidence_id") String evidenceId, String source,
                            @JsonProperty("missing_fields") List<String> missingFields,
                            @JsonProperty("error_code") String errorCode) {
    /** Supplies opaque evidence identity without including run secrets or dependency diagnostics. */
    public static ProToolResult of(String status, Object data, String errorCode) {
        return new ProToolResult(status, data, "ev-" + UUID.randomUUID(), "java", List.of(), errorCode);
    }
}
