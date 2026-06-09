import { describe, expect, it } from "vitest";
import { parseSseEventBlock } from "./assistantStream";

describe("parseSseEventBlock", () => {
  it("parses token events from Java SSE blocks", () => {
    expect(parseSseEventBlock('event: token\ndata: {"text":"外套"}')).toEqual({
      type: "token",
      text: "外套"
    });
  });

  it("parses recommendation ids without trusting product details from AI", () => {
    expect(parseSseEventBlock("event: recommendation\ndata: {\"recommendedSpuIds\":[1,2]}")).toEqual({
      type: "recommendation",
      spuIds: [1, 2]
    });
  });

  it("parses done events", () => {
    expect(parseSseEventBlock("event: done\ndata:")).toEqual({ type: "done" });
  });
});
