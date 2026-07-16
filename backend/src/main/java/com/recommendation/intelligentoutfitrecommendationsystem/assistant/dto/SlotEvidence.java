package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/** Exact source text supporting one LLM-proposed slot. */
public record SlotEvidence(String text, String source) {
}
