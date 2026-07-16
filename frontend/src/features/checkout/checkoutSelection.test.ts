import { describe, expect, it } from "vitest";
import { parseCheckoutSkuIds, serializeCheckoutSkuIds } from "./checkoutSelection";

describe("checkoutSelection", () => {
  it("稳定序列化并过滤非法 SKU", () => {
    expect(parseCheckoutSkuIds("3,2,2,foo,-1")).toEqual([3, 2]);
    expect(serializeCheckoutSkuIds([3, 2, 2])).toBe("3,2");
  });
});
