export const APP_PATHS = {
  home: "/app/home",
  ai: "/app/ai",
  products: "/app/products",
  productDetail: (spuId: number | string) => `/app/products/${spuId}`,
  cart: "/app/cart",
  orders: "/app/orders",
  profile: "/app/profile"
} as const;

export function isCustomerPath(pathname: string): boolean {
  return pathname === "/app" || pathname.startsWith("/app/");
}

export const APP_NAV_ITEMS = [
  { key: "home", label: "今日", to: APP_PATHS.home },
  { key: "ai", label: "AI 造型师", to: APP_PATHS.ai },
  { key: "products", label: "探索商品", to: APP_PATHS.products },
  { key: "cart", label: "购物袋", to: APP_PATHS.cart },
  { key: "orders", label: "我的订单", to: APP_PATHS.orders },
  { key: "profile", label: "个人中心", to: APP_PATHS.profile }
] as const;

export const ADMIN_NAV_ITEMS = [
  { key: "dashboard", label: "数据概览", to: "/admin" },
  { key: "products", label: "商品管理", to: "/admin/products" },
  { key: "categories", label: "分类管理", to: "/admin/categories" },
  { key: "inventory", label: "SKU / 库存", to: "/admin/inventory" },
  { key: "orders", label: "订单管理", to: "/admin/orders" },
  { key: "users", label: "用户管理", to: "/admin/users" },
  { key: "analytics", label: "经营分析", to: "/admin/analytics" },
  { key: "audit", label: "操作日志", to: "/admin/audit-logs" }
] as const;
