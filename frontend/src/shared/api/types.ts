export type ApiResponse<T> = {
  code?: number;
  message?: string;
  data: T;
};

export type AuthTokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
};

export type CurrentUserResponse = {
  userId: number;
  username: string;
  role?: string;
};

export type ProductSearchItem = {
  spuId: number;
  spuCode: string;
  name: string;
  categoryName: string;
  mainImageUrl?: string;
  fitType?: string;
  minPrice: number;
  maxPrice: number;
};

export type RecommendationCandidate = {
  spuId: number;
  skuId: number;
  skuCode?: string;
  spuCode: string;
  name: string;
  categoryName: string;
  mainImageUrl?: string;
  fitType?: string;
  color?: string;
  size?: string;
  materials?: string;
  seasons?: string;
  styleTags?: string;
  attributeTags?: string;
  salePrice: number;
  stockStatus?: string;
  minPrice?: number;
  maxPrice?: number;
  totalAvailableStock?: number;
  availableStock?: number;
  recommendationReason?: string;
  rankScore?: number;
};

export type RecommendedItem = {
  spuId: number;
  skuId?: number;
  reason?: string;
  rankScore?: number;
};

export type ProductDetail = ProductSearchItem & {
  description?: string;
  materials?: string[];
  seasons?: string[];
  styleTags?: string[];
  attributes?: Record<string, string>;
};

export type CartItem = {
  id: number;
  userId: number;
  skuId: number;
  spuId: number;
  skuCode: string;
  spuCode: string;
  name: string;
  categoryName: string;
  color?: string;
  size?: string;
  salePrice: number;
  stockStatus?: string;
  mainImageUrl?: string;
  quantity: number;
  availableStock?: number;
};

export type OrderItemResponse = {
  skuId: number;
  spuId: number;
  skuCode: string;
  spuCode: string;
  productName: string;
  categoryName: string;
  color?: string;
  size?: string;
  salePrice: number;
  quantity: number;
  lineAmount: number;
  mainImageUrl?: string;
};

export type OrderResponse = {
  orderNo: string;
  status: string;
  totalAmount: number;
  items: OrderItemResponse[];
  createdAt: string;
  paidAt?: string | null;
  closedAt?: string | null;
  closeReason?: string | null;
};

export type PaymentResponse = {
  paymentNo: string;
  orderNo: string;
  amount: number;
  channel: string;
  status: string;
  transactionId?: string;
  paidAt?: string;
};

export type AssistantChatRequest = {
  threadId?: string;
  message: string;
  category?: string;
  style?: string;
  season?: string;
  material?: string;
  fit?: string;
  budgetMax?: number;
};

export type AssistantChatResponse = {
  threadId: string;
  answer: string;
  recommendedSpuIds: number[];
  recommendedItems?: RecommendedItem[];
  candidatesCount: number;
};

export type ConversationMessage = {
  role: "USER" | "ASSISTANT" | string;
  content: string;
  messageStatus: string;
  requestId?: string;
  createdAt: string;
};
