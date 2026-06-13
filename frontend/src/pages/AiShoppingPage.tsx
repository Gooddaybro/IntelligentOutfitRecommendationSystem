import { useEffect } from "react";
import { ChatPanel } from "../features/assistant/ChatPanel";
import type { ChatPanelState, RecommendationResultMeta } from "../features/assistant/ChatPanel";
import { ProductCard } from "../features/catalog/ProductCard";
import type { PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { api } from "../shared/api/client";
import type { CartItem, RecommendationCandidate } from "../shared/api/types";
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

  return (
    <main className="workbench ai-layout">
      <ChatPanel
        onRecommendations={(items, meta) => {
          setRecommendations(items);
          setRecommendationMeta(meta);
        }}
        state={chatState}
      />
      <section className="recommendation-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">推荐卡片</p>
            <h2>后端商品库候选</h2>
          </div>
          <span>{isRecommendationsLoading ? "加载中" : `${recommendations.length} 件`}</span>
        </div>
        {recommendationMeta?.hasAiResult && !recommendationMeta.hasStrongMatch && (
          <p className="recommendation-notice">
            AI 暂时没有选出强匹配商品，你可以继续浏览下面的后端候选。
          </p>
        )}
        <div className="product-grid compact">
          {recommendations.map((candidate) => (
            <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} />
          ))}
        </div>
      </section>
      <aside className="side-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">购物车</p>
            <h2>当前选择</h2>
          </div>
          <button onClick={onOpenCart}>结算</button>
        </div>
        <div className="mini-list">
          {cartItems.length === 0 && <p className="muted">还没有商品。</p>}
          {cartItems.map((item) => (
            <div key={item.skuId} className="mini-row">
              <span>{item.name}</span>
              <strong>x {item.quantity}</strong>
            </div>
          ))}
        </div>
      </aside>
    </main>
  );
}
