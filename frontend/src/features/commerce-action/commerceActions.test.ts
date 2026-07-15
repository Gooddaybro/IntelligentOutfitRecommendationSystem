import { describe, expect, it } from "vitest";
import { actionConfirmText, buildAddToCartAction, buildBuyNowAction } from "./commerceActions";

const candidate = {
  spuId: 1,
  skuId: 10,
  spuCode: "SPU-1",
  name: "通勤夹克",
  categoryName: "外套",
  salePrice: 299
};

describe("commerce actions", () => {
  it("builds explicit add-to-cart actions from backend SKU facts", () => {
    expect(buildAddToCartAction(candidate, 2)).toEqual({
      kind: "ADD_TO_CART",
      spuId: 1,
      skuId: 10,
      quantity: 2,
      productName: "通勤夹克",
      unitPrice: 299
    });
  });

  it("keeps buy-now as a separate confirmed action", () => {
    expect(actionConfirmText(buildBuyNowAction(candidate))).toBe("立即下单：通勤夹克 x 1");
  });

  it("preserves recommendation source metadata on commerce actions", () => {
    const action = buildAddToCartAction(candidate, 1, {
      source: "ASSISTANT_RECOMMENDATION",
      threadId: "thread-1",
      recommendationId: "rec_123"
    });

    expect(action.source).toBe("ASSISTANT_RECOMMENDATION");
    expect(action.spuId).toBe(1);
    expect(action.threadId).toBe("thread-1");
    expect(action.recommendationId).toBe("rec_123");
  });
});
