import { useEffect, useRef } from "react";
import { Sparkles } from "lucide-react";
import { ChatPanel } from "../features/assistant/ChatPanel";
import type { ChatPanelState, RecommendationResultMeta } from "../features/assistant/ChatPanel";
import { ProductCard } from "../features/catalog/ProductCard";
import type { PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { api } from "../shared/api/client";
import type { BehaviorEventType, CartItem, RecommendationCandidate } from "../shared/api/types";
import type { Dispatch, SetStateAction } from "react";

type AiShoppingPageProps = {
  cartItems: CartItem[];
  chatState: ChatPanelState;
  recommendations: RecommendationCandidate[];
  setRecommendations: Dispatch<SetStateAction<RecommendationCandidate[]>>;
  recommendationMeta?: RecommendationResultMeta;
  setRecommendationMeta: Dispatch<SetStateAction<RecommendationResultMeta | undefined>>;
  recommendationsLoaded: boolean;
  setRecommendationsLoaded: Dispatch<SetStateAction<boolean>>;
  isRecommendationsLoading: boolean;
  setIsRecommendationsLoading: Dispatch<SetStateAction<boolean>>;
  onAction: (action: PendingCommerceAction) => void;
  onRefreshCart: () => Promise<void>;
  onOpenCart: () => void;
};

export function AiShoppingPage({
  cartItems,
  chatState,
  recommendations,
  setRecommendations,
  recommendationMeta,
  setRecommendationMeta,
  recommendationsLoaded,
  setRecommendationsLoaded,
  isRecommendationsLoading,
  setIsRecommendationsLoading,
  onAction,
  onRefreshCart,
  onOpenCart
}: AiShoppingPageProps) {
  const quickPrompts = ["上班通勤", "约会穿搭", "学生党", "显高显瘦", "平价百搭", "秋冬保暖"];
  const cartTotal = cartItems.reduce((sum, item) => sum + item.salePrice * item.quantity, 0);
  const exposureKeysRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    async function loadInitialRecommendations() {
      if (recommendationsLoaded) {
        await onRefreshCart();
        return;
      }

      setIsRecommendationsLoading(true);
      try {
        setRecommendations(await api.recommendationCandidates({}));
        setRecommendationMeta(undefined);
        setRecommendationsLoaded(true);
        await onRefreshCart();
      } finally {
        setIsRecommendationsLoading(false);
      }
    }

    void loadInitialRecommendations();
  }, [
    onRefreshCart,
    recommendationsLoaded,
    setIsRecommendationsLoading,
    setRecommendations,
    setRecommendationMeta,
    setRecommendationsLoaded
  ]);

  function useHeroPrompt(prompt: string) {
    chatState.setDraft(prompt);
    window.requestAnimationFrame(() => {
      document.querySelector<HTMLTextAreaElement>("[data-testid='ai-chat-input']")?.focus();
    });
  }

  function behaviorEventId(eventType: BehaviorEventType) {
    return `${eventType.toLowerCase()}:${Date.now().toString(36)}:${Math.random().toString(36).slice(2, 8)}`;
  }

  function recordRecommendationEvent(
    eventType: BehaviorEventType,
    candidate: RecommendationCandidate,
    metadata?: Record<string, unknown>
  ) {
    if (!recommendationMeta?.hasAiResult || !chatState.threadId) {
      return;
    }
    void api
      .recordBehaviorEvent({
        eventId: behaviorEventId(eventType),
        eventType,
        spuId: candidate.spuId,
        skuId: candidate.skuId,
        threadId: chatState.threadId,
        metadata
      })
      .catch(() => undefined);
  }

  useEffect(() => {
    if (!recommendationMeta?.hasAiResult || !chatState.threadId) {
      return;
    }
    recommendations.forEach((candidate, index) => {
      const key = `${chatState.threadId}:${candidate.spuId}:${candidate.skuId}:exposed`;
      if (exposureKeysRef.current.has(key)) {
        return;
      }
      exposureKeysRef.current.add(key);
      recordRecommendationEvent("RECOMMENDATION_EXPOSED", candidate, { position: index + 1 });
    });
  }, [chatState.threadId, recommendationMeta?.hasAiResult, recommendations]);

  return (
    <main className="workbench outfit-workbench">
      <section className="stylist-hero" aria-label="AI 智能导购">
        <div className="hero-content">
          <p className="eyebrow">AI Outfit Stylist</p>
          <h1>让 AI 为你挑选合适穿搭</h1>
          <p>输入场景、预算、风格，快速生成专属商品推荐。</p>
          <form
            className="hero-search"
            onSubmit={(event) => {
              event.preventDefault();
              useHeroPrompt(chatState.draft || "我想找一套适合春天通勤的穿搭，显瘦一点，预算500以内");
            }}
          >
            <input
              value={chatState.draft}
              onChange={(event) => chatState.setDraft(event.target.value)}
              placeholder="我想找一套适合春天通勤的穿搭，显瘦一点，预算500以内..."
            />
            <button type="submit">
              <Sparkles size={18} />
              智能推荐
            </button>
          </form>
          <div className="quick-tags" aria-label="快捷场景">
            {quickPrompts.map((prompt) => (
              <button key={prompt} type="button" onClick={() => useHeroPrompt(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        </div>
      </section>

      <div className="ai-layout">
        <ChatPanel
          onRecommendations={(items, meta) => {
            setRecommendations(items);
            setRecommendationMeta(meta);
          }}
          state={chatState}
        />
        <section className="recommendation-panel" data-testid="recommendation-panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">AI 推荐商品</p>
              <h2>为你筛选的穿搭单品</h2>
            </div>
            <span>{isRecommendationsLoading ? "加载中" : `${recommendations.length} 件`}</span>
          </div>
          {recommendationMeta?.hasAiResult && !recommendationMeta.hasStrongMatch && (
            <p className="recommendation-notice">
              AI 暂时没有选出强匹配商品，你可以继续浏览下面的后端候选。
            </p>
          )}
          <div className="product-grid compact">
            {recommendations.map((candidate, index) => (
              <ProductCard
                key={`${candidate.spuId}-${candidate.skuId}`}
                candidate={candidate}
                onAction={onAction}
                actionMetadata={
                  recommendationMeta?.hasAiResult
                    ? { source: "ASSISTANT_RECOMMENDATION", threadId: chatState.threadId }
                    : undefined
                }
                position={index + 1}
                onBehaviorEvent={(event) => recordRecommendationEvent(event.eventType, event.candidate, event.metadata)}
              />
            ))}
          </div>
        </section>
        <aside className="side-panel outfit-list">
          <div className="section-heading">
            <div>
              <p className="eyebrow">搭配清单</p>
              <h2>我的穿搭组合</h2>
            </div>
            <span>{cartItems.length} 件</span>
          </div>
          <div className="mini-list" data-testid="mini-cart-list">
            {cartItems.length === 0 && <p className="muted">还没有商品。把喜欢的推荐加入清单后，这里会生成搭配摘要。</p>}
            {cartItems.map((item) => (
              <div key={item.skuId} className="mini-row outfit-row" data-testid="mini-cart-row">
                <div className="product-image small">
                  {item.mainImageUrl ? (
                    <img src={item.mainImageUrl} alt={item.name} onError={(event) => { event.currentTarget.style.display = "none"; }} />
                  ) : (
                    <span>暂无图片</span>
                  )}
                </div>
                <div>
                  <span>{item.name}</span>
                  <p>{item.color || "默认颜色"} · {item.size || "默认尺码"} · x {item.quantity}</p>
                </div>
                <strong>￥{(item.salePrice * item.quantity).toFixed(2)}</strong>
              </div>
            ))}
          </div>
          <div className="cart-summary">
            <div className="total-price">
              <span>清单总计</span>
              <strong>￥{cartTotal.toFixed(2)}</strong>
            </div>
            <p className="ai-review">AI 会根据你的需求优先推荐百搭、舒适、易组合的单品，最终加购和下单仍由你确认。</p>
            <button className="primary-button checkout-button" data-testid="open-cart-from-ai" onClick={onOpenCart}>
              去结算
            </button>
          </div>
        </aside>
      </div>
    </main>
  );
}
