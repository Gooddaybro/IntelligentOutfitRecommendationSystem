import type {
  Address,
  AssistantChatRequest,
  AssistantChatResponse,
  CartItem,
  CheckoutPreview,
  OrderResponse,
  ProductDetail,
  RecommendationCandidate,
  UserBodyDataRequest,
  UserBodyDataResponse,
  UserPreferencesRequest,
  UserPreferencesResponse,
  UserProfileRequest,
  UserProfileResponse
} from "./types";
import type { AdminCategory, AdminProduct, AdminProductInput, AdminSku } from "./adminTypes";

const catalog: RecommendationCandidate[] = [
  { spuId: 1001, skuId: 2001, spuCode: "PUFFER_COMMUTE", skuCode: "PUFFER-IVORY-M", name: "轻量通勤羽绒服", categoryName: "外套", color: "米白", size: "M", fitType: "合身", salePrice: 699, availableStock: 8, mainImageUrl: "/images/products/puffer-winter-light-main.jpg", styleTags: "通勤,简约" },
  { spuId: 1001, skuId: 2002, spuCode: "PUFFER_COMMUTE", skuCode: "PUFFER-IVORY-L", name: "轻量通勤羽绒服", categoryName: "外套", color: "米白", size: "L", fitType: "合身", salePrice: 699, availableStock: 3, mainImageUrl: "/images/products/puffer-winter-light-main.jpg", styleTags: "通勤,简约" },
  { spuId: 1002, skuId: 2011, spuCode: "TRENCH_COMMUTE", skuCode: "TRENCH-KHAKI-M", name: "浅卡其通勤风衣", categoryName: "外套", color: "浅卡其", size: "M", fitType: "宽松", salePrice: 599, availableStock: 12, mainImageUrl: "/images/products/jacket-commute-trench-main.jpg", styleTags: "通勤,自然" },
  { spuId: 1003, skuId: 2021, spuCode: "SHIRT_OXFORD", skuCode: "SHIRT-BLUE-M", name: "牛津纺通勤衬衫", categoryName: "上装", color: "雾蓝", size: "M", fitType: "合身", salePrice: 269, availableStock: 18, mainImageUrl: "/images/products/oxford-shirt-commute-main.jpg", styleTags: "通勤,基础" },
  { spuId: 1004, skuId: 2031, spuCode: "PANTS_TAPERED", skuCode: "PANTS-BLACK-M", name: "锥形通勤长裤", categoryName: "裤装", color: "炭黑", size: "M", fitType: "锥形", salePrice: 329, availableStock: 7, mainImageUrl: "/images/products/chino-commute-tapered-main.jpg", styleTags: "通勤,利落" },
  { spuId: 1005, skuId: 2041, spuCode: "KNIT_CARDIGAN", skuCode: "KNIT-OAT-M", name: "燕麦色针织开衫", categoryName: "上装", color: "燕麦", size: "M", fitType: "宽松", salePrice: 399, availableStock: 5, mainImageUrl: "/images/products/knit-minimal-cardigan-main.jpg", styleTags: "自然,简约" },
  { spuId: 1006, skuId: 2051, spuCode: "SKIRT_PLEATED", skuCode: "SKIRT-GRAY-M", name: "通勤百褶半身裙", categoryName: "裙装", color: "烟灰", size: "M", fitType: "A 字", salePrice: 359, availableStock: 9, mainImageUrl: "/images/products/skirt-commute-pleated-main.jpg", styleTags: "通勤,优雅" }
];

const categoryIds: Record<string, number> = { "上装": 1, "外套": 2, "裤装": 3, "裙装": 4, "鞋靴": 5 };

function createAdminProducts(): AdminProduct[] {
  return Array.from(new Map(catalog.map((item) => [item.spuId, item])).values()).map((item, index) => {
    const skus = catalog.filter((sku) => sku.spuId === item.spuId);
    const prices = skus.map((sku) => sku.salePrice);
    return {
      spuId: item.spuId,
      spuCode: item.spuCode,
      name: item.name,
      categoryId: categoryIds[item.categoryName] || 99,
      categoryName: item.categoryName,
      mainImageUrl: item.mainImageUrl,
      minPrice: Math.min(...prices),
      maxPrice: Math.max(...prices),
      skuCount: skus.length,
      totalStock: skus.reduce((sum, sku) => sum + (sku.availableStock || 0), 0),
      status: "ON_SALE",
      createdAt: `2026-07-${String(index + 1).padStart(2, "0")}T09:00:00Z`,
      description: "水木商城演示商品",
      styleTags: item.styleTags?.split(",") || []
    };
  });
}

function createAdminInventory(): AdminSku[] {
  return catalog.map((item) => ({
    skuId: item.skuId,
    skuCode: item.skuCode || String(item.skuId),
    spuId: item.spuId,
    productName: item.name,
    color: item.color,
    size: item.size,
    salePrice: item.salePrice,
    availableStock: item.availableStock || 0,
    lowStockThreshold: 5,
    status: "ACTIVE"
  }));
}

function createAdminCategories(): AdminCategory[] {
  return Object.entries(categoryIds).map(([name, id], index) => ({ id, name, parentId: null, level: 1, sortOrder: index + 1, enabled: true, productCount: new Set(catalog.filter((item) => item.categoryName === name).map((item) => item.spuId)).size }));
}

let cartItems: CartItem[] = [];
let orders: OrderResponse[] = [];
let addressBook: Address[] = [{ id: 1, recipientName: "林木", phone: "138****2026", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号", isDefault: true }];
let favoriteSpuIds = new Set<number>([1002]);
let profile: UserProfileResponse = { userId: 1, nickname: "林木", avatarUrl: null, gender: null, birthday: null };
let bodyData: UserBodyDataResponse = { userId: 1 };
let preferences: UserPreferencesResponse = { userId: 1, preferredStyles: ["自然", "通勤"], preferredColors: ["米白", "鼠尾草绿"], dislikedColors: [], preferredCategories: ["外套"], budgetMin: 200, budgetMax: 800 };
let adminProducts = createAdminProducts();
let adminInventory = createAdminInventory();
let adminCategories = createAdminCategories();

function productDetail(spuId: number): ProductDetail {
  const sku = catalog.find((item) => item.spuId === spuId);
  if (!sku) throw new Error("商品不存在");
  const prices = catalog.filter((item) => item.spuId === spuId).map((item) => item.salePrice);
  return {
    spuId,
    spuCode: sku.spuCode,
    name: sku.name,
    categoryName: sku.categoryName,
    mainImageUrl: sku.mainImageUrl,
    fitType: sku.fitType,
    minPrice: Math.min(...prices),
    maxPrice: Math.max(...prices),
    description: "为日常通勤挑选的轻松廓形，在舒适度与利落感之间保持平衡。",
    materials: ["面料信息以商品标签为准"],
    styleTags: sku.styleTags?.split(",")
  };
}

function toCartItem(sku: RecommendationCandidate, quantity: number): CartItem {
  return { id: sku.skuId, userId: 1, skuId: sku.skuId, spuId: sku.spuId, skuCode: sku.skuCode || String(sku.skuId), spuCode: sku.spuCode, name: sku.name, categoryName: sku.categoryName, color: sku.color, size: sku.size, salePrice: sku.salePrice, mainImageUrl: sku.mainImageUrl, quantity, availableStock: sku.availableStock };
}

export function resetMockApi() {
  cartItems = [];
  orders = [];
  addressBook = [{ id: 1, recipientName: "林木", phone: "138****2026", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号", isDefault: true }];
  favoriteSpuIds = new Set([1002]);
  adminProducts = createAdminProducts();
  adminInventory = createAdminInventory();
  adminCategories = createAdminCategories();
}

export const mockApi = {
  login: async (_username: string, _password: string) => ({ accessToken: "mock-access-token", refreshToken: "mock-refresh-token", tokenType: "Bearer", expiresIn: 3600 }),
  register: async (username: string, _password: string, _email?: string) => ({ userId: 1, username, status: "ACTIVE" }),
  me: async () => ({ userId: 1, username: "水木体验官", role: "ADMIN" }),
  profile: async () => ({ ...profile }),
  updateProfile: async (request: UserProfileRequest) => (profile = { ...profile, ...request }),
  bodyData: async () => ({ ...bodyData }),
  updateBodyData: async (request: UserBodyDataRequest) => (bodyData = { ...bodyData, ...request }),
  preferences: async () => ({ ...preferences }),
  updatePreferences: async (request: UserPreferencesRequest) => (preferences = { ...preferences, ...request }),
  searchProducts: async (keyword: string) => {
    const normalized = keyword.trim().toLowerCase();
    return Array.from(new Map(catalog.filter((item) => !normalized || `${item.name}${item.categoryName}${item.styleTags}`.toLowerCase().includes(normalized)).map((item) => [item.spuId, item])).values()).map((item) => ({ spuId: item.spuId, spuCode: item.spuCode, name: item.name, categoryName: item.categoryName, mainImageUrl: item.mainImageUrl, fitType: item.fitType, minPrice: item.salePrice, maxPrice: item.salePrice }));
  },
  recommendationCandidates: async (params: Partial<AssistantChatRequest>) => catalog.filter((item) => !params.category || item.categoryName === params.category),
  productDetail: async (spuId: number) => productDetail(spuId),
  chat: async (_request: AssistantChatRequest): Promise<AssistantChatResponse> => ({ threadId: "mock-thread", answer: "我为你准备了几件适合日常通勤的自然风单品。", recommendedSpuIds: catalog.slice(0, 3).map((item) => item.spuId), candidatesCount: catalog.length }),
  recordBehaviorEvent: async (request: { eventId: string }) => ({ eventId: request.eventId }),
  cart: async () => [...cartItems],
  addCartItem: async (skuId: number, quantity: number) => {
    const sku = catalog.find((item) => item.skuId === skuId);
    if (!sku) throw new Error("SKU 不存在");
    const existing = cartItems.find((item) => item.skuId === skuId);
    cartItems = existing ? cartItems.map((item) => item.skuId === skuId ? { ...item, quantity: item.quantity + quantity } : item) : [...cartItems, toCartItem(sku, quantity)];
    return [...cartItems];
  },
  updateCartItem: async (skuId: number, quantity: number) => (cartItems = cartItems.map((item) => item.skuId === skuId ? { ...item, quantity } : item)),
  removeCartItem: async (skuId: number) => (cartItems = cartItems.filter((item) => item.skuId !== skuId)),
  addresses: async () => [...addressBook],
  saveAddress: async (address: Omit<Address, "id"> & { id?: number }) => {
    const saved = { ...address, id: address.id || Date.now() } as Address;
    addressBook = address.id ? addressBook.map((item) => item.id === address.id ? saved : item) : [...addressBook, saved];
    return [...addressBook];
  },
  removeAddress: async (id: number) => (addressBook = addressBook.filter((item) => item.id !== id)),
  favorites: async () => Array.from(new Map(catalog.filter((item) => favoriteSpuIds.has(item.spuId)).map((item) => [item.spuId, item])).values()),
  addFavorite: async (spuId: number) => { favoriteSpuIds.add(spuId); return Array.from(new Map(catalog.filter((item) => favoriteSpuIds.has(item.spuId)).map((item) => [item.spuId, item])).values()); },
  removeFavorite: async (spuId: number) => { favoriteSpuIds.delete(spuId); return Array.from(new Map(catalog.filter((item) => favoriteSpuIds.has(item.spuId)).map((item) => [item.spuId, item])).values()); },
  checkoutPreview: async (skuIds: number[], _addressId?: number): Promise<CheckoutPreview> => {
    const items = cartItems.filter((item) => skuIds.includes(item.skuId));
    const invalidReasons = items.flatMap((item) => (item.availableStock ?? 0) < item.quantity ? [`${item.name} 库存不足`] : []);
    const merchandiseAmount = items.reduce((sum, item) => sum + item.salePrice * item.quantity, 0);
    return { items, merchandiseAmount, shippingAmount: 0, discountAmount: 0, payableAmount: merchandiseAmount, invalidReasons };
  },
  createOrder: async (skuIds: number[], addressId?: number) => {
    const items = cartItems.filter((item) => skuIds.includes(item.skuId)).map((item) => ({ ...item, productName: item.name, lineAmount: item.salePrice * item.quantity }));
    const order = { orderNo: `DEMO-${Date.now()}`, status: "PENDING_PAYMENT", totalAmount: items.reduce((sum, item) => sum + item.lineAmount, 0), items, createdAt: new Date().toISOString(), address: addressBook.find((item) => item.id === addressId) } satisfies OrderResponse;
    orders = [order, ...orders];
    cartItems = cartItems.filter((item) => !skuIds.includes(item.skuId));
    return order;
  },
  buyNow: async (skuId: number, quantity: number) => {
    const sku = catalog.find((item) => item.skuId === skuId);
    if (!sku) throw new Error("SKU 不存在");
    const item = toCartItem(sku, quantity);
    const order = { orderNo: `DEMO-${Date.now()}`, status: "PENDING_PAYMENT", totalAmount: item.salePrice * quantity, items: [{ ...item, productName: item.name, lineAmount: item.salePrice * quantity }], createdAt: new Date().toISOString() } satisfies OrderResponse;
    orders = [order, ...orders];
    return order;
  },
  orders: async () => [...orders],
  order: async (orderNo: string) => {
    const order = orders.find((item) => item.orderNo === orderNo);
    if (!order) throw new Error("订单不存在");
    return order;
  },
  cancelOrder: async (orderNo: string) => {
    orders = orders.map((item) => item.orderNo === orderNo ? { ...item, status: "CANCELLED", closedAt: new Date().toISOString(), closeReason: "用户取消" } : item);
    return orders.find((item) => item.orderNo === orderNo)!;
  },
  confirmReceipt: async (orderNo: string) => {
    orders = orders.map((item) => item.orderNo === orderNo ? { ...item, status: "COMPLETED" } : item);
    return orders.find((item) => item.orderNo === orderNo)!;
  },
  pay: async (orderNo: string, channel = "MOCK") => ({ paymentNo: `PAY-${orderNo}`, orderNo, amount: orders.find((item) => item.orderNo === orderNo)?.totalAmount || 0, channel, status: "PENDING" }),
  payment: async (paymentNo: string) => ({ paymentNo, orderNo: paymentNo.replace("PAY-", ""), amount: 0, channel: "MOCK", status: "SUCCESS", paidAt: new Date().toISOString() }),
  payMock: async (orderNo: string) => {
    const paidAt = new Date().toISOString();
    orders = orders.map((item) => item.orderNo === orderNo ? { ...item, status: "PAID", paidAt } : item);
    return { paymentNo: `PAY-${orderNo}`, orderNo, amount: orders.find((item) => item.orderNo === orderNo)?.totalAmount || 0, channel: "MOCK", status: "SUCCESS", paidAt };
  },
  adminOverview: async () => ({
    onSaleProducts: adminProducts.filter((item) => item.status === "ON_SALE").length,
    skuCount: adminInventory.length,
    lowStockCount: adminInventory.filter((item) => item.availableStock <= item.lowStockThreshold).length,
    pendingShipmentOrders: orders.filter((item) => item.status === "PAID").length,
    afterSaleOrders: 0,
    orderCount: orders.length,
    paidAmount: orders.filter((item) => item.status === "PAID").reduce((sum, item) => sum + item.totalAmount, 0),
    rangeLabel: "最近 30 天",
    trend: [],
    hotProducts: []
  }),
  adminProducts: async () => adminProducts.map((item) => ({ ...item })),
  adminSaveProduct: async (input: AdminProductInput) => {
    if (input.spuId) {
      const existing = adminProducts.find((item) => item.spuId === input.spuId);
      if (!existing) throw new Error("商品不存在");
      const saved = { ...existing, ...input, spuId: existing.spuId };
      adminProducts = adminProducts.map((item) => item.spuId === saved.spuId ? saved : item);
      return { ...saved };
    }
    const spuId = Math.max(0, ...adminProducts.map((item) => item.spuId)) + 1;
    const saved: AdminProduct = { ...input, spuId, skuCount: 0, totalStock: 0, createdAt: new Date().toISOString() };
    adminProducts = [saved, ...adminProducts];
    return { ...saved };
  },
  adminSetProductStatus: async (spuId: number, status: AdminProduct["status"]) => {
    adminProducts = adminProducts.map((item) => item.spuId === spuId ? { ...item, status } : item);
    const product = adminProducts.find((item) => item.spuId === spuId);
    if (!product) throw new Error("商品不存在");
    return { ...product };
  },
  adminCategories: async () => adminCategories.map((item) => ({ ...item })),
  adminUpdateCategory: async (category: AdminCategory) => {
    if (category.parentId === category.id) throw new Error("分类不能以自身作为父分类");
    adminCategories = adminCategories.map((item) => item.id === category.id ? { ...category } : item);
    const updated = adminCategories.find((item) => item.id === category.id);
    if (!updated) throw new Error("分类不存在");
    return { ...updated };
  },
  adminInventory: async () => adminInventory.map((item) => ({ ...item })),
  adminAdjustInventory: async (skuId: number, targetStock: number, reason: string) => {
    if (!Number.isInteger(targetStock) || targetStock < 0) throw new Error("库存必须是非负整数");
    if (!reason.trim()) throw new Error("请填写库存调整原因");
    const current = adminInventory.find((item) => item.skuId === skuId);
    if (!current) throw new Error("SKU 不存在");
    const adjustment = { beforeStock: current.availableStock, afterStock: targetStock, reason: reason.trim(), operator: "水木体验官", adjustedAt: new Date().toISOString() };
    adminInventory = adminInventory.map((item) => item.skuId === skuId ? { ...item, availableStock: targetStock, lastAdjustment: adjustment } : item);
    adminProducts = adminProducts.map((item) => item.spuId === current.spuId ? { ...item, totalStock: item.totalStock + targetStock - current.availableStock } : item);
    return { ...adminInventory.find((item) => item.skuId === skuId)! };
  }
};
