import { Send, SlidersHorizontal, Square } from "lucide-react";
import { FormEvent, useMemo, useRef, useState } from "react";
import { api } from "../../shared/api/client";
import { streamAssistantChat } from "../../shared/api/assistantStream";
import type { AssistantChatRequest, RecommendationCandidate } from "../../shared/api/types";

type ChatMessage = {
  role: "user" | "assistant";
  content: string;
};

type ChatPanelProps = {
  onRecommendations: (items: RecommendationCandidate[]) => void;
};

export function ChatPanel({ onRecommendations }: ChatPanelProps) {
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: "assistant", content: "告诉我你的场景、风格、预算或身材偏好，我会先从 Java 商品库筛选，再给你推荐。" }
  ]);
  const [draft, setDraft] = useState("");
  const [filters, setFilters] = useState({ category: "", style: "", season: "", budgetMax: "" });
  const [threadId, setThreadId] = useState<string | undefined>();
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState("");
  const abortRef = useRef<AbortController | null>(null);

  const requestFilters = useMemo<Partial<AssistantChatRequest>>(
    () => ({
      category: filters.category || undefined,
      style: filters.style || undefined,
      season: filters.season || undefined,
      budgetMax: filters.budgetMax ? Number(filters.budgetMax) : undefined
    }),
    [filters]
  );

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
            const candidates = await api.recommendationCandidates(requestFilters);
            const filtered = event.spuIds.length
              ? candidates.filter((candidate) => event.spuIds.includes(candidate.spuId))
              : candidates;
            onRecommendations(filtered);
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
            const candidates = await api.recommendationCandidates(requestFilters);
            onRecommendations(
              event.spuIds.length
                ? candidates.filter((candidate) => event.spuIds.includes(candidate.spuId))
                : candidates
            );
          }
          if (event.type === "error") {
            setError(event.message);
          }
        },
        abortRef.current.signal
      );
    } catch (streamError) {
      setError(streamError instanceof Error ? streamError.message : "AI 响应失败");
      const fallback = await api.chat({ ...requestFilters, threadId, message });
      setThreadId(fallback.threadId);
      setMessages((current) => {
        const next = [...current];
        next[next.length - 1] = { role: "assistant", content: fallback.answer };
        return next;
      });
      const candidates = await api.recommendationCandidates(requestFilters);
      onRecommendations(
        fallback.recommendedSpuIds.length
          ? candidates.filter((candidate) => fallback.recommendedSpuIds.includes(candidate.spuId))
          : candidates
      );
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
