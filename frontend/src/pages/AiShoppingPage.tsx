import { useEffect, useState } from "react";
import { ChatPanel } from "../features/assistant/ChatPanel";
import { ProductCard } from "../features/catalog/ProductCard";
import type { PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { api } from "../shared/api/client";
import type { CartItem, RecommendationCandidate } from "../shared/api/types";

type AiShoppingPageProps = {
  cartItems: CartItem[];
  onAction: (action: PendingCommerceAction) => void;
  onRefreshCart: () => Promise<void>;
  onOpenCart: () => void;
};

export function AiShoppingPage({ cartItems, onAction, onRefreshCart, onOpenCart }: AiShoppingPageProps) {
  const [recommendations, setRecommendations] = useState<RecommendationCandidate[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    async function loadInitialRecommendations() {
      setIsLoading(true);
      try {
        setRecommendations(await api.recommendationCandidates({}));
        await onRefreshCart();
      } finally {
        setIsLoading(false);
      }
    }

    void loadInitialRecommendations();
  }, [onRefreshCart]);

  return (
    <main className="workbench ai-layout">
      <ChatPanel onRecommendations={setRecommendations} />
      <section className="recommendation-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">推荐卡片</p>
            <h2>后端商品库候选</h2>
          </div>
          <span>{isLoading ? "加载中" : `${recommendations.length} 件`}</span>
        </div>
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
