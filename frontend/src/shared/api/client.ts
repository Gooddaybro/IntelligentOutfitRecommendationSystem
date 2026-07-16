import type {
  Address,
  ApiResponse,
  AssistantChatRequest,
  AssistantChatResponse,
  AuthTokenResponse,
  BehaviorEventRequest,
  BehaviorEventResponse,
  CartItem,
  CheckoutPreview,
  CurrentUserResponse,
  OrderResponse,
  PaymentResponse,
  ProductDetail,
  ProductSearchItem,
  RecommendationCandidate,
  UserBodyDataRequest,
  UserBodyDataResponse,
  UserPreferencesRequest,
  UserPreferencesResponse,
  UserProfileRequest,
  UserProfileResponse
} from "./types";
import { mockApi } from "./mockApi";
import type {
  AdminAnalytics,
  AdminAuditLog,
  AdminCategory,
  AdminOrder,
  AdminOverview,
  AdminProduct,
  AdminProductInput,
  AdminSku,
  AdminUser,
  AdminUserStatus
} from "./adminTypes";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
const TOKEN_STORAGE_KEY = "ior.accessToken";
const REFRESH_TOKEN_STORAGE_KEY = "ior.refreshToken";

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setAuthTokens(tokens: AuthTokenResponse): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, tokens.refreshToken);
}

export function clearAuthTokens(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
}

export async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getAccessToken();

  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json");
  }

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers
  });

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const message = payload?.message ?? `请求失败：${response.status}`;
    throw new Error(message);
  }

  return (payload?.data ?? payload) as T;
}

const httpApi = {
  login: (username: string, password: string) =>
    requestJson<AuthTokenResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password })
    }),
  register: (username: string, password: string, email?: string) =>
    requestJson<{ userId: number; username: string; status: string }>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password, email })
    }),
  me: () => requestJson<CurrentUserResponse>("/api/users/me"),
  profile: () => requestJson<UserProfileResponse>("/api/me/profile"),
  updateProfile: (request: UserProfileRequest) =>
    requestJson<UserProfileResponse>("/api/me/profile", {
      method: "PUT",
      body: JSON.stringify(request)
    }),
  bodyData: () => requestJson<UserBodyDataResponse>("/api/me/body-data"),
  updateBodyData: (request: UserBodyDataRequest) =>
    requestJson<UserBodyDataResponse>("/api/me/body-data", {
      method: "PUT",
      body: JSON.stringify(request)
    }),
  preferences: () => requestJson<UserPreferencesResponse>("/api/me/preferences"),
  updatePreferences: (request: UserPreferencesRequest) =>
    requestJson<UserPreferencesResponse>("/api/me/preferences", {
      method: "PUT",
      body: JSON.stringify(request)
    }),
  searchProducts: (keyword: string) => {
    const params = new URLSearchParams();
    if (keyword.trim()) {
      params.set("keyword", keyword.trim());
    }
    return requestJson<ProductSearchItem[]>(`/api/products?${params.toString()}`);
  },
  recommendationCandidates: (params: Partial<AssistantChatRequest>) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== "") {
        query.set(key, String(value));
      }
    });
    return requestJson<RecommendationCandidate[]>(`/api/products/recommendation-candidates?${query.toString()}`);
  },
  productDetail: (spuId: number) => requestJson<ProductDetail>(`/api/products/${spuId}`),
  chat: (request: AssistantChatRequest) =>
    requestJson<AssistantChatResponse>("/api/assistant/chat", {
      method: "POST",
      body: JSON.stringify(request)
    }),
  recordBehaviorEvent: (request: BehaviorEventRequest) =>
    requestJson<BehaviorEventResponse>("/api/behavior/events", {
      method: "POST",
      body: JSON.stringify(request)
    }),
  cart: () => requestJson<CartItem[]>("/api/cart/items"),
  addCartItem: (skuId: number, quantity: number, recommendationId?: string) =>
    requestJson<CartItem[]>("/api/cart/items", {
      method: "POST",
      body: JSON.stringify({ skuId, quantity, recommendationId })
    }),
  updateCartItem: (skuId: number, quantity: number) =>
    requestJson<CartItem[]>(`/api/cart/items/${skuId}`, {
      method: "PUT",
      body: JSON.stringify({ quantity })
    }),
  removeCartItem: (skuId: number) =>
    requestJson<CartItem[]>(`/api/cart/items/${skuId}`, {
      method: "DELETE"
    }),
  addresses: () => requestJson<Address[]>("/api/addresses"),
  saveAddress: (address: Omit<Address, "id"> & { id?: number }) => requestJson<Address[]>(address.id ? `/api/addresses/${address.id}` : "/api/addresses", { method: address.id ? "PUT" : "POST", body: JSON.stringify(address) }),
  removeAddress: (id: number) => requestJson<Address[]>(`/api/addresses/${id}`, { method: "DELETE" }),
  favorites: () => requestJson<RecommendationCandidate[]>("/api/favorites"),
  addFavorite: (spuId: number) => requestJson<RecommendationCandidate[]>("/api/favorites", { method: "POST", body: JSON.stringify({ spuId }) }),
  removeFavorite: (spuId: number) => requestJson<RecommendationCandidate[]>(`/api/favorites/${spuId}`, { method: "DELETE" }),
  checkoutPreview: (skuIds: number[], addressId?: number) => requestJson<CheckoutPreview>("/api/checkout/preview", { method: "POST", body: JSON.stringify({ skuIds, addressId }) }),
  createOrder: (skuIds: number[], addressId?: number) =>
    requestJson<OrderResponse>("/api/orders", {
      method: "POST",
      body: JSON.stringify({ source: "CART", skuIds, addressId })
    }),
  buyNow: (skuId: number, quantity: number, recommendationId?: string) =>
    requestJson<OrderResponse>("/api/orders/buy-now", {
      method: "POST",
      body: JSON.stringify({ skuId, quantity, recommendationId })
    }),
  orders: () => requestJson<OrderResponse[]>("/api/orders"),
  order: (orderNo: string) => requestJson<OrderResponse>(`/api/orders/${orderNo}`),
  cancelOrder: (orderNo: string) => requestJson<OrderResponse>(`/api/orders/${orderNo}/cancel`, { method: "POST" }),
  confirmReceipt: (orderNo: string) => requestJson<OrderResponse>(`/api/orders/${orderNo}/confirm-receipt`, { method: "POST" }),
  pay: (orderNo: string, channel: "MOCK" | "ALIPAY" | "WECHAT" = "MOCK") =>
    requestJson<PaymentResponse>("/api/payments", {
      method: "POST",
      body: JSON.stringify({ orderNo, channel })
    }),
  payment: (paymentNo: string) => requestJson<PaymentResponse>(`/api/payments/${paymentNo}`),
  payMock: (orderNo: string) =>
    requestJson<PaymentResponse>("/api/payments/mock-pay", {
      method: "POST",
      body: JSON.stringify({ orderNo })
    }),
  adminOverview: () => requestJson<AdminOverview>("/api/admin/overview"),
  adminProducts: () => requestJson<AdminProduct[]>("/api/admin/products"),
  adminSaveProduct: (input: AdminProductInput) => requestJson<AdminProduct>(input.spuId ? `/api/admin/products/${input.spuId}` : "/api/admin/products", {
    method: input.spuId ? "PUT" : "POST",
    body: JSON.stringify(input)
  }),
  adminSetProductStatus: (spuId: number, status: AdminProduct["status"]) => requestJson<AdminProduct>(`/api/admin/products/${spuId}/status`, {
    method: "POST",
    body: JSON.stringify({ status })
  }),
  adminCategories: () => requestJson<AdminCategory[]>("/api/admin/categories"),
  adminUpdateCategory: (category: AdminCategory) => requestJson<AdminCategory>(`/api/admin/categories/${category.id}`, {
    method: "PUT",
    body: JSON.stringify(category)
  }),
  adminInventory: () => requestJson<AdminSku[]>("/api/admin/inventory"),
  adminAdjustInventory: (skuId: number, targetStock: number, reason: string) => requestJson<AdminSku>(`/api/admin/inventory/${skuId}/adjustments`, {
    method: "POST",
    body: JSON.stringify({ targetStock, reason })
  }),
  adminOrders: () => requestJson<AdminOrder[]>("/api/admin/orders"),
  adminShipOrder: (orderNo: string, carrier: string, trackingNo: string) => requestJson<AdminOrder>(`/api/admin/orders/${orderNo}/ship`, {
    method: "POST",
    body: JSON.stringify({ carrier, trackingNo })
  }),
  adminUsers: () => requestJson<AdminUser[]>("/api/admin/users"),
  adminSetUserStatus: (userId: number, status: AdminUserStatus) => requestJson<AdminUser>(`/api/admin/users/${userId}/status`, {
    method: "POST",
    body: JSON.stringify({ status })
  }),
  adminAnalytics: () => requestJson<AdminAnalytics>("/api/admin/analytics"),
  adminAuditLogs: () => requestJson<AdminAuditLog[]>("/api/admin/audit-logs")
};

export const IS_MOCK_MODE = import.meta.env.VITE_DATA_MODE === "mock";
export const api = IS_MOCK_MODE ? { ...httpApi, ...mockApi } : httpApi;

export type { ApiResponse };
