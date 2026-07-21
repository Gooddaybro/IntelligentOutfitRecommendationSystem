import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../shared/api/client";
import type { AssistantStreamEvent } from "../../shared/api/assistantStream";
import { ChatPanel } from "./ChatPanel";

const streamAssistantChatMock = vi.hoisted(() => vi.fn());

vi.mock("../../shared/api/assistantStream", () => ({
  streamAssistantChat: streamAssistantChatMock
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe("ChatPanel stale request protection", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    streamAssistantChatMock.mockReset();
  });

  it("keeps request 2 products and typed metadata when request 1 snapshot finishes late", async () => {
    const firstSnapshot = deferred<Awaited<ReturnType<typeof api.recommendationSnapshot>>>();
    const secondCandidate = {
      spuId: 202,
      skuId: 2202,
      spuCode: "NEW-202",
      name: "第二次请求商品",
      categoryName: "上装",
      salePrice: 188
    };
    const firstCandidate = {
      spuId: 101,
      skuId: 1101,
      spuCode: "OLD-101",
      name: "第一次请求商品",
      categoryName: "外套",
      salePrice: 288
    };
    vi.spyOn(api, "recommendationSnapshot").mockImplementation((recommendationId) =>
      recommendationId === "rec-old" ? firstSnapshot.promise : Promise.resolve([secondCandidate])
    );

    const doneEvents: AssistantStreamEvent[] = [
      {
        type: "done",
        spuIds: [101],
        recommendedItems: [{ spuId: 101, skuId: 1101 }],
        recommendationId: "rec-old",
        recommendationStatus: "STRONG_MATCH"
      },
      {
        type: "done",
        spuIds: [202],
        recommendedItems: [{ spuId: 202, skuId: 2202 }],
        recommendationId: "rec-new",
        recommendationStatus: "PARTIAL_MATCH"
      }
    ];
    streamAssistantChatMock.mockImplementation(
      async (_request: unknown, onEvent: (event: AssistantStreamEvent) => void) => {
        const event = doneEvents[streamAssistantChatMock.mock.calls.length - 1];
        void onEvent(event);
      }
    );
    const onRecommendations = vi.fn();
    render(<ChatPanel onRecommendations={onRecommendations} />);

    fireEvent.change(screen.getByTestId("ai-chat-input"), { target: { value: "第一次请求" } });
    fireEvent.click(screen.getByTestId("ai-chat-submit"));
    await waitFor(() => expect(api.recommendationSnapshot).toHaveBeenCalledWith("rec-old"));
    await waitFor(() => expect(screen.getByTestId("ai-chat-submit")).toBeInTheDocument());

    fireEvent.change(screen.getByTestId("ai-chat-input"), { target: { value: "第二次请求" } });
    fireEvent.click(screen.getByTestId("ai-chat-submit"));

    await waitFor(() => expect(onRecommendations).toHaveBeenCalledTimes(1));
    expect(onRecommendations).toHaveBeenLastCalledWith(
      [secondCandidate],
      expect.objectContaining({
        recommendationId: "rec-new",
        recommendationStatus: "PARTIAL_MATCH",
        recommendedItems: [{ spuId: 202, skuId: 2202 }]
      })
    );

    firstSnapshot.resolve([firstCandidate]);
    await firstSnapshot.promise;
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(onRecommendations).toHaveBeenCalledTimes(1);
    expect(onRecommendations.mock.calls[0][0]).toEqual([secondCandidate]);
    expect(onRecommendations.mock.calls[0][1].recommendationStatus).toBe("PARTIAL_MATCH");
  });
});
