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

  it("records recommendation behavior events with bearer auth", async () => {
    localStorage.setItem("ior.accessToken", "token-1");
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve(JSON.stringify({ data: { eventId: "evt-1" } }))
    });
    vi.stubGlobal("fetch", fetchMock);

    await api.recordBehaviorEvent({
      eventId: "evt-1",
      eventType: "RECOMMENDATION_CLICKED",
      spuId: 1001,
      skuId: 2001,
      threadId: "thread-1",
      metadata: { position: 1 }
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/behavior/events",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          eventId: "evt-1",
          eventType: "RECOMMENDATION_CLICKED",
          spuId: 1001,
          skuId: 2001,
          threadId: "thread-1",
          metadata: { position: 1 }
        })
      })
    );
    expect((fetchMock.mock.calls[0][1].headers as Headers).get("Authorization")).toBe("Bearer token-1");
  });
});

describe("current user profile api client", () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("updates shopping preferences through the /api/me boundary", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: () =>
        Promise.resolve(
          JSON.stringify({
            data: {
              userId: 1001,
              preferredStyles: ["commute"],
              preferredColors: ["black"],
              dislikedColors: [],
              preferredCategories: ["外套"],
              budgetMin: 100,
              budgetMax: 500
            }
          })
        )
    });
    vi.stubGlobal("fetch", fetchMock);

    await api.updatePreferences({
      preferredStyles: ["commute"],
      preferredColors: ["black"],
      dislikedColors: [],
      preferredCategories: ["外套"],
      budgetMin: 100,
      budgetMax: 500
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/me/preferences",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({
          preferredStyles: ["commute"],
          preferredColors: ["black"],
          dislikedColors: [],
          preferredCategories: ["外套"],
          budgetMin: 100,
          budgetMax: 500
        })
      })
    );
  });
});
