import type { RecommendationCandidate } from "../../shared/api/types";

export type CommerceActionKind = "ADD_TO_CART" | "BUY_NOW";
export type CommerceActionSource = "ASSISTANT_RECOMMENDATION";

export type CommerceActionMetadata = {
  source?: CommerceActionSource;
  threadId?: string;
};

export type PendingCommerceAction = {
  kind: CommerceActionKind;
  spuId: number;
  skuId: number;
  quantity: number;
  productName: string;
  unitPrice: number;
  source?: CommerceActionSource;
  threadId?: string;
};

export function buildAddToCartAction(
  candidate: RecommendationCandidate,
  quantity = 1,
  metadata: CommerceActionMetadata = {}
): PendingCommerceAction {
  return {
    kind: "ADD_TO_CART",
    spuId: candidate.spuId,
    skuId: candidate.skuId,
    quantity,
    productName: candidate.name,
    unitPrice: candidate.salePrice,
    ...metadata
  };
}

export function buildBuyNowAction(
  candidate: RecommendationCandidate,
  quantity = 1,
  metadata: CommerceActionMetadata = {}
): PendingCommerceAction {
  return {
    kind: "BUY_NOW",
    spuId: candidate.spuId,
    skuId: candidate.skuId,
    quantity,
    productName: candidate.name,
    unitPrice: candidate.salePrice,
    ...metadata
  };
}

export function actionConfirmText(action: PendingCommerceAction): string {
  const verb = action.kind === "BUY_NOW" ? "立即下单" : "加入购物车";
  return `${verb}：${action.productName} x ${action.quantity}`;
}
