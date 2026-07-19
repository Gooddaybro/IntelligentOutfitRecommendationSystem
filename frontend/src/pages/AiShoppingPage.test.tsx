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
            recommendedItems: [{ spuId: 1, skuId: 2, outfitRole: "TOP" }],
            mentionedItems: [{ spuId: 1, skuId: 2, outfitRole: "TOP" }]
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
    expect(screen.getAllByText("真实夏季上衣")).toHaveLength(2);
    expect(screen.getByRole("heading", { name: "鞋履" })).toBeVisible();
    expect(screen.getAllByText("暂未绑定商品").length).toBeGreaterThan(0);
    expect(screen.queryByText(/占位鞋/)).not.toBeInTheDocument();
  });

  it("shows only the weak-fallback product that Java bound to the conversation", () => {
    const candidates = Array.from({ length: 24 }, (_, index) => ({
      spuId: index + 1,
      skuId: index + 2,
      spuCode: `SPU-${index + 1}`,
      name: index === 0 ? "真实夏季上衣" : `候选商品 ${index + 1}`,
      categoryName: index === 0 ? "T恤" : "其他",
      salePrice: 100 + index,
      outfitRole: index === 0 ? "TOP" as const : undefined
    }));

    render(
      <MemoryRouter>
        <AiShoppingPage
          chatState={chatState}
          recommendations={candidates}
          setRecommendations={vi.fn()}
          recommendationMeta={{
            hasAiResult: true, hasStrongMatch: false, recommendationStatus: "WEAK_FALLBACK",
            resolvedIntent: { requestType: "OUTFIT_ADVICE" }, recommendedItems: [],
            mentionedItems: [{ spuId: 1, skuId: 2, outfitRole: "TOP" }]
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

    expect(screen.getByText("对话提及")).toBeVisible();
    expect(screen.getAllByText("真实夏季上衣")).toHaveLength(2);
    expect(screen.getByTestId("add-to-cart-action")).toBeVisible();
    expect(screen.getByText("已绑定 1 件 · 候选 24 件")).toBeVisible();
    expect(screen.queryByText("候选商品 2")).not.toBeInTheDocument();
  });

  it("does not offer commerce actions when the answer has no bound product ids", () => {
    render(
      <MemoryRouter>
        <AiShoppingPage
          chatState={chatState}
          recommendations={[{
            spuId: 1, skuId: 2, spuCode: "TOP-1", name: "未绑定候选", categoryName: "T恤", salePrice: 139
          }]}
          setRecommendations={vi.fn()}
          recommendationMeta={{
            hasAiResult: true, hasStrongMatch: false, recommendationStatus: "WEAK_FALLBACK",
            resolvedIntent: { requestType: "OUTFIT_ADVICE" }, recommendedItems: [], mentionedItems: []
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

    expect(screen.getByText(/本轮文字建议未绑定真实商品/)).toBeVisible();
    expect(screen.queryByTestId("add-to-cart-action")).not.toBeInTheDocument();
  });
});
