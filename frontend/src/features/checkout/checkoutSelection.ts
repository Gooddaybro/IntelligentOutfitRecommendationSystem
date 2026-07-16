export function parseCheckoutSkuIds(value: string | null): number[] {
  return Array.from(new Set((value || "").split(",").map(Number).filter((id) => Number.isInteger(id) && id > 0)));
}

export function serializeCheckoutSkuIds(ids: number[]) {
  return Array.from(new Set(ids.filter((id) => Number.isInteger(id) && id > 0))).join(",");
}
