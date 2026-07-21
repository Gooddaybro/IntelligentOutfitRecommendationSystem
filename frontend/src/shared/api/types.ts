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

export type UserProfileRequest = {
  nickname?: string | null;
  avatarUrl?: string | null;
  gender?: string | null;
  birthday?: string | null;
};

export type UserProfileResponse = UserProfileRequest & {
  userId: number;
};

export type UserBodyDataRequest = {
  heightCm?: number | null;
  weightKg?: number | null;
  gender?: string | null;
  shoulderWidthCm?: number | null;
  bustCm?: number | null;
  waistCm?: number | null;
  hipCm?: number | null;
  preferredFit?: string | null;
};

export type UserBodyDataResponse = UserBodyDataRequest & {
  userId: number;
};

export type BodyMeasurementsPatchRequest = {
  heightCm?: number | null;
  weightKg?: number | null;
};

export type RecommendationStatus =
  | "STRONG_MATCH"
  | "PARTIAL_MATCH"
  | "BROWSE_FALLBACK"
  | "EMPTY"
  | "FAILED";
export type OutfitRole = "TOP" | "BOTTOM" | "OUTER" | "SHOES" | "ACCESSORY" | "OTHER";

export type UserPreferencesRequest = {
  preferredStyles: string[];
  preferredColors: string[];
  dislikedColors: string[];
  preferredCategories: string[];
  budgetMin?: number | null;
  budgetMax?: number | null;
};

export type UserPreferencesResponse = UserPreferencesRequest & {
  userId: number;
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
  outfitRole?: OutfitRole;
};

export type RecommendedItem = {
  spuId: number;
  skuId?: number;
  reason?: string;
  rankScore?: number;
  matchedDimensions?: Array<{
    dimension: string;
    requestedValue: string;
    candidateValue: string;
    evidenceSource: string;
  }>;
  outfitRole?: OutfitRole;
};

export type SubjectMeasurements = {
  heightCm?: number;
  weightKg?: number;
  originalText?: string;
  normalizedFrom?: string;
  subject?: "SELF" | "OTHER" | "UNKNOWN";
  scope?: string;
  source?: string;
};

export type DemandIntent = {
  version?: string;
  source?: string;
  rawQuery?: string;
  requestType?: string | null;
  requestedCapabilities?: string[];
  targetGender?: string | null;
  category?: string | null;
  season?: string | null;
  scene?: string[];
  style?: string[];
  fitPreferences?: string[];
  budgetMax?: number | null;
  attributes?: string[];
  subjectMeasurements?: SubjectMeasurements | null;
  hardFilters?: string[];
  softPreferences?: string[];
  confidence?: number;
  missingSlots?: string[];
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

export type Address = {
  id: number;
  recipientName: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
  isDefault?: boolean;
};

export type CheckoutPreview = {
  items: CartItem[];
  merchandiseAmount: number;
  shippingAmount: number;
  discountAmount: number;
  payableAmount: number;
  invalidReasons: string[];
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
  address?: Address;
  shipment?: { carrier: string; trackingNo: string; latestEvent: string };
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
  gender?: "male" | "female";
  budgetMax?: number;
};

export type AssistantChatResponse = {
  threadId: string;
  answer: string;
  recommendedSpuIds: number[];
  recommendedItems?: RecommendedItem[];
  candidatesCount: number;
  resolvedIntent?: DemandIntent;
  recommendationId?: string;
  recommendationStatus?: RecommendationStatus;
};

export type BehaviorEventType =
  | "RECOMMENDATION_EXPOSED"
  | "RECOMMENDATION_CLICKED"
  | "RECOMMENDATION_CART_ADD"
  | "RECOMMENDATION_FAVORITE_ADD";

export type BehaviorEventRequest = {
  eventId: string;
  eventType: BehaviorEventType;
  spuId: number;
  skuId?: number;
  threadId?: string;
  quantity?: number;
  metadata?: Record<string, unknown>;
  recommendationId?: string;
};

export type BehaviorEventResponse = {
  eventId: string;
};

export type ConversationMessage = {
  role: "USER" | "ASSISTANT" | string;
  content: string;
  messageStatus: string;
  requestId?: string;
  createdAt: string;
};
