import type { RecommendationCandidate } from "../../shared/api/types";

export type SkuSelection = { color?: string; size?: string };

export function resolveSku(skus: RecommendationCandidate[], selection: SkuSelection) {
  if (!selection.color || !selection.size) return undefined;
  return skus.find((sku) => sku.color === selection.color && sku.size === selection.size);
}

export function missingSkuOptions(selection: SkuSelection) {
  return (["color", "size"] as const).filter((key) => !selection[key]);
}
