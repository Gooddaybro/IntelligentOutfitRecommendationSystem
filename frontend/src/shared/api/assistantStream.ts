import { getAccessToken } from "./client";
import type { AssistantChatRequest } from "./types";

export type AssistantStreamEvent =
  | { type: "thread"; threadId: string }
  | { type: "token"; text: string }
  | { type: "recommendation"; spuIds: number[] }
  | { type: "done" }
  | { type: "error"; message: string };

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

export function parseSseEventBlock(block: string): AssistantStreamEvent | null {
  const eventName = block
    .split("\n")
    .find((line) => line.startsWith("event:"))
    ?.slice("event:".length)
    .trim();

  const data = block
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice("data:".length).trim())
    .join("\n");

  if (!eventName && !data) {
    return null;
  }

  if (eventName === "done") {
    return { type: "done" };
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

  if (eventName === "thread") {
    return { type: "thread", threadId: String((payload as { threadId?: string }).threadId ?? payload) };
  }

  if (eventName === "recommendation") {
    const ids = Array.isArray(payload) ? payload : (payload as { recommendedSpuIds?: number[] }).recommendedSpuIds;
    return { type: "recommendation", spuIds: Array.isArray(ids) ? ids.map(Number) : [] };
  }

  const text = typeof payload === "string" ? payload : String((payload as { text?: string; token?: string }).text ?? (payload as { token?: string }).token ?? "");
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
