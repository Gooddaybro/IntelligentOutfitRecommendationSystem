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

  it("keeps mode and progress isolated per active run", () => {
    const running = assistantShoppingReducer(initialAssistantShoppingState, {
      type: "setAgentMode",
      value: "pro"
    });
    const withRun = assistantShoppingReducer(running, {
      type: "setActiveRunId",
      value: "run-1"
    });
    const withProgress = assistantShoppingReducer(withRun, {
      type: "setProgress",
      value: [{
        type: "progress",
        runId: "run-1",
        sequence: 1,
        tool: "search_products",
        stage: "started",
        message: "正在搜索商品"
      }]
    });

    expect(withProgress.agentMode).toBe("pro");
    expect(withProgress.activeRunId).toBe("run-1");
    expect(withProgress.progress).toHaveLength(1);
    expect(assistantShoppingReducer(withProgress, { type: "reset" })).toEqual(initialAssistantShoppingState);
  });
});
