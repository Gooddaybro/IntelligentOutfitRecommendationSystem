import { ShoppingBag, ShoppingCart } from "lucide-react";
import { Link } from "react-router-dom";
import type { BehaviorEventType, RecommendationCandidate, RecommendationStatus } from "../../shared/api/types";
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
  recommendationStatus?: RecommendationStatus;
  isAttributed?: boolean;
};

export function ProductCard({ candidate, onAction, actionMetadata, position, onBehaviorEvent, variant = "standard", recommendationStatus, isAttributed = false }: ProductCardProps) {
  const shouldShowAiFacts = (recommendationStatus === "STRONG_MATCH" || recommendationStatus === "PARTIAL_MATCH") && isAttributed;

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
      <Link className="product-image" to={`/app/products/${candidate.spuId}`} aria-label={`查看${candidate.name}详情`}>
        {shouldShowAiFacts && <span className="ai-match-badge">AI 推荐</span>}
        {candidate.mainImageUrl ? (
          <img src={candidate.mainImageUrl} alt={candidate.name} onError={(event) => { event.currentTarget.style.display = "none"; }} />
        ) : (
          <span>暂无图片</span>
        )}
      </Link>
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
        {shouldShowAiFacts && candidate.recommendationReason && (
          <p className="recommendation-reason" data-testid="recommendation-reason">
            <strong>推荐理由</strong>
            {candidate.recommendationReason}
          </p>
        )}
        {shouldShowAiFacts && candidate.rankScore !== undefined && (
          <p className="recommendation-rank-score">排序分 {candidate.rankScore}</p>
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
