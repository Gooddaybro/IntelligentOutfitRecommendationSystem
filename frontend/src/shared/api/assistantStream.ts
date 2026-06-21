import { getAccessToken } from "./client";
import type { AssistantChatRequest, RecommendedItem } from "./types";

export type AssistantStreamEvent =
  | { type: "thread"; threadId: string }
  | { type: "token"; text: string }
  | { type: "recommendation"; spuIds: number[]; recommendedItems?: RecommendedItem[] }
  | { type: "done"; threadId?: string; answer?: string; spuIds: number[]; recommendedItems?: RecommendedItem[] }
  | { type: "error"; message: string };

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

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
    normalized.push(normalizedItem);
  });

  return normalized;
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

  if (eventName === "error") {
    return { type: "error", message: data || "AI 流式响应失败" };
  }

  let payload: unknown = data;
  try {
    payload = data ? JSON.parse(data) : data;
  } catch {
    payload = data;
  }

  if (eventName === "thread" || eventName === "meta") {
    const threadId =
      (payload as { threadId?: string; thread_id?: string }).threadId ??
      (payload as { thread_id?: string }).thread_id ??
      payload;
    return { type: "thread", threadId: String(threadId) };
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
      answer?: string;
      recommendedSpuIds?: number[];
      recommended_spu_ids?: number[];
    };
    const recommendedItems = normalizeRecommendedItems(donePayload);
    const ids = donePayload.recommendedSpuIds ?? donePayload.recommended_spu_ids ?? recommendedItems.map((item) => item.spuId);
    return {
      type: "done",
      threadId: donePayload.threadId ?? donePayload.thread_id,
      answer: donePayload.answer,
      spuIds: Array.isArray(ids) ? ids.map(Number) : [],
      recommendedItems
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
  signal?: AbortSignal
): Promise<void> {
  const headers = new Headers({ "Content-Type": "application/json" });
  const token = getAccessToken();

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}/api/assistant/chat/stream`, {
    method: "POST",
    headers,
    body: JSON.stringify(request),
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
