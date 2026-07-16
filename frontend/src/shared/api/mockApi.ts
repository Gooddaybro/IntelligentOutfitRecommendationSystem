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

const catalog: RecommendationCandidate[] = [
  { spuId: 1001, skuId: 2001, spuCode: "PUFFER_COMMUTE", skuCode: "PUFFER-IVORY-M", name: "轻量通勤羽绒服", categoryName: "外套", color: "米白", size: "M", fitType: "合身", salePrice: 699, availableStock: 8, mainImageUrl: "/images/products/puffer-winter-light-main.jpg", styleTags: "通勤,简约" },
  { spuId: 1001, skuId: 2002, spuCode: "PUFFER_COMMUTE", skuCode: "PUFFER-IVORY-L", name: "轻量通勤羽绒服", categoryName: "外套", color: "米白", size: "L", fitType: "合身", salePrice: 699, availableStock: 3, mainImageUrl: "/images/products/puffer-winter-light-main.jpg", styleTags: "通勤,简约" },
  { spuId: 1002, skuId: 2011, spuCode: "TRENCH_COMMUTE", skuCode: "TRENCH-KHAKI-M", name: "浅卡其通勤风衣", categoryName: "外套", color: "浅卡其", size: "M", fitType: "宽松", salePrice: 599, availableStock: 12, mainImageUrl: "/images/products/jacket-commute-trench-main.jpg", styleTags: "通勤,自然" },
  { spuId: 1003, skuId: 2021, spuCode: "SHIRT_OXFORD", skuCode: "SHIRT-BLUE-M", name: "牛津纺通勤衬衫", categoryName: "上装", color: "雾蓝", size: "M", fitType: "合身", salePrice: 269, availableStock: 18, mainImageUrl: "/images/products/oxford-shirt-commute-main.jpg", styleTags: "通勤,基础" },
  { spuId: 1004, skuId: 2031, spuCode: "PANTS_TAPERED", skuCode: "PANTS-BLACK-M", name: "锥形通勤长裤", categoryName: "裤装", color: "炭黑", size: "M", fitType: "锥形", salePrice: 329, availableStock: 7, mainImageUrl: "/images/products/chino-commute-tapered-main.jpg", styleTags: "通勤,利落" },
  { spuId: 1005, skuId: 2041, spuCode: "KNIT_CARDIGAN", skuCode: "KNIT-OAT-M", name: "燕麦色针织开衫", categoryName: "上装", color: "燕麦", size: "M", fitType: "宽松", salePrice: 399, availableStock: 5, mainImageUrl: "/images/products/knit-minimal-cardigan-main.jpg", styleTags: "自然,简约" },
  { spuId: 1006, skuId: 2051, spuCode: "SKIRT_PLEATED", skuCode: "SKIRT-GRAY-M", name: "通勤百褶半身裙", categoryName: "裙装", color: "烟灰", size: "M", fitType: "A 字", salePrice: 359, availableStock: 9, mainImageUrl: "/images/products/skirt-commute-pleated-main.jpg", styleTags: "通勤,优雅" }
];

let cartItems: CartItem[] = [];
let orders: OrderResponse[] = [];
let addressBook: Address[] = [{ id: 1, recipientName: "林木", phone: "138****2026", province: "浙江省", city: "杭州市", district: "西湖区", detail: "文一路 88 号", isDefault: true }];
let profile: UserProfileResponse = { userId: 1, nickname: "林木", avatarUrl: null, gender: null, birthday: null };
let bodyData: UserBodyDataResponse = { userId: 1 };
let preferences: UserPreferencesResponse = { userId: 1, preferredStyles: ["自然", "通勤"], preferredColors: ["米白", "鼠尾草绿"], dislikedColors: [], preferredCategories: ["外套"], budgetMin: 200, budgetMax: 800 };

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
  pay: async (orderNo: string, channel = "MOCK") => ({ paymentNo: `PAY-${orderNo}`, orderNo, amount: orders.find((item) => item.orderNo === orderNo)?.totalAmount || 0, channel, status: "PENDING" }),
  payment: async (paymentNo: string) => ({ paymentNo, orderNo: paymentNo.replace("PAY-", ""), amount: 0, channel: "MOCK", status: "SUCCESS", paidAt: new Date().toISOString() }),
  payMock: async (orderNo: string) => {
    const paidAt = new Date().toISOString();
    orders = orders.map((item) => item.orderNo === orderNo ? { ...item, status: "PAID", paidAt } : item);
    return { paymentNo: `PAY-${orderNo}`, orderNo, amount: orders.find((item) => item.orderNo === orderNo)?.totalAmount || 0, channel: "MOCK", status: "SUCCESS", paidAt };
  }
};
