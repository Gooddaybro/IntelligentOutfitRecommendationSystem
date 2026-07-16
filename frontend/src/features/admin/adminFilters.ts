import type { AdminProduct } from "../../shared/api/adminTypes";

export type AdminProductFilters = {
  keyword: string;
  category: string;
  status: AdminProduct["status"] | "ALL";
};

export function filterAdminProducts(products: AdminProduct[], filters: AdminProductFilters): AdminProduct[] {
  const keyword = filters.keyword.trim().toLocaleLowerCase();
  return products.filter((product) => {
    const keywordMatches = !keyword || `${product.name} ${product.spuCode}`.toLocaleLowerCase().includes(keyword);
    const categoryMatches = filters.category === "ALL" || product.categoryName === filters.category;
    const statusMatches = filters.status === "ALL" || product.status === filters.status;
    return keywordMatches && categoryMatches && statusMatches;
  });
}
