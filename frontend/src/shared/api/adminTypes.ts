export type AdminProductStatus = "DRAFT" | "ON_SALE" | "OFF_SHELF" | "DELETED";
export type AdminUserStatus = "ACTIVE" | "DISABLED";

export type AdminOverview = {
  onSaleProducts: number;
  skuCount: number;
  lowStockCount: number;
  pendingShipmentOrders: number;
  afterSaleOrders: number;
  orderCount: number;
  paidAmount: number;
  rangeLabel: string;
  trend: Array<{ label: string; amount: number }>;
  hotProducts: Array<{ spuId: number; name: string; sales: number }>;
};

export type AdminProduct = {
  spuId: number;
  spuCode: string;
  name: string;
  categoryId: number;
  categoryName: string;
  mainImageUrl?: string;
  minPrice: number;
  maxPrice: number;
  skuCount: number;
  totalStock: number;
  status: AdminProductStatus;
  createdAt: string;
  description?: string;
  styleTags?: string[];
};

export type AdminProductInput = Omit<AdminProduct, "spuId" | "skuCount" | "totalStock" | "createdAt"> & { spuId?: number };

export type AdminCategory = {
  id: number;
  name: string;
  parentId?: number | null;
  level: 1 | 2;
  sortOrder: number;
  enabled: boolean;
  productCount: number;
};

export type InventoryAdjustment = {
  beforeStock: number;
  afterStock: number;
  reason: string;
  operator: string;
  adjustedAt: string;
};

export type AdminSku = {
  skuId: number;
  skuCode: string;
  spuId: number;
  productName: string;
  color?: string;
  size?: string;
  salePrice: number;
  availableStock: number;
  lowStockThreshold: number;
  status: "ACTIVE" | "INACTIVE";
  lastAdjustment?: InventoryAdjustment;
};

export type AdminOrder = {
  orderNo: string;
  username: string;
  status: string;
  paymentStatus: string;
  totalAmount: number;
  itemCount: number;
  createdAt: string;
  availableActions: Array<"SHIP" | "CANCEL" | "AFTER_SALE">;
  addressSummary?: string;
  shipment?: { carrier: string; trackingNo: string };
};

export type AdminUser = {
  userId: number;
  username: string;
  nickname?: string;
  email?: string;
  phone?: string;
  status: AdminUserStatus;
  registeredAt: string;
  orderCount: number;
  paidAmount: number;
};

export type AdminAnalytics = {
  rangeLabel: string;
  orderCount: number;
  paidAmount: number;
  funnel: { exposed: number; clicked: number; cartAdded: number; purchased: number; definition: string };
  trend: Array<{ label: string; orderCount: number; paidAmount: number }>;
  hotProducts: Array<{ spuId: number; name: string; sales: number; paidAmount: number }>;
  categoryTrend: Array<{ categoryName: string; sales: number }>;
};

export type AdminAuditLog = {
  id: number;
  operator: string;
  action: string;
  targetType: string;
  targetId: string;
  result: "SUCCESS" | "FAILED";
  summary: string;
  createdAt: string;
};
