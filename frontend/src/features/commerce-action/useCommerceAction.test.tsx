import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../../shared/api/client";
import type { OrderResponse } from "../../shared/api/types";
import type { PendingCommerceAction } from "./commerceActions";
import { useCommerceAction } from "./useCommerceAction";

const order: OrderResponse = {
  orderNo: "ORD1",
  status: "UNPAID",
  totalAmount: 299,
  items: [],
  createdAt: "2026-08-24T10:00:00"
};

describe("useCommerceAction buy-now idempotency", () => {
  afterEach(() => vi.restoreAllMocks());

  it("reuses the same key when a failed SKU and quantity intent is confirmed again", async () => {
    vi.spyOn(crypto, "randomUUID").mockReturnValue("11111111-1111-4111-8111-111111111111");
    const buyNow = vi.spyOn(api, "buyNow")
      .mockRejectedValueOnce(new Error("network unavailable"))
      .mockResolvedValueOnce(order);
    const { result } = renderCommerceAction();

    act(() => result.current.setPendingAction(action(2102, 2)));
    await act(async () => {
      await expect(result.current.confirm()).rejects.toThrow("network unavailable");
    });
    await act(async () => {
      await result.current.confirm();
    });

    expect(buyNow).toHaveBeenNthCalledWith(
      1, 2102, 2, "11111111-1111-4111-8111-111111111111", "rec-1"
    );
    expect(buyNow).toHaveBeenNthCalledWith(
      2, 2102, 2, "11111111-1111-4111-8111-111111111111", "rec-1"
    );
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1);
  });

  it("rotates the key when quantity or SKU changes after a failure", async () => {
    vi.spyOn(crypto, "randomUUID")
      .mockReturnValueOnce("11111111-1111-4111-8111-111111111111")
      .mockReturnValueOnce("22222222-2222-4222-8222-222222222222")
      .mockReturnValueOnce("33333333-3333-4333-8333-333333333333");
    const buyNow = vi.spyOn(api, "buyNow")
      .mockRejectedValueOnce(new Error("first failure"))
      .mockRejectedValueOnce(new Error("second failure"))
      .mockResolvedValueOnce(order);
    const { result } = renderCommerceAction();

    act(() => result.current.setPendingAction(action(2102, 1)));
    await act(async () => {
      await expect(result.current.confirm()).rejects.toThrow("first failure");
    });
    act(() => result.current.setPendingAction(action(2102, 2)));
    await act(async () => {
      await expect(result.current.confirm()).rejects.toThrow("second failure");
    });
    act(() => result.current.setPendingAction(action(2202, 2)));
    await act(async () => {
      await result.current.confirm();
    });

    expect(buyNow).toHaveBeenNthCalledWith(
      1, 2102, 1, "11111111-1111-4111-8111-111111111111", "rec-1"
    );
    expect(buyNow).toHaveBeenNthCalledWith(
      2, 2102, 2, "22222222-2222-4222-8222-222222222222", "rec-1"
    );
    expect(buyNow).toHaveBeenNthCalledWith(
      3, 2202, 2, "33333333-3333-4333-8333-333333333333", "rec-1"
    );
  });

  it("discards the failed intent when cancel is followed by reopening the same action", async () => {
    vi.spyOn(crypto, "randomUUID")
      .mockReturnValueOnce("11111111-1111-4111-8111-111111111111")
      .mockReturnValueOnce("22222222-2222-4222-8222-222222222222");
    const buyNow = vi.spyOn(api, "buyNow")
      .mockRejectedValueOnce(new Error("network unavailable"))
      .mockResolvedValueOnce(order);
    const { result } = renderCommerceAction();

    act(() => result.current.setPendingAction(action(2102, 2)));
    await act(async () => {
      await expect(result.current.confirm()).rejects.toThrow("network unavailable");
    });
    act(() => result.current.setPendingAction(null));
    act(() => result.current.setPendingAction(action(2102, 2)));
    await act(async () => {
      await result.current.confirm();
    });

    expect(buyNow).toHaveBeenNthCalledWith(
      2, 2102, 2, "22222222-2222-4222-8222-222222222222", "rec-1"
    );
    expect(crypto.randomUUID).toHaveBeenCalledTimes(2);
  });

  it("discards a successful intent before reopening the same action", async () => {
    vi.spyOn(crypto, "randomUUID")
      .mockReturnValueOnce("11111111-1111-4111-8111-111111111111")
      .mockReturnValueOnce("22222222-2222-4222-8222-222222222222");
    const buyNow = vi.spyOn(api, "buyNow").mockResolvedValue(order);
    const { result } = renderCommerceAction();

    act(() => result.current.setPendingAction(action(2102, 2)));
    await act(async () => {
      await result.current.confirm();
    });
    act(() => result.current.setPendingAction(action(2102, 2)));
    await act(async () => {
      await result.current.confirm();
    });

    expect(buyNow).toHaveBeenNthCalledWith(
      1, 2102, 2, "11111111-1111-4111-8111-111111111111", "rec-1"
    );
    expect(buyNow).toHaveBeenNthCalledWith(
      2, 2102, 2, "22222222-2222-4222-8222-222222222222", "rec-1"
    );
  });
});

function renderCommerceAction() {
  return renderHook(() => useCommerceAction({
    onCartItemsChange: vi.fn(),
    onOrderCreated: vi.fn()
  }));
}

function action(skuId: number, quantity: number): PendingCommerceAction {
  return {
    kind: "BUY_NOW",
    spuId: skuId - 1000,
    skuId,
    quantity,
    productName: "test product",
    unitPrice: 299,
    recommendationId: "rec-1"
  };
}
