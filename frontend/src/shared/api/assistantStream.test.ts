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
        'event: done\ndata: {"thread_id":"th-1","answer":"建议选择通勤外套","recommended_spu_ids":[1001,1002],"candidates_count":3,"intent":"recommendation"}'
      )
    ).toEqual({
      type: "done",
      threadId: "th-1",
      answer: "建议选择通勤外套",
      spuIds: [1001, 1002]
    });
  });

  it("keeps legacy recommendation events compatible", () => {
    expect(parseSseEventBlock('event: recommendation\ndata: {"recommendedSpuIds":[1,2]}')).toEqual({
      type: "recommendation",
      spuIds: [1, 2]
    });
  });
});
