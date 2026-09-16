import { getAccessToken } from "./client";
import type { AgentMode, AssistantChatRequest, DemandIntent, MentionedItem, RecommendationStatus, RecommendedItem } from "./types";

export type AssistantStreamEvent =
  | { type: "thread"; threadId: string; requestId?: string; runId?: string; agentMode?: AgentMode }
  | { type: "progress"; runId: string; sequence: number; tool: string; stage: "started" | "completed"; message: string }
  | { type: "token"; text: string }
  | { type: "recommendation"; spuIds: number[]; recommendedItems?: RecommendedItem[] }
  | {
      type: "done";
      threadId?: string;
      requestId?: string;
      runId?: string;
      agentMode?: AgentMode;
      answer?: string;
      spuIds: number[];
      recommendedItems?: RecommendedItem[];
      mentionedItems?: MentionedItem[];
      resolvedIntent?: DemandIntent;
      recommendationId?: string;
      recommendationStatus?: RecommendationStatus;
      requirements?: unknown[];
    }
  | { type: "error"; message: string; code?: string; runId?: string };

export type AssistantProgressEvent = Extract<AssistantStreamEvent, { type: "progress" }>;

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

function normalizeLegacyRecommendationStatus(status: unknown): RecommendationStatus | undefined {
  if (status === "WEAK_FALLBACK") return "BROWSE_FALLBACK";
  if (status === "ERROR") return "FAILED";
  if (status === "STRONG_MATCH" || status === "PARTIAL_MATCH" || status === "BROWSE_FALLBACK"
      || status === "EMPTY" || status === "FAILED") {
    return status;
  }
  return undefined;
}

function normalizeRecommendedItems(payload: unknown): RecommendedItem[] {
  const source =
    (payload as { recommendedItems?: unknown }).recommendedItems ??
    (payload as { recommended_items?: unknown }).recommended_items ??
    [];

  if (!Array.isArray(source)) {
    return [];
  }

  const normalized: RecommendedItem[] = [];

  source.forEach((item) => {
    const raw = item as {
      spuId?: number | string;
      spu_id?: number | string;
      skuId?: number | string;
      sku_id?: number | string;
      reason?: string;
      rankScore?: number | string;
      rank_score?: number | string;
      name?: string;
      salePrice?: number | string;
      sale_price?: number | string;
      mainImageUrl?: string;
      main_image_url?: string;
      color?: string;
      size?: string;
      availableStock?: number | string;
      available_stock?: number | string;
      sizeAdvice?: string;
      size_advice?: string;
      basis?: string;
      matchedDimensions?: RecommendedItem["matchedDimensions"];
      matched_dimensions?: Array<{ dimension: string; requested_value: string; candidate_value: string; evidence_source: string }>;
      outfitRole?: RecommendedItem["outfitRole"];
      outfit_role?: RecommendedItem["outfitRole"];
    };
    const spuId = raw.spuId ?? raw.spu_id;
    const skuId = raw.skuId ?? raw.sku_id;
    const rankScore = raw.rankScore ?? raw.rank_score;

    if (spuId === undefined || spuId === null) {
      return;
    }

    const normalizedItem: RecommendedItem = {
      spuId: Number(spuId)
    };
    if (skuId !== undefined && skuId !== null) {
      normalizedItem.skuId = Number(skuId);
    }
    if (raw.reason !== undefined) {
      normalizedItem.reason = raw.reason;
    }
    if (rankScore !== undefined && rankScore !== null) {
      normalizedItem.rankScore = Number(rankScore);
    }
    const name = raw.name;
    const salePrice = raw.salePrice ?? raw.sale_price;
    const availableStock = raw.availableStock ?? raw.available_stock;
    const mainImageUrl = raw.mainImageUrl ?? raw.main_image_url;
    const sizeAdvice = raw.sizeAdvice ?? raw.size_advice;
    if (name !== undefined) normalizedItem.name = name;
    if (salePrice !== undefined && salePrice !== null) normalizedItem.salePrice = Number(salePrice);
    if (mainImageUrl !== undefined) normalizedItem.mainImageUrl = mainImageUrl;
    if (raw.color !== undefined) normalizedItem.color = raw.color;
    if (raw.size !== undefined) normalizedItem.size = raw.size;
    if (availableStock !== undefined && availableStock !== null) normalizedItem.availableStock = Number(availableStock);
    if (sizeAdvice !== undefined) normalizedItem.sizeAdvice = sizeAdvice;
    if (raw.basis !== undefined) normalizedItem.basis = raw.basis;
    normalizedItem.outfitRole = raw.outfitRole ?? raw.outfit_role;
    normalizedItem.matchedDimensions = raw.matchedDimensions ?? raw.matched_dimensions?.map((item) => ({
      dimension: item.dimension,
      requestedValue: item.requested_value,
      candidateValue: item.candidate_value,
      evidenceSource: item.evidence_source
    }));
    normalized.push(normalizedItem);
  });

  return normalized;
}

/** 兼容同步与 SSE 命名差异，但不从文本或缺失的 SKU 推断商品身份。 */
function normalizeMentionedItems(payload: unknown): MentionedItem[] | undefined {
  const record = payload as Record<string, unknown>;
  const hasCamelCaseField = Object.prototype.hasOwnProperty.call(record, "mentionedItems");
  const hasSnakeCaseField = Object.prototype.hasOwnProperty.call(record, "mentioned_items");
  if (!hasCamelCaseField && !hasSnakeCaseField) {
    return undefined;
  }
  const source = hasCamelCaseField ? record.mentionedItems : record.mentioned_items;

  if (!Array.isArray(source)) {
    return [];
  }

  return source.flatMap((item) => {
    const raw = item as {
      spuId?: number | string;
      spu_id?: number | string;
      skuId?: number | string;
      sku_id?: number | string;
      outfitRole?: MentionedItem["outfitRole"];
      outfit_role?: MentionedItem["outfitRole"];
    };
    const spuId = raw.spuId ?? raw.spu_id;
    const skuId = raw.skuId ?? raw.sku_id;
    if (spuId === undefined || spuId === null || skuId === undefined || skuId === null) {
      return [];
    }
    return [{
      spuId: Number(spuId),
      skuId: Number(skuId),
      outfitRole: raw.outfitRole ?? raw.outfit_role
    }];
  });
}

export function parseSseEventBlock(block: string): AssistantStreamEvent | null {
  const lines = block.split("\n");
  const eventName = lines
    .find((line) => line.startsWith("event:"))
    ?.slice("event:".length)
    .trim();

  const data = lines
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice("data:".length).trim())
    .join("\n");

  if (!eventName && !data) {
    return null;
  }

  let payload: unknown = data;
  try {
    payload = data ? JSON.parse(data) : data;
  } catch {
    payload = data;
  }

  if (eventName === "error") {
    const errorPayload = payload && typeof payload === "object" ? payload as {
      code?: string;
      message?: string;
      run_id?: string;
      runId?: string;
    } : undefined;
    return {
      type: "error",
      message: errorPayload?.message ?? (typeof payload === "string" ? payload : "AI 流式响应失败"),
      ...(errorPayload?.code ? { code: errorPayload.code } : {}),
      ...((errorPayload?.runId ?? errorPayload?.run_id) ? { runId: errorPayload?.runId ?? errorPayload?.run_id } : {})
    };
  }

  if (eventName === "thread" || eventName === "meta") {
    const meta = payload && typeof payload === "object" ? payload as {
      threadId?: string;
      thread_id?: string;
      requestId?: string;
      request_id?: string;
      runId?: string;
      run_id?: string;
      agentMode?: AgentMode;
      agent_mode?: AgentMode;
    } : undefined;
    const threadId = meta?.threadId ?? meta?.thread_id ?? payload;
    const requestId = meta?.requestId ?? meta?.request_id;
    const runId = meta?.runId ?? meta?.run_id;
    const agentMode = meta?.agentMode ?? meta?.agent_mode;
    const isVersionedMeta = agentMode === "pro" || Boolean(runId);
    return {
      type: "thread",
      threadId: String(threadId),
      ...(isVersionedMeta && requestId ? { requestId } : {}),
      ...(isVersionedMeta && runId ? { runId } : {}),
      ...(agentMode === "lite" || agentMode === "pro" ? { agentMode } : {})
    };
  }

  if (eventName === "progress") {
    const progress = payload && typeof payload === "object" ? payload as {
      runId?: string;
      run_id?: string;
      sequence?: number | string;
      tool?: string;
      stage?: string;
      message?: string;
    } : undefined;
    const runId = progress?.runId ?? progress?.run_id;
    const sequence = progress?.sequence === undefined ? NaN : Number(progress.sequence);
    if (!runId || !Number.isInteger(sequence) || sequence < 0
        || !progress?.tool || (progress.stage !== "started" && progress.stage !== "completed")
        || !progress.message) {
      return null;
    }
    return {
      type: "progress",
      runId,
      sequence,
      tool: progress.tool,
      stage: progress.stage,
      message: progress.message
    };
  }

  if (eventName === "recommendation") {
    const ids = Array.isArray(payload)
      ? payload
      : (payload as { recommendedSpuIds?: number[]; recommended_spu_ids?: number[] }).recommendedSpuIds ??
        (payload as { recommended_spu_ids?: number[] }).recommended_spu_ids;
    const recommendedItems = normalizeRecommendedItems(payload);
    return {
      type: "recommendation",
      spuIds: Array.isArray(ids) ? ids.map(Number) : recommendedItems.map((item) => item.spuId),
      recommendedItems
    };
  }

  if (eventName === "done") {
    const donePayload = payload as {
      threadId?: string;
      thread_id?: string;
      requestId?: string;
      request_id?: string;
      runId?: string;
      run_id?: string;
      agentMode?: AgentMode;
      agent_mode?: AgentMode;
      answer?: string;
      recommendedSpuIds?: number[];
      recommended_spu_ids?: number[];
      resolvedIntent?: DemandIntent;
      resolved_intent?: DemandIntent;
      recommendationId?: string;
      recommendation_id?: string;
      recommendationStatus?: unknown;
      recommendation_status?: unknown;
      requirements?: unknown[];
    };
    const recommendedItems = normalizeRecommendedItems(donePayload);
    const mentionedItems = normalizeMentionedItems(donePayload);
    const ids = donePayload.recommendedSpuIds ?? donePayload.recommended_spu_ids ?? recommendedItems.map((item) => item.spuId);
    return {
      type: "done",
      threadId: donePayload.threadId ?? donePayload.thread_id,
      requestId: donePayload.requestId ?? donePayload.request_id,
      runId: donePayload.runId ?? donePayload.run_id,
      agentMode: donePayload.agentMode ?? donePayload.agent_mode,
      answer: donePayload.answer,
      spuIds: Array.isArray(ids) ? ids.map(Number) : [],
      recommendedItems,
      mentionedItems,
      resolvedIntent: donePayload.resolvedIntent ?? donePayload.resolved_intent,
      recommendationId: donePayload.recommendationId ?? donePayload.recommendation_id,
      recommendationStatus: normalizeLegacyRecommendationStatus(
        donePayload.recommendationStatus ?? donePayload.recommendation_status
      ),
      requirements: donePayload.requirements
    };
  }

  const text =
    typeof payload === "string"
      ? payload
      : String(
          (payload as { content?: string; text?: string; token?: string }).content ??
            (payload as { text?: string }).text ??
            (payload as { token?: string }).token ??
            ""
        );
  return { type: "token", text };
}

export async function streamAssistantChat(
  request: AssistantChatRequest,
  onEvent: (event: AssistantStreamEvent) => void,
  signal?: AbortSignal,
  mode?: AgentMode
): Promise<void> {
  const headers = new Headers({ "Content-Type": "application/json" });
  const token = getAccessToken();

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const effectiveMode = mode ?? request.agentMode ?? "lite";
  const { agentMode: _requestMode, ...legacyRequest } = request;
  const body = effectiveMode === "pro"
    ? JSON.stringify({ ...legacyRequest, agentMode: "pro" })
    : JSON.stringify(legacyRequest);
  const response = await fetch(`${API_BASE_URL}${chatStreamPath(effectiveMode)}`, {
    method: "POST",
    headers,
    body,
    signal
  });

  if (!response.ok || !response.body) {
    throw new Error(`AI 流式请求失败：${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
    const blocks = buffer.split(/\n\n/);
    buffer = blocks.pop() ?? "";

    blocks.map(parseSseEventBlock).filter(Boolean).forEach((event) => onEvent(event as AssistantStreamEvent));

    if (done) {
      break;
    }
  }

  const finalEvent = parseSseEventBlock(buffer);
  if (finalEvent) {
    onEvent(finalEvent);
  }
}

export function chatStreamPath(mode: AgentMode): string {
  return mode === "pro" ? "/api/assistant/v2/chat/stream" : "/api/assistant/chat/stream";
}
