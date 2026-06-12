import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./client";

describe("payment api client", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("creates payment through the unified endpoint without frontend amount", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: () =>
        Promise.resolve(
          JSON.stringify({
            data: {
              paymentNo: "PAY1",
              orderNo: "ORD1",
              amount: 99,
              channel: "MOCK",
              status: "SUCCESS",
              transactionId: "TX1",
              paidAt: "2026-06-12T10:00:00"
            }
          })
        )
    });
    vi.stubGlobal("fetch", fetchMock);

    await api.pay("ORD1", "MOCK");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/payments",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ orderNo: "ORD1", channel: "MOCK" })
      })
    );
  });
});
