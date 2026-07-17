import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AiShoppingPage } from "./AiShoppingPage";

vi.mock("../features/assistant/ChatPanel", () => ({
  ChatPanel: () => <section data-testid="chat-panel" />
}));

const chatState = {
  messages: [], setMessages: vi.fn(), draft: "", setDraft: vi.fn(),
  filters: { category: "", style: "", season: "", budgetMax: "" }, setFilters: vi.fn(),
  threadId: "thread-1", setThreadId: vi.fn(), isStreaming: false, setIsStreaming: vi.fn(),
  error: "", setError: vi.fn(), abortRef: { current: null }
};

describe("AiShoppingPage", () => {
  it("groups only real outfit products and leaves missing roles as text", () => {
    render(
      <MemoryRouter>
        <AiShoppingPage
          chatState={chatState}
          recommendations={[{
            spuId: 1, skuId: 2, spuCode: "TOP-1", name: "真实夏季上衣", categoryName: "T恤",
            salePrice: 139, outfitRole: "TOP", recommendationReason: "季节匹配", rankScore: 1.4
          }]}
          setRecommendations={vi.fn()}
          recommendationMeta={{
            hasAiResult: true, hasStrongMatch: true, recommendationStatus: "STRONG_MATCH",
            resolvedIntent: { requestType: "OUTFIT_ADVICE" },
            recommendedItems: [{ spuId: 1, skuId: 2, outfitRole: "TOP" }]
          }}
          setRecommendationMeta={vi.fn()}
          recommendationsLoaded
          setRecommendationsLoaded={vi.fn()}
          isRecommendationsLoading={false}
          setIsRecommendationsLoading={vi.fn()}
          onAction={vi.fn()}
          onRefreshCart={vi.fn().mockResolvedValue(undefined)}
        />
      </MemoryRouter>
    );

    expect(screen.getByRole("heading", { name: "上装" })).toBeVisible();
    expect(screen.getByText("真实夏季上衣")).toBeVisible();
    expect(screen.getByRole("heading", { name: "鞋履" })).toBeVisible();
    expect(screen.getAllByText("本组暂无真实匹配商品，请参考对话中的文字搭配建议。").length).toBeGreaterThan(0);
    expect(screen.queryByText(/占位鞋/)).not.toBeInTheDocument();
  });
});
