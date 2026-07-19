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
        'event: done\ndata: {"thread_id":"th-1","answer":"建议选择通勤外套","recommended_spu_ids":[1001,1002],"recommended_items":[{"spuId":1001,"skuId":2001,"reason":"通勤场景匹配","rankScore":1.91,"outfitRole":"TOP"}],"mentioned_items":[{"spuId":1001,"skuId":2001,"outfitRole":"TOP"}],"candidates_count":3,"intent":"recommendation","resolved_intent":{"requestType":"OUTFIT_ADVICE","targetGender":"female","category":"半裙","budgetMax":500},"recommendation_status":"STRONG_MATCH","recommendation_id":"rec_123"}'
      )
    ).toMatchObject({
      type: "done",
      threadId: "th-1",
      answer: "建议选择通勤外套",
      spuIds: [1001, 1002],
      recommendedItems: [{ spuId: 1001, skuId: 2001, reason: "通勤场景匹配", rankScore: 1.91, outfitRole: "TOP" }],
      mentionedItems: [{ spuId: 1001, skuId: 2001, outfitRole: "TOP" }],
      resolvedIntent: { requestType: "OUTFIT_ADVICE", targetGender: "female", category: "半裙", budgetMax: 500 },
      recommendationId: "rec_123",
      recommendationStatus: "STRONG_MATCH"
    });
  });

  it("normalizes camelCase mentioned items and nested snake_case ids", () => {
    expect(
      parseSseEventBlock(
        'event: done\ndata: {"threadId":"th-3","mentionedItems":[{"spu_id":"1003","sku_id":"2201","outfit_role":"BOTTOM"}]}'
      )
    ).toMatchObject({
      type: "done",
      mentionedItems: [{ spuId: 1003, skuId: 2201, outfitRole: "BOTTOM" }]
    });
  });

  it("derives recommendation ids from recommended items when legacy id list is absent", () => {
    const event = parseSseEventBlock(
      'event: done\ndata: {"thread_id":"th-2","recommended_items":[{"spu_id":1002,"sku_id":2101,"reason":"预算匹配","rank_score":0.87}]}'
    );

    expect(event).toMatchObject({
      type: "done",
      threadId: "th-2",
      answer: undefined,
      spuIds: [1002],
      recommendedItems: [{ spuId: 1002, skuId: 2101, reason: "预算匹配", rankScore: 0.87 }],
      resolvedIntent: undefined,
      recommendationId: undefined
    });
    expect(event).toHaveProperty("mentionedItems", undefined);
  });

  it("keeps legacy recommendation events compatible", () => {
    expect(parseSseEventBlock('event: recommendation\ndata: {"recommendedSpuIds":[1,2]}')).toEqual({
      type: "recommendation",
      spuIds: [1, 2],
      recommendedItems: []
    });
  });
});
