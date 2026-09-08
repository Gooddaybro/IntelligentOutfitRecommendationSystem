import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./client";

beforeEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});

describe("order api client", () => {
  it("sends the required address and Idempotency-Key when creating an order", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve(JSON.stringify({ data: { orderNo: "ORD1" } }))
    });
    vi.stubGlobal("fetch", fetchMock);

    await api.createOrder([2102, 2202], 7, "2d36f872-e8d1-4e4f-b12e-a9702c88e890");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/orders",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ source: "CART", skuIds: [2102, 2202], addressId: 7 })
      })
    );
    expect((fetchMock.mock.calls[0][1].headers as Headers).get("Idempotency-Key"))
      .toBe("2d36f872-e8d1-4e4f-b12e-a9702c88e890");
  });

  it("sends the required Idempotency-Key when buying now", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve(JSON.stringify({ data: { orderNo: "ORD2" } }))
    });
    vi.stubGlobal("fetch", fetchMock);

    await api.buyNow(2102, 2, "11111111-1111-4111-8111-111111111111", "rec-1");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/orders/buy-now",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ skuId: 2102, quantity: 2, recommendationId: "rec-1" })
      })
    );
    expect((fetchMock.mock.calls[0][1].headers as Headers).get("Idempotency-Key"))
      .toBe("11111111-1111-4111-8111-111111111111");
  });
});

describe("favorite api client", () => {
  it("uses the unified favorites routes and JSON request body", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: () => Promise.resolve(JSON.stringify({ data: [] }))
    });
    vi.stubGlobal("fetch", fetchMock);

    await api.favorites();
    await api.addFavorite(1001, "rec-1");
    await api.removeFavorite(1001);

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/favorites", expect.anything());
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/favorites", expect.objectContaining({
      method: "POST",
      body: JSON.stringify({ spuId: 1001, recommendationId: "rec-1" })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/favorites/1001", expect.objectContaining({
      method: "DELETE"
    }));
  });
});

describe("address api client", () => {
  const address = {
    id: 9,
    userId: 1001,
    recipientName: "林木",
    phone: "13800000000",
    province: "浙江省",
    city: "杭州市",
    district: "西湖区",
    detail: "文一路 88 号",
    isDefault: true
  };

  it("lists addresses through the unified route", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, text: () => Promise.resolve(JSON.stringify({ data: [] })) });
    vi.stubGlobal("fetch", fetchMock);

    await api.addresses();

    expect(fetchMock).toHaveBeenCalledWith("/api/addresses", expect.objectContaining({ method: "GET" }));
  });

  it("uses the five address routes and sends only address fields for create and update", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, text: () => Promise.resolve(JSON.stringify({ data: [] })) });
    vi.stubGlobal("fetch", fetchMock);

    await api.createAddress(address);
    await api.updateAddress(address.id, address);
    await api.deleteAddress(address.id);
    await api.setDefaultAddress(address.id);

    const body = JSON.stringify({
      recipientName: "林木",
      phone: "13800000000",
      province: "浙江省",
      city: "杭州市",
      district: "西湖区",
      detail: "文一路 88 号"
    });
    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/addresses", expect.objectContaining({ method: "POST", body }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/addresses/9", expect.objectContaining({ method: "PUT", body }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/addresses/9", expect.objectContaining({ method: "DELETE" }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, "/api/addresses/9/default", expect.objectContaining({ method: "PUT" }));
  });
});

describe("payment api client", () => {
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

  it("patches only height and weight", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, text: () => Promise.resolve("{}") });
    vi.stubGlobal("fetch", fetchMock);

    await api.updateBodyMeasurements({ heightCm: 177, weightKg: 65 });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/me/body-data/measurements",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ heightCm: 177, weightKg: 65 }) })
    );
  });

  it("reads the persisted recommendation candidate snapshot", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, text: () => Promise.resolve(JSON.stringify({ data: [] })) });
    vi.stubGlobal("fetch", fetchMock);

    await api.recommendationSnapshot("rec / 1");

    expect(fetchMock).toHaveBeenCalledWith("/api/assistant/recommendations/rec%20%2F%201/candidates", expect.anything());
  });
});
