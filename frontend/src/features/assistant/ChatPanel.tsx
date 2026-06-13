import { Send, SlidersHorizontal, Square } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { api } from "../../shared/api/client";
import { streamAssistantChat } from "../../shared/api/assistantStream";
import type { AssistantChatRequest, RecommendationCandidate } from "../../shared/api/types";
import type { Dispatch, FormEvent, MutableRefObject, SetStateAction } from "react";

export type ChatMessage = {
  role: "user" | "assistant";
  content: string;
};

export type ChatFilters = {
  category: string;
  style: string;
  season: string;
  budgetMax: string;
};

export type ChatPanelState = {
  messages: ChatMessage[];
  setMessages: Dispatch<SetStateAction<ChatMessage[]>>;
  draft: string;
  setDraft: Dispatch<SetStateAction<string>>;
  filters: ChatFilters;
  setFilters: Dispatch<SetStateAction<ChatFilters>>;
  threadId?: string;
  setThreadId: Dispatch<SetStateAction<string | undefined>>;
  isStreaming: boolean;
  setIsStreaming: Dispatch<SetStateAction<boolean>>;
  error: string;
  setError: Dispatch<SetStateAction<string>>;
  abortRef: MutableRefObject<AbortController | null>;
};

export type RecommendationResultMeta = {
  hasAiResult: boolean;
  hasStrongMatch: boolean;
};

export const initialChatMessages: ChatMessage[] = [
  { role: "assistant", content: "告诉我你的场景、风格、预算或身材偏好，我会先从 Java 商品库筛选，再给你推荐。" }
];

export const initialChatFilters: ChatFilters = { category: "", style: "", season: "", budgetMax: "" };

type ChatPanelProps = {
  onRecommendations: (items: RecommendationCandidate[], meta?: RecommendationResultMeta) => void;
  state?: ChatPanelState;
};

export function ChatPanel({ onRecommendations, state }: ChatPanelProps) {
  const [internalMessages, setInternalMessages] = useState<ChatMessage[]>(initialChatMessages);
  const [internalDraft, setInternalDraft] = useState("");
  const [internalFilters, setInternalFilters] = useState<ChatFilters>(initialChatFilters);
  const [internalThreadId, setInternalThreadId] = useState<string | undefined>();
  const [internalIsStreaming, setInternalIsStreaming] = useState(false);
  const [internalError, setInternalError] = useState("");
  const internalAbortRef = useRef<AbortController | null>(null);

  const messages = state?.messages ?? internalMessages;
  const setMessages = state?.setMessages ?? setInternalMessages;
  const draft = state?.draft ?? internalDraft;
  const setDraft = state?.setDraft ?? setInternalDraft;
  const filters = state?.filters ?? internalFilters;
  const setFilters = state?.setFilters ?? setInternalFilters;
  const threadId = state?.threadId ?? internalThreadId;
  const setThreadId = state?.setThreadId ?? setInternalThreadId;
  const isStreaming = state?.isStreaming ?? internalIsStreaming;
  const setIsStreaming = state?.setIsStreaming ?? setInternalIsStreaming;
  const error = state?.error ?? internalError;
  const setError = state?.setError ?? setInternalError;
  const abortRef = state?.abortRef ?? internalAbortRef;

  const requestFilters = useMemo<Partial<AssistantChatRequest>>(
    () => ({
      category: filters.category || undefined,
      style: filters.style || undefined,
      season: filters.season || undefined,
      budgetMax: filters.budgetMax ? Number(filters.budgetMax) : undefined
    }),
    [filters]
  );

  function orderCandidatesByRecommendedSpuIds(candidates: RecommendationCandidate[], spuIds: number[]) {
    if (!spuIds.length) {
      return candidates;
    }

    const idOrder = new Map(spuIds.map((id, index) => [id, index]));
    return [...candidates].sort((first, second) => {
      const firstOrder = idOrder.get(first.spuId);
      const secondOrder = idOrder.get(second.spuId);

      if (firstOrder === undefined && secondOrder === undefined) {
        return 0;
      }

      if (firstOrder === undefined) {
        return 1;
      }

      if (secondOrder === undefined) {
        return -1;
      }

      return firstOrder - secondOrder;
    });
  }

  async function updateRecommendations(spuIds: number[]) {
    const candidates = await api.recommendationCandidates(requestFilters);
    onRecommendations(orderCandidatesByRecommendedSpuIds(candidates, spuIds), {
      hasAiResult: true,
      hasStrongMatch: spuIds.length > 0
    });
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const message = draft.trim();
    if (!message || isStreaming) {
      return;
    }

    setDraft("");
    setError("");
    setMessages((current) => [...current, { role: "user", content: message }, { role: "assistant", content: "" }]);
    setIsStreaming(true);
    abortRef.current = new AbortController();

    try {
      await streamAssistantChat(
        { ...requestFilters, threadId, message },
        async (event) => {
          if (event.type === "thread") {
            setThreadId(event.threadId);
          }
          if (event.type === "token") {
            setMessages((current) => {
              const next = [...current];
              const last = next[next.length - 1];
              next[next.length - 1] = { ...last, content: `${last.content}${event.text}` };
              return next;
            });
          }
          if (event.type === "recommendation") {
            await updateRecommendations(event.spuIds);
          }
          if (event.type === "done") {
            if (event.threadId) {
              setThreadId(event.threadId);
            }
            if (event.answer) {
              setMessages((current) => {
                const next = [...current];
                const last = next[next.length - 1];
                next[next.length - 1] = { ...last, content: last.content || event.answer || "" };
                return next;
              });
            }
            await updateRecommendations(event.spuIds);
          }
          if (event.type === "error") {
            setError(event.message);
          }
        },
        abortRef.current.signal
      );
    } catch (streamError) {
      if (streamError instanceof Error && streamError.name === "AbortError") {
        setMessages((current) => {
          const next = [...current];
          const last = next[next.length - 1];
          next[next.length - 1] = { ...last, content: last.content || "已停止生成。" };
          return next;
        });
        return;
      }

      setError(streamError instanceof Error ? streamError.message : "AI 响应失败");
      const fallback = await api.chat({ ...requestFilters, threadId, message });
      setThreadId(fallback.threadId);
      setMessages((current) => {
        const next = [...current];
        next[next.length - 1] = { role: "assistant", content: fallback.answer };
        return next;
      });
      await updateRecommendations(fallback.recommendedSpuIds);
    } finally {
      setIsStreaming(false);
      abortRef.current = null;
    }
  }

  return (
    <section className="chat-panel">
      <div className="filter-row">
        <label>
          <SlidersHorizontal size={16} />
          <input
            placeholder="分类"
            value={filters.category}
            onChange={(event) => setFilters((current) => ({ ...current, category: event.target.value }))}
          />
        </label>
        <input
          placeholder="风格"
          value={filters.style}
          onChange={(event) => setFilters((current) => ({ ...current, style: event.target.value }))}
        />
        <input
          placeholder="季节"
          value={filters.season}
          onChange={(event) => setFilters((current) => ({ ...current, season: event.target.value }))}
        />
        <input
          placeholder="预算上限"
          value={filters.budgetMax}
          onChange={(event) => setFilters((current) => ({ ...current, budgetMax: event.target.value }))}
          inputMode="numeric"
        />
      </div>
      <div className="message-list">
        {messages.map((message, index) => (
          <div key={`${message.role}-${index}`} className={`message ${message.role}`}>
            {message.content || "正在生成..."}
          </div>
        ))}
      </div>
      {error && <p className="error-text">{error}</p>}
      <form className="chat-input" onSubmit={submit}>
        <textarea value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="例如：明天面试，想要显瘦、预算 500 以内" />
        {isStreaming ? (
          <button type="button" onClick={() => abortRef.current?.abort()} title="停止生成">
            <Square size={18} />
          </button>
        ) : (
          <button className="primary-button" type="submit" title="发送">
            <Send size={18} />
          </button>
        )}
      </form>
    </section>
  );
}
