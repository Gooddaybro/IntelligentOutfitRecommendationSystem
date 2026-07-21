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
        recommendationStatus: "PARTIAL_MATCH",
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

  it("stores the typed partial and failed completion statuses", () => {
    const partialLoading = assistantShoppingReducer(initialAssistantShoppingState, {
      type: "recommendationStarted",
      requestId: "req-partial"
    });
    const partial = assistantShoppingReducer(partialLoading, {
      type: "recommendationCompleted",
      requestId: "req-partial",
      status: "PARTIAL_MATCH"
    });
    const failedLoading = assistantShoppingReducer(partial, {
      type: "recommendationStarted",
      requestId: "req-failed"
    });

    expect(partial.recommendationStatus).toBe("PARTIAL_MATCH");
    expect(assistantShoppingReducer(failedLoading, {
      type: "recommendationCompleted",
      requestId: "req-failed",
      status: "FAILED"
    }).recommendationStatus).toBe("FAILED");
  });
});
