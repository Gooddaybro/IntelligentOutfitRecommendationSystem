import { afterEach, describe, expect, it, vi } from "vitest";
import { chatStreamPath, parseSseEventBlock, streamAssistantChat } from "./assistantStream";

describe("parseSseEventBlock", () => {
  it("parses Java meta events as thread identity", () => {
    expect(parseSseEventBlock('event: meta\ndata: {"request_id":"req-1","thread_id":"th-1"}')).toEqual({
      type: "thread",
      threadId: "th-1"
    });
  });

  it("keeps the Pro run identity and mode from Java meta", () => {
    expect(parseSseEventBlock('event: meta\ndata: {"request_id":"req-2","thread_id":"th-2","run_id":"run-2","agent_mode":"pro"}')).toEqual({
      type: "thread",
      threadId: "th-2",
      requestId: "req-2",
      runId: "run-2",
      agentMode: "pro"
    });
  });

  it("parses safe Pro progress without exposing tool data", () => {
    expect(parseSseEventBlock('event: progress\ndata: {"run_id":"run-2","sequence":3,"tool":"search_products","stage":"started","message":"正在搜索商品"}')).toEqual({
      type: "progress",
      runId: "run-2",
      sequence: 3,
      tool: "search_products",
      stage: "started",
      message: "正在搜索商品"
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

  it("parses Java-validated Pro product facts only from done", () => {
    expect(parseSseEventBlock('event: done\ndata: {"thread_id":"th-4","run_id":"run-4","agent_mode":"pro","answer":"已核验","recommended_items":[{"spu_id":1004,"sku_id":2004,"name":"真实外套","sale_price":"299.90","main_image_url":"/coat.jpg","color":"黑色","size":"L","available_stock":4,"reason":"预算和场景匹配","size_advice":"建议 L","basis":"product_chart"}],"recommended_spu_ids":[1004],"recommendation_status":"STRONG_MATCH","recommendation_id":"rec-4"}')).toMatchObject({
      type: "done",
      runId: "run-4",
      agentMode: "pro",
      recommendedItems: [{
        spuId: 1004,
        skuId: 2004,
        name: "真实外套",
        salePrice: 299.9,
        mainImageUrl: "/coat.jpg",
        color: "黑色",
        size: "L",
        availableStock: 4,
        sizeAdvice: "建议 L",
        basis: "product_chart"
      }]
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

  it.each([
    ["WEAK_FALLBACK", "BROWSE_FALLBACK"],
    ["ERROR", "FAILED"]
  ])("normalizes legacy %s done status to %s at the SSE boundary", (legacy, current) => {
    expect(parseSseEventBlock(
      `event: done\ndata: {"thread_id":"th-legacy","recommendation_status":"${legacy}"}`
    )).toMatchObject({
      type: "done",
      recommendationStatus: current
    });
  });
});

describe("chatStreamPath", () => {
  it("uses the versioned endpoint only for Pro", () => {
    expect(chatStreamPath("lite")).toBe("/api/assistant/chat/stream");
    expect(chatStreamPath("pro")).toBe("/api/assistant/v2/chat/stream");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("sends Pro requests to v2 with the public mode field", async () => {
    const encoder = new TextEncoder();
    const read = vi.fn()
      .mockResolvedValueOnce({ value: encoder.encode('event: meta\ndata: {"thread_id":"th-5","run_id":"run-5","agent_mode":"pro"}\n\n'), done: false })
      .mockResolvedValueOnce({ value: undefined, done: true });
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: { getReader: () => ({ read }) }
    });
    vi.stubGlobal("fetch", fetchMock);

    await streamAssistantChat({ message: "测试 Pro", agentMode: "pro" }, vi.fn(), undefined, "pro");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/assistant/v2/chat/stream",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ message: "测试 Pro", agentMode: "pro" })
      })
    );
  });
});
