import { ShoppingBag, ShoppingCart } from "lucide-react";
import type { BehaviorEventType, RecommendationCandidate } from "../../shared/api/types";
import {
  buildAddToCartAction,
  buildBuyNowAction,
  type CommerceActionMetadata,
  type PendingCommerceAction
} from "../commerce-action/commerceActions";

type ProductBehaviorEvent = {
  eventType: BehaviorEventType;
  candidate: RecommendationCandidate;
  metadata?: Record<string, unknown>;
};

export type ProductCardVariant = "standard" | "featured" | "supporting";

type ProductCardProps = {
  candidate: RecommendationCandidate;
  onAction: (action: PendingCommerceAction) => void;
  actionMetadata?: CommerceActionMetadata;
  position?: number;
  onBehaviorEvent?: (event: ProductBehaviorEvent) => void;
  variant?: ProductCardVariant;
};

export function ProductCard({ candidate, onAction, actionMetadata, position, onBehaviorEvent, variant = "standard" }: ProductCardProps) {
  const matchLabel = candidate.rankScore !== undefined ? `AI 匹配 ${Math.round(candidate.rankScore * 100)}%` : "AI 推荐";

  return (
    <article
      className={`product-card product-card--${variant}`}
      data-testid="recommendation-card"
      data-sku-id={candidate.skuId}
      data-variant={variant}
      onClick={() =>
        onBehaviorEvent?.({
          eventType: "RECOMMENDATION_CLICKED",
          candidate,
          metadata: position === undefined ? undefined : { position }
        })
      }
    >
      <div className="product-image">
        {variant === "featured" && <span className="featured-card-label">AI 首选</span>}
        {(candidate.rankScore !== undefined || candidate.recommendationReason) && <span className="ai-match-badge">{matchLabel}</span>}
        {candidate.mainImageUrl ? (
          <img src={candidate.mainImageUrl} alt={candidate.name} onError={(event) => { event.currentTarget.style.display = "none"; }} />
        ) : (
          <span>暂无图片</span>
        )}
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
        {candidate.recommendationReason && (
          <p className="recommendation-reason" data-testid="recommendation-reason">
            <strong>推荐理由</strong>
            {candidate.recommendationReason}
          </p>
        )}
        <div className="product-actions">
          <button
            data-testid="add-to-cart-action"
            onClick={(event) => {
              event.stopPropagation();
              onAction(buildAddToCartAction(candidate, 1, actionMetadata));
            }}
            title="加入购物车"
          >
            <ShoppingCart size={16} />
            加购
          </button>
          <button
            className="primary-button"
            data-testid="buy-now-action"
            onClick={(event) => {
              event.stopPropagation();
              onAction(buildBuyNowAction(candidate, 1, actionMetadata));
            }}
            title="立即购买"
          >
            <ShoppingBag size={16} />
            购买
          </button>
        </div>
      </div>
    </article>
  );
}
