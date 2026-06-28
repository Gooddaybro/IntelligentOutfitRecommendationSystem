import { describe, expect, it } from "vitest";
import {
  assistantShoppingReducer,
  initialAssistantShoppingState
} from "./assistantState";
import { inferBudgetMaxFromMessage } from "./ChatPanel";

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

  it("infers budget max from natural language prompt", () => {
    expect(inferBudgetMaxFromMessage("学生党想要平价百搭，预算500以内")).toBe(500);
    expect(inferBudgetMaxFromMessage("不超过 300")).toBe(300);
    expect(inferBudgetMaxFromMessage("显高显瘦")).toBeUndefined();
  });
});
