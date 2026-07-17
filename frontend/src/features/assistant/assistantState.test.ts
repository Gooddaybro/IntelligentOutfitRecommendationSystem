import { describe, expect, it } from "vitest";
import {
  assistantShoppingReducer,
  initialAssistantShoppingState
} from "./assistantState";

describe("assistant shopping state", () => {
  it("updates chat fields through reducer transitions", () => {
    const state = assistantShoppingReducer(initialAssistantShoppingState, {
      type: "setDraft",
      value: "通勤预算 300"
    });

    expect(state.draft).toBe("通勤预算 300");
  });

  it("preserves recommendation metadata and clears it on reset", () => {
    const withRecommendations = assistantShoppingReducer(initialAssistantShoppingState, {
      type: "setRecommendationMeta",
      value: {
        hasAiResult: true,
        hasStrongMatch: true,
        recommendedItems: [{ spuId: 1002, skuId: 2101, reason: "预算匹配", rankScore: 0.9 }]
      }
    });

    expect(withRecommendations.recommendationMeta?.recommendedItems?.[0].reason).toBe("预算匹配");
    expect(assistantShoppingReducer(withRecommendations, { type: "reset" })).toEqual(initialAssistantShoppingState);
  });

  it("ignores a completion event from an older request", () => {
    const loading = assistantShoppingReducer(initialAssistantShoppingState, {
      type: "recommendationStarted",
      requestId: "req-new"
    });
    const stale = assistantShoppingReducer(loading, {
      type: "recommendationCompleted",
      requestId: "req-old",
      status: "STRONG_MATCH"
    });

    expect(stale.recommendationRequestId).toBe("req-new");
    expect(stale.recommendationStatus).toBe("LOADING");
  });
});
