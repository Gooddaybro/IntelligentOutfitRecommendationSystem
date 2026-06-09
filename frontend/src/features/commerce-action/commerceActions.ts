import type { RecommendationCandidate } from "../../shared/api/types";

export type CommerceActionKind = "ADD_TO_CART" | "BUY_NOW";

export type PendingCommerceAction = {
  kind: CommerceActionKind;
  skuId: number;
  quantity: number;
  productName: string;
  unitPrice: number;
};

export function buildAddToCartAction(candidate: RecommendationCandidate, quantity = 1): PendingCommerceAction {
  return {
    kind: "ADD_TO_CART",
    skuId: candidate.skuId,
    quantity,
    productName: candidate.name,
    unitPrice: candidate.salePrice
  };
}

export function buildBuyNowAction(candidate: RecommendationCandidate, quantity = 1): PendingCommerceAction {
  return {
    kind: "BUY_NOW",
    skuId: candidate.skuId,
    quantity,
    productName: candidate.name,
    unitPrice: candidate.salePrice
  };
}

export function actionConfirmText(action: PendingCommerceAction): string {
  const verb = action.kind === "BUY_NOW" ? "立即下单" : "加入购物车";
  return `${verb}：${action.productName} x ${action.quantity}`;
}
