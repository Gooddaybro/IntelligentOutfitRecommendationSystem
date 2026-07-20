import { describe, expect, it } from "vitest";
import { parseSseEventBlock } from "./assistantStream";

describe("parseSseEventBlock", () => {
  it("parses Java meta events as thread identity", () => {
    expect(parseSseEventBlock('event: meta\ndata: {"request_id":"req-1","thread_id":"th-1"}')).toEqual({
      type: "thread",
      threadId: "th-1"
    });
  });

  it("parses token events from the current Java content field", () => {
    expect(parseSseEventBlock('event: token\ndata: {"content":"外套"}')).toEqual({
      type: "token",
      text: "外套"
    });
  });

  it("parses done events with answer and recommendation ids", () => {
    expect(
      parseSseEventBlock(
        'event: done\ndata: {"thread_id":"th-1","answer":"建议选择通勤外套","recommended_spu_ids":[1001,1002],"recommended_items":[{"spuId":1001,"skuId":2001,"reason":"通勤场景匹配","rankScore":1.91,"outfitRole":"TOP"}],"candidates_count":3,"intent":"recommendation","resolved_intent":{"requestType":"OUTFIT_ADVICE","targetGender":"female","category":"半裙","budgetMax":500},"recommendation_status":"STRONG_MATCH","recommendation_id":"rec_123"}'
      )
    ).toEqual({
      type: "done",
      threadId: "th-1",
      answer: "建议选择通勤外套",
      spuIds: [1001, 1002],
      recommendedItems: [{ spuId: 1001, skuId: 2001, reason: "通勤场景匹配", rankScore: 1.91, outfitRole: "TOP" }],
      resolvedIntent: { requestType: "OUTFIT_ADVICE", targetGender: "female", category: "半裙", budgetMax: 500 },
      recommendationId: "rec_123",
      recommendationStatus: "STRONG_MATCH"
    });
  });

  it("derives recommendation ids from recommended items when legacy id list is absent", () => {
    expect(
      parseSseEventBlock(
        'event: done\ndata: {"thread_id":"th-2","recommended_items":[{"spu_id":1002,"sku_id":2101,"reason":"预算匹配","rank_score":0.87}]}'
      )
    ).toEqual({
      type: "done",
      threadId: "th-2",
      answer: undefined,
      spuIds: [1002],
      recommendedItems: [{ spuId: 1002, skuId: 2101, reason: "预算匹配", rankScore: 0.87 }],
      resolvedIntent: undefined,
      recommendationId: undefined
    });
  });

  it("keeps legacy recommendation events compatible", () => {
    expect(parseSseEventBlock('event: recommendation\ndata: {"recommendedSpuIds":[1,2]}')).toEqual({
      type: "recommendation",
      spuIds: [1, 2],
      recommendedItems: []
    });
  });
});
