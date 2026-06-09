import { ShoppingBag, ShoppingCart } from "lucide-react";
import type { RecommendationCandidate } from "../../shared/api/types";
import { buildAddToCartAction, buildBuyNowAction, type PendingCommerceAction } from "../commerce-action/commerceActions";

type ProductCardProps = {
  candidate: RecommendationCandidate;
  onAction: (action: PendingCommerceAction) => void;
};

export function ProductCard({ candidate, onAction }: ProductCardProps) {
  return (
    <article className="product-card">
      <div className="product-image">
        {candidate.mainImageUrl ? <img src={candidate.mainImageUrl} alt={candidate.name} /> : <span>暂无图片</span>}
      </div>
      <div className="product-body">
        <div>
          <p className="eyebrow">{candidate.categoryName}</p>
          <h3>{candidate.name}</h3>
        </div>
        <div className="product-meta">
          <span>{candidate.color || "默认颜色"}</span>
          <span>{candidate.size || "默认尺码"}</span>
          <span>{candidate.fitType || "常规版型"}</span>
        </div>
        <div className="product-footer">
          <strong>￥{candidate.salePrice}</strong>
          <span>{candidate.stockStatus || "库存以结算为准"}</span>
        </div>
        <div className="product-actions">
          <button onClick={() => onAction(buildAddToCartAction(candidate))} title="加入购物车">
            <ShoppingCart size={16} />
            加购
          </button>
          <button className="primary-button" onClick={() => onAction(buildBuyNowAction(candidate))} title="立即购买">
            <ShoppingBag size={16} />
            购买
          </button>
        </div>
      </div>
    </article>
  );
}
