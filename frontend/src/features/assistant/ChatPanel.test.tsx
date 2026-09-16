import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../shared/api/client";
import type { AssistantStreamEvent } from "../../shared/api/assistantStream";
import { ChatPanel } from "./ChatPanel";
import type { ChatPanelState } from "./ChatPanel";

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

  it("aborts on unmount and ignores a snapshot that resolves afterward", async () => {
    const snapshot = deferred<Awaited<ReturnType<typeof api.recommendationSnapshot>>>();
    const stream = deferred<void>();
    vi.spyOn(api, "recommendationSnapshot").mockReturnValue(snapshot.promise);
    let requestSignal: AbortSignal | undefined;
    streamAssistantChatMock.mockImplementation(
      (_request: unknown, onEvent: (event: AssistantStreamEvent) => void, signal?: AbortSignal) => {
        requestSignal = signal;
        void onEvent({
          type: "done",
          spuIds: [303],
          recommendedItems: [{ spuId: 303, skuId: 3303 }],
          recommendationId: "rec-unmounted",
          recommendationStatus: "STRONG_MATCH"
        });
        return stream.promise;
      }
    );
    const onRecommendations = vi.fn();
    const view = render(<ChatPanel onRecommendations={onRecommendations} />);

    fireEvent.change(screen.getByTestId("ai-chat-input"), { target: { value: "卸载中的请求" } });
    fireEvent.click(screen.getByTestId("ai-chat-submit"));
    await waitFor(() => expect(api.recommendationSnapshot).toHaveBeenCalledWith("rec-unmounted"));

    view.unmount();
    expect(requestSignal?.aborted).toBe(true);
    snapshot.resolve([{
      spuId: 303, skuId: 3303, spuCode: "UNMOUNTED", name: "卸载后商品", categoryName: "上装", salePrice: 99
    }]);
    await snapshot.promise;
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(onRecommendations).not.toHaveBeenCalled();
  });

  it("does not let an unmounted request finally clear a remounted request controller", async () => {
    const oldStream = deferred<void>();
    const newStream = deferred<void>();
    streamAssistantChatMock
      .mockReturnValueOnce(oldStream.promise)
      .mockReturnValueOnce(newStream.promise);
    const sharedAbortRef: ChatPanelState["abortRef"] = { current: null };
    const setIsStreaming = vi.fn();

    function ControlledPanel() {
      const [draft, setDraft] = useState("");
      const state: ChatPanelState = {
        messages: [],
        setMessages: vi.fn(),
        draft,
        setDraft,
        filters: { category: "", style: "", season: "", budgetMax: "" },
        setFilters: vi.fn(),
        threadId: undefined,
        setThreadId: vi.fn(),
        isStreaming: false,
        setIsStreaming,
        error: "",
        setError: vi.fn(),
        abortRef: sharedAbortRef
      };
      return <ChatPanel state={state} onRecommendations={vi.fn()} />;
    }

    const first = render(<ControlledPanel />);
    fireEvent.change(screen.getByTestId("ai-chat-input"), { target: { value: "旧请求" } });
    fireEvent.click(screen.getByTestId("ai-chat-submit"));
    await waitFor(() => expect(streamAssistantChatMock).toHaveBeenCalledTimes(1));
    first.unmount();

    render(<ControlledPanel />);
    fireEvent.change(screen.getByTestId("ai-chat-input"), { target: { value: "新请求" } });
    fireEvent.click(screen.getByTestId("ai-chat-submit"));
    await waitFor(() => expect(streamAssistantChatMock).toHaveBeenCalledTimes(2));
    const newController = sharedAbortRef.current;
    expect(newController).not.toBeNull();
    setIsStreaming.mockClear();

    oldStream.resolve();
    await oldStream.promise;
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(sharedAbortRef.current).toBe(newController);
    expect(setIsStreaming).not.toHaveBeenCalledWith(false);
  });

  it("locks the selected Pro mode for the run and renders validated facts only after done", async () => {
    const stream = deferred<void>();
    let emit!: (event: AssistantStreamEvent) => Promise<void> | void;
    streamAssistantChatMock.mockImplementation(async (_request: unknown, onEvent: (event: AssistantStreamEvent) => Promise<void> | void) => {
      emit = onEvent;
      await stream.promise;
    });
    const onRecommendations = vi.fn();
    const recommendationSnapshot = vi.spyOn(api, "recommendationSnapshot");
    render(<ChatPanel onRecommendations={onRecommendations} />);

    const mode = screen.getByLabelText("AI 模式");
    expect(mode).toHaveValue("lite");
    fireEvent.change(mode, { target: { value: "pro" } });
    fireEvent.change(screen.getByTestId("ai-chat-input"), { target: { value: "预算 300 的通勤外套" } });
    fireEvent.click(screen.getByTestId("ai-chat-submit"));

    await waitFor(() => expect(streamAssistantChatMock).toHaveBeenCalledTimes(1));
    expect(streamAssistantChatMock.mock.calls[0][0]).toMatchObject({
      message: "预算 300 的通勤外套",
      agentMode: "pro"
    });
    expect(streamAssistantChatMock.mock.calls[0][3]).toBe("pro");
    expect(mode).toBeDisabled();
    fireEvent.change(mode, { target: { value: "lite" } });
    expect(mode).toHaveValue("pro");

    await act(async () => {
      await emit({ type: "thread", threadId: "thread-pro", runId: "run-pro", agentMode: "pro" });
      await emit({
        type: "progress",
        runId: "run-pro",
        sequence: 1,
        tool: "search_products",
        stage: "started",
        message: "正在搜索商品"
      });
    });
    expect(screen.getByTestId("assistant-progress")).toHaveTextContent("正在搜索商品");
    expect(onRecommendations).not.toHaveBeenCalled();

    await act(async () => {
      await emit({
        type: "done",
        threadId: "thread-pro",
        runId: "run-pro",
        agentMode: "pro",
        answer: "已核验这件通勤外套",
        spuIds: [1004],
        recommendedItems: [{
          spuId: 1004,
          skuId: 2004,
          name: "真实外套",
          salePrice: 299.9,
          mainImageUrl: "/coat.jpg",
          color: "黑色",
          size: "L",
          availableStock: 4,
          reason: "预算和场景匹配"
        }],
        recommendationStatus: "STRONG_MATCH",
        recommendationId: "rec-pro"
      });
    });

    await waitFor(() => expect(onRecommendations).toHaveBeenCalledTimes(1));
    expect(onRecommendations).toHaveBeenCalledWith(
      [expect.objectContaining({
        spuId: 1004,
        skuId: 2004,
        name: "真实外套",
        salePrice: 299.9,
        mainImageUrl: "/coat.jpg",
        availableStock: 4
      })],
      expect.objectContaining({ agentMode: "pro", recommendationId: "rec-pro" })
    );
    expect(recommendationSnapshot).not.toHaveBeenCalled();
    expect(screen.getAllByTestId("chat-message-assistant").at(-1)).toHaveTextContent("已核验这件通勤外套");

    stream.resolve();
    await waitFor(() => expect(mode).not.toBeDisabled());
    fireEvent.change(mode, { target: { value: "lite" } });
    expect(mode).toHaveValue("lite");
    expect(screen.getByTestId("chat-message-user")).toHaveTextContent("预算 300 的通勤外套");
  });

  it("shows a Pro availability error without falling back to the Lite endpoint", async () => {
    streamAssistantChatMock.mockRejectedValue(new Error("Pro 尚未启用"));
    const chat = vi.spyOn(api, "chat");
    render(<ChatPanel onRecommendations={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("AI 模式"), { target: { value: "pro" } });
    fireEvent.change(screen.getByTestId("ai-chat-input"), { target: { value: "测试 Pro 不可用" } });
    fireEvent.click(screen.getByTestId("ai-chat-submit"));

    await waitFor(() => expect(screen.getByText("Pro 尚未启用")).toBeInTheDocument());
    expect(chat).not.toHaveBeenCalled();
  });
});
