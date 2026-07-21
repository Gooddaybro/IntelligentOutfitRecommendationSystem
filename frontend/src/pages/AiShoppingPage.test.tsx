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
  it("shows every browse fallback candidate without outfit grouping or AI attribution", () => {
    render(
      <MemoryRouter>
        <AiShoppingPage
          chatState={chatState}
          recommendations={[
            {
              spuId: 10, skuId: 20, spuCode: "BROWSE-1", name: "夏季亚麻短袖衬衫", categoryName: "衬衫",
              salePrice: 169, recommendationReason: "仅供浏览", rankScore: 0.8
            },
            {
              spuId: 12, skuId: 22, spuCode: "BROWSE-2", name: "通勤直筒长裤", categoryName: "长裤",
              salePrice: 199, outfitRole: "BOTTOM"
            }
          ]}
          setRecommendations={vi.fn()}
          recommendationMeta={{
            recommendationStatus: "BROWSE_FALLBACK",
            resolvedIntent: { requestType: "OUTFIT_ADVICE" },
            recommendedItems: [{ spuId: 10, skuId: 20, reason: "不应显示的 AI 理由" }]
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

    expect(screen.getByText("夏季亚麻短袖衬衫")).toBeVisible();
    expect(screen.getByText("通勤直筒长裤")).toBeVisible();
    expect(screen.queryByTestId("outfit-groups")).not.toBeInTheDocument();
    expect(screen.getAllByTestId("recommendation-card").every((card) => card.dataset.variant === "standard")).toBe(true);
    expect(screen.queryByText(/AI 首选|AI 推荐|不应显示的 AI 理由|仅供浏览/)).not.toBeInTheDocument();
  });

  it.each(["EMPTY", "FAILED"] as const)("does not show stale products when status is %s", (status) => {
    render(
      <MemoryRouter>
        <AiShoppingPage
          chatState={chatState}
          recommendations={[{
            spuId: 13, skuId: 23, spuCode: "STALE-1", name: "上一次的推荐", categoryName: "外套", salePrice: 299
          }]}
          setRecommendations={vi.fn()}
          recommendationMeta={{ recommendationStatus: status }}
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

    expect(screen.queryByText("上一次的推荐")).not.toBeInTheDocument();
  });

  it("groups a real partial-match top and renders missing bottom as text only", () => {
    render(
      <MemoryRouter>
        <AiShoppingPage
          chatState={chatState}
          recommendations={[{
            spuId: 11, skuId: 21, spuCode: "TOP-2", name: "真实局部匹配上装", categoryName: "T恤",
            salePrice: 139, outfitRole: "TOP", recommendationReason: "上装匹配", rankScore: 1.2
          }]}
          setRecommendations={vi.fn()}
          recommendationMeta={{
            recommendationStatus: "PARTIAL_MATCH",
            resolvedIntent: { requestType: "OUTFIT_ADVICE" },
            recommendedItems: [{ spuId: 11, skuId: 21, outfitRole: "TOP" }]
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

    expect(screen.getByTestId("outfit-groups")).toBeVisible();
    expect(screen.getByText("真实局部匹配上装")).toBeVisible();
    const bottomGroup = screen.getByRole("region", { name: "下装" });
    expect(bottomGroup.querySelector('[data-testid="recommendation-card"]')).toBeNull();
    expect(bottomGroup.querySelector("p")).not.toBeNull();
  });

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
            recommendationStatus: "STRONG_MATCH",
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
