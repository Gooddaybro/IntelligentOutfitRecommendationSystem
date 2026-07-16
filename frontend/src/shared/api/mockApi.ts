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
import type { AdminAnalytics, AdminAuditLog, AdminCategory, AdminOrder, AdminProduct, AdminProductInput, AdminSku, AdminUser, AdminUserStatus } from "./adminTypes";

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

function cloneOrder(order: AdminOrder): AdminOrder {
  return { ...order, availableActions: [...order.availableActions], shipment: order.shipment ? { ...order.shipment } : undefined };
}

function createAdminOrders(): AdminOrder[] {
  return [
    { orderNo: "ORD-20260716-001", username: "linmu", status: "PAID", paymentStatus: "PAID", totalAmount: 699, itemCount: 2, createdAt: "2026-07-16T09:00:00Z", availableActions: ["SHIP"], addressSummary: "\u6d59\u6c5f\u7701\u676d\u5dde\u5e02\u897f\u6e56\u533a\u6587\u4e00\u8def 188 \u53f7" },
    { orderNo: "ORD-20260715-006", username: "qingmu", status: "SHIPPED", paymentStatus: "PAID", totalAmount: 599, itemCount: 1, createdAt: "2026-07-15T14:30:00Z", availableActions: ["AFTER_SALE"], addressSummary: "\u6d59\u6c5f\u7701\u676d\u5dde\u5e02\u4f59\u676d\u533a\u672a\u6765\u79d1\u6280\u57ce", shipment: { carrier: "\u4eac\u4e1c\u7269\u6d41", trackingNo: "JD20260715006" } },
    { orderNo: "ORD-20260714-003", username: "yemu", status: "UNPAID", paymentStatus: "UNPAID", totalAmount: 329, itemCount: 1, createdAt: "2026-07-14T18:10:00Z", availableActions: ["CANCEL"], addressSummary: "\u4e0a\u6d77\u5e02\u5f90\u6c47\u533a" },
    { orderNo: "ORD-20260712-002", username: "linmu", status: "COMPLETED", paymentStatus: "PAID", totalAmount: 269, itemCount: 1, createdAt: "2026-07-12T11:20:00Z", availableActions: ["AFTER_SALE"], addressSummary: "\u6d59\u6c5f\u7701\u676d\u5dde\u5e02\u897f\u6e56\u533a\u6587\u4e00\u8def 188 \u53f7", shipment: { carrier: "\u987a\u4e30\u901f\u8fd0", trackingNo: "SF20260712002" } }
  ];
}

function createAdminUsers(): AdminUser[] {
  return [
    { userId: 10001, username: "linmu", nickname: "\u6797\u6728", email: "linmu@example.com", phone: "13800000000", status: "ACTIVE", registeredAt: "2026-07-01T09:00:00Z", orderCount: 3, paidAmount: 1299 },
    { userId: 10002, username: "qingmu", nickname: "\u9752\u6728", email: "qingmu@example.com", phone: "13900000000", status: "ACTIVE", registeredAt: "2026-07-08T10:10:00Z", orderCount: 1, paidAmount: 599 },
    { userId: 10003, username: "yemu", nickname: "\u53f6\u6728", email: "yemu@example.com", phone: "13700000000", status: "DISABLED", registeredAt: "2026-06-28T16:00:00Z", orderCount: 1, paidAmount: 0 }
  ];
}

function createAdminAuditLogs(): AdminAuditLog[] {
  return [
    { id: 1, operator: "\u7cfb\u7edf", action: "CREATE_PRODUCT", targetType: "SPU", targetId: "1001", result: "SUCCESS", summary: "\u521d\u59cb\u5316\u6f14\u793a\u5546\u54c1\u6570\u636e", createdAt: "2026-07-16T08:00:00Z" },
    { id: 2, operator: "\u8fd0\u8425\u7ba1\u7406\u5458", action: "ADJUST_STOCK", targetType: "SKU", targetId: "2002", result: "SUCCESS", summary: "\u4f4e\u5e93\u5b58 SKU \u8fdb\u5165\u9884\u8b66\u6c60", createdAt: "2026-07-16T08:30:00Z" }
  ];
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
let adminOrderRows = createAdminOrders();
let adminUserRows = createAdminUsers();
let adminAuditLogRows = createAdminAuditLogs();

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
  adminOrderRows = createAdminOrders();
  adminUserRows = createAdminUsers();
  adminAuditLogRows = createAdminAuditLogs();
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
    pendingShipmentOrders: adminOrderRows.filter((item) => item.status === "PAID" && item.availableActions.includes("SHIP")).length,
    afterSaleOrders: adminOrderRows.filter((item) => item.availableActions.includes("AFTER_SALE")).length,
    orderCount: adminOrderRows.length,
    paidAmount: adminOrderRows.filter((item) => item.paymentStatus === "PAID").reduce((sum, item) => sum + item.totalAmount, 0),
    rangeLabel: "\u6700\u8fd1 30 \u5929",
    trend: [
      { label: "07-12", amount: 269 },
      { label: "07-14", amount: 0 },
      { label: "07-15", amount: 599 },
      { label: "07-16", amount: 699 }
    ],
    hotProducts: adminProducts.slice(0, 3).map((item, index) => ({ spuId: item.spuId, name: item.name, sales: 12 - index * 3 }))
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
  },
  adminOrders: async () => adminOrderRows.map(cloneOrder),
  adminShipOrder: async (orderNo: string, carrier: string, trackingNo: string) => {
    const trimmedCarrier = carrier.trim();
    const trimmedTrackingNo = trackingNo.trim();
    if (!trimmedCarrier || !trimmedTrackingNo) throw new Error("\u8bf7\u586b\u5199\u627f\u8fd0\u5546\u548c\u8fd0\u5355\u53f7");
    const current = adminOrderRows.find((item) => item.orderNo === orderNo);
    if (!current) throw new Error("\u8ba2\u5355\u4e0d\u5b58\u5728");
    if (current.status !== "PAID" || !current.availableActions.includes("SHIP")) throw new Error("\u5f53\u524d\u8ba2\u5355\u4e0d\u53ef\u53d1\u8d27");
    const updated: AdminOrder = { ...current, status: "SHIPPED", availableActions: current.availableActions.filter((action) => action !== "SHIP"), shipment: { carrier: trimmedCarrier, trackingNo: trimmedTrackingNo } };
    adminOrderRows = adminOrderRows.map((item) => item.orderNo === orderNo ? updated : item);
    adminAuditLogRows = [{ id: Date.now(), operator: "\u8fd0\u8425\u7ba1\u7406\u5458", action: "SHIP_ORDER", targetType: "ORDER", targetId: orderNo, result: "SUCCESS", summary: `${trimmedCarrier} ${trimmedTrackingNo}`, createdAt: new Date().toISOString() }, ...adminAuditLogRows];
    return cloneOrder(updated);
  },
  adminUsers: async () => adminUserRows.map((item) => ({ ...item })),
  adminSetUserStatus: async (userId: number, status: AdminUserStatus) => {
    const current = adminUserRows.find((item) => item.userId === userId);
    if (!current) throw new Error("\u7528\u6237\u4e0d\u5b58\u5728");
    const updated = { ...current, status };
    adminUserRows = adminUserRows.map((item) => item.userId === userId ? updated : item);
    adminAuditLogRows = [{ id: Date.now(), operator: "\u8fd0\u8425\u7ba1\u7406\u5458", action: status === "DISABLED" ? "DISABLE_USER" : "ENABLE_USER", targetType: "USER", targetId: String(userId), result: "SUCCESS", summary: `${updated.username} \u72b6\u6001\u53d8\u66f4\u4e3a ${status}`, createdAt: new Date().toISOString() }, ...adminAuditLogRows];
    return { ...updated };
  },
  adminAnalytics: async (): Promise<AdminAnalytics> => ({
    rangeLabel: "\u6700\u8fd1 30 \u5929",
    orderCount: adminOrderRows.length,
    paidAmount: adminOrderRows.filter((item) => item.paymentStatus === "PAID").reduce((sum, item) => sum + item.totalAmount, 0),
    funnel: { exposed: 1280, clicked: 426, cartAdded: 96, purchased: 28, definition: "\u66dd\u5149\u4e3a\u5546\u54c1\u5361\u7247\u8fdb\u5165\u89c6\u53e3\uff0c\u70b9\u51fb\u4e3a\u8fdb\u5165\u8be6\u60c5\u6216 AI \u63a8\u8350\u5361\u7247\uff0c\u6210\u4ea4\u4e3a\u5df2\u652f\u4ed8\u8ba2\u5355\u3002" },
    trend: [
      { label: "07-12", orderCount: 1, paidAmount: 269 },
      { label: "07-14", orderCount: 1, paidAmount: 0 },
      { label: "07-15", orderCount: 1, paidAmount: 599 },
      { label: "07-16", orderCount: 1, paidAmount: 699 }
    ],
    hotProducts: adminProducts.slice(0, 4).map((item, index) => ({ spuId: item.spuId, name: item.name, sales: 14 - index * 2, paidAmount: item.minPrice * (14 - index * 2) })),
    categoryTrend: [
      { categoryName: "\u5916\u5957", sales: 18 },
      { categoryName: "\u4e0a\u88c5", sales: 12 },
      { categoryName: "\u88e4\u88c5", sales: 9 },
      { categoryName: "\u88d9\u88c5", sales: 6 }
    ]
  }),
  adminAuditLogs: async () => adminAuditLogRows.map((item) => ({ ...item }))

};
