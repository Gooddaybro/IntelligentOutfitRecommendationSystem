import { Send, SlidersHorizontal, Square } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { api } from "../../shared/api/client";
import { streamAssistantChat } from "../../shared/api/assistantStream";
import type { AssistantChatRequest, DemandIntent, RecommendationCandidate, RecommendationStatus, RecommendedItem } from "../../shared/api/types";
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
  recommendedItems?: RecommendedItem[];
  recommendationId?: string;
  recommendationStatus: RecommendationStatus;
  resolvedIntent?: DemandIntent;
};

export const initialChatMessages: ChatMessage[] = [
  { role: "assistant", content: "告诉我你的场景、风格、预算或身材偏好，我会先从 Java 商品库筛选，再给你推荐。" }
];

export const initialChatFilters: ChatFilters = { category: "", style: "", season: "", budgetMax: "" };

export function requestFiltersFromResolvedIntent(
  resolvedIntent?: DemandIntent,
  fallbackFilters: Partial<AssistantChatRequest> = {}
): Partial<AssistantChatRequest> {
  if (!resolvedIntent) {
    return fallbackFilters;
  }

  const gender = resolvedIntent.targetGender === "male" || resolvedIntent.targetGender === "female" ? resolvedIntent.targetGender : undefined;

  return {
    ...fallbackFilters,
    category: resolvedIntent.category ?? undefined,
    style: resolvedIntent.style?.[0],
    budgetMax: resolvedIntent.budgetMax ?? undefined,
    gender
  };
}

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
  const [resolvedIntent, setResolvedIntent] = useState<DemandIntent | undefined>();
  const [measurementNotice, setMeasurementNotice] = useState("");
  const internalAbortRef = useRef<AbortController | null>(null);
  const requestSequenceRef = useRef(0);

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
  const activePreferenceItems = [
    filters.category && `分类：${filters.category}`,
    filters.style && `风格：${filters.style}`,
    filters.season && `季节：${filters.season}`,
    filters.budgetMax && `预算：￥${filters.budgetMax} 以内`
  ].filter(Boolean);
  const resolvedIntentItems = [
    resolvedIntent?.targetGender && `目标性别：${resolvedIntent.targetGender}`,
    resolvedIntent?.category && `解析分类：${resolvedIntent.category}`,
    resolvedIntent?.style?.length && `解析风格：${resolvedIntent.style.join(" / ")}`,
    resolvedIntent?.season && `解析季节：${resolvedIntent.season}`,
    resolvedIntent?.fitPreferences?.length && `解析版型：${resolvedIntent.fitPreferences.join(" / ")}`,
    resolvedIntent?.subjectMeasurements?.heightCm && `咨询对象身高：${resolvedIntent.subjectMeasurements.heightCm} cm`,
    resolvedIntent?.subjectMeasurements?.weightKg && `咨询对象体重：${resolvedIntent.subjectMeasurements.weightKg} kg`,
    resolvedIntent?.budgetMax && `解析预算：￥${resolvedIntent.budgetMax} 以内`
  ].filter(Boolean);
  const latestUserMessage = [...messages].reverse().find((message) => message.role === "user")?.content;

  function orderCandidatesByRecommendations(
    candidates: RecommendationCandidate[],
    spuIds: number[],
    recommendedItems: RecommendedItem[] = []
  ) {
    const idOrder = new Map(spuIds.map((id, index) => [id, index]));
    const skuOrder = new Map(
      recommendedItems
        .filter((item) => item.skuId !== undefined)
        .map((item, index) => [`${item.spuId}:${item.skuId}`, index])
    );

    if (!idOrder.size && !skuOrder.size) {
      return attachRecommendationReasons(candidates, recommendedItems);
    }

    return [...candidates].sort((first, second) => {
      const firstOrder = skuOrder.get(`${first.spuId}:${first.skuId}`) ?? idOrder.get(first.spuId);
      const secondOrder = skuOrder.get(`${second.spuId}:${second.skuId}`) ?? idOrder.get(second.spuId);

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
    }).map((candidate) => attachRecommendationReason(candidate, recommendedItems));
  }

  function attachRecommendationReasons(candidates: RecommendationCandidate[], recommendedItems: RecommendedItem[]) {
    return candidates.map((candidate) => attachRecommendationReason(candidate, recommendedItems));
  }

  function attachRecommendationReason(candidate: RecommendationCandidate, recommendedItems: RecommendedItem[]) {
    const matched =
      recommendedItems.find((item) => item.skuId !== undefined && item.spuId === candidate.spuId && item.skuId === candidate.skuId) ??
      recommendedItems.find((item) => item.spuId === candidate.spuId);

    if (!matched) {
      return candidate;
    }

    return {
      ...candidate,
      recommendationReason: matched.reason,
      rankScore: matched.rankScore,
      outfitRole: matched.outfitRole
    };
  }

  async function updateRecommendations(
    spuIds: number[],
    recommendedItems: RecommendedItem[] = [],
    recommendationId?: string,
    recommendationStatus: RecommendationStatus = "BROWSE_FALLBACK",
    intentSnapshot?: DemandIntent,
    localRequestId?: number
  ) {
    try {
      const candidates = recommendationId
        ? await api.recommendationSnapshot(recommendationId)
        : await api.recommendationCandidates(requestFilters);
      if (localRequestId !== undefined && localRequestId !== requestSequenceRef.current) return;
      onRecommendations(orderCandidatesByRecommendations(candidates, spuIds, recommendedItems), {
        recommendedItems,
        recommendationId,
        recommendationStatus,
        resolvedIntent: intentSnapshot
      });
    } catch {
      if (localRequestId !== undefined && localRequestId !== requestSequenceRef.current) return;
      setError("候选快照读取失败，请重试");
      onRecommendations([], {
        recommendedItems: [],
        recommendationId,
        recommendationStatus: "FAILED",
        resolvedIntent: intentSnapshot
      });
    }
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
    const effectiveRequestFilters = requestFilters;
    const localRequestId = ++requestSequenceRef.current;

    try {
      await streamAssistantChat(
        { ...effectiveRequestFilters, threadId, message },
        async (event) => {
          if (localRequestId !== requestSequenceRef.current) return;
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
            return;
          }
          if (event.type === "done") {
            if (event.threadId) {
              setThreadId(event.threadId);
            }
            setResolvedIntent(event.resolvedIntent);
            if (event.answer) {
              setMessages((current) => {
                const next = [...current];
                const last = next[next.length - 1];
                next[next.length - 1] = { ...last, content: last.content || event.answer || "" };
                return next;
              });
            }
            await updateRecommendations(
              event.spuIds,
              event.recommendedItems,
              event.recommendationId,
              event.recommendationStatus ?? "BROWSE_FALLBACK",
              event.resolvedIntent,
              localRequestId
            );
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
      const fallback = await api.chat({ ...effectiveRequestFilters, threadId, message });
      setThreadId(fallback.threadId);
      setResolvedIntent(fallback.resolvedIntent);
      setMessages((current) => {
        const next = [...current];
        next[next.length - 1] = { role: "assistant", content: fallback.answer };
        return next;
      });
      await updateRecommendations(
        fallback.recommendedSpuIds,
        fallback.recommendedItems ?? [],
        fallback.recommendationId,
        fallback.recommendationStatus ?? "BROWSE_FALLBACK",
        fallback.resolvedIntent,
        localRequestId
      );
    } finally {
      setIsStreaming(false);
      abortRef.current = null;
    }
  }

  async function saveResolvedMeasurements() {
    const measurements = resolvedIntent?.subjectMeasurements;
    if (measurements?.subject !== "SELF" || measurements.heightCm === undefined || measurements.weightKg === undefined) return;
    try {
      const current = await api.bodyData();
      if (current.heightCm === measurements.heightCm && current.weightKg === measurements.weightKg) {
        setMeasurementNotice("个人资料中的身高体重已与本轮一致");
        return;
      }
      const oldValue = `${current.heightCm ?? "未设置"} cm / ${current.weightKg ?? "未设置"} kg`;
      const newValue = `${measurements.heightCm} cm / ${measurements.weightKg} kg`;
      if (!window.confirm(`确认更新身体数据？\n原值：${oldValue}\n新值：${newValue}`)) return;
      await api.updateBodyMeasurements({ heightCm: measurements.heightCm, weightKg: measurements.weightKg });
      setMeasurementNotice("已保存为我的身体数据");
    } catch {
      setMeasurementNotice("保存失败，但不影响本轮穿搭结果");
    }
  }

  return (
    <section className="chat-panel chat-panel--noir" aria-label="AI 穿搭对话">
      <div className="section-heading chat-panel__heading">
        <div>
          <p className="eyebrow">CONVERSATION / AI</p>
          <h2>当前穿搭线索</h2>
        </div>
      </div>
      {resolvedIntent?.subjectMeasurements?.subject === "SELF" &&
        resolvedIntent.subjectMeasurements.heightCm !== undefined &&
        resolvedIntent.subjectMeasurements.weightKg !== undefined && (
          <div className="measurement-save-card">
            <span>
              本轮采用 {resolvedIntent.subjectMeasurements.heightCm} cm / {resolvedIntent.subjectMeasurements.weightKg} kg
              {resolvedIntent.subjectMeasurements.normalizedFrom === "ASSUMED_JIN" &&
                `（约 ${resolvedIntent.subjectMeasurements.weightKg * 2} 斤，已换算）`}
            </span>
            <button type="button" onClick={() => void saveResolvedMeasurements()}>保存为我的身体数据</button>
            {measurementNotice && <span role="status">{measurementNotice}</span>}
          </div>
        )}
      <div className="ai-insight ai-insight--noir">
        {activePreferenceItems.length > 0 || resolvedIntentItems.length > 0 || latestUserMessage ? (
          <>
            <p>AI 正在根据这些线索筛选商品：</p>
            <ul>
              {activePreferenceItems.map((item) => (
                <li key={item}>{item}</li>
              ))}
              {resolvedIntentItems.map((item) => (
                <li key={item}>{item}</li>
              ))}
              {latestUserMessage && <li>最近需求：{latestUserMessage}</li>}
            </ul>
          </>
        ) : (
          <>
            <p>等待你的穿搭需求</p>
            <span>输入场景、预算、风格后，AI 会在这里整理你的偏好线索。</span>
          </>
        )}
      </div>
      <div className="filter-row">
        <label>
          <SlidersHorizontal size={16} />
          <input
            data-testid="chat-filter-category"
            placeholder="分类"
            value={filters.category}
            onChange={(event) => setFilters((current) => ({ ...current, category: event.target.value }))}
          />
        </label>
        <input
          data-testid="chat-filter-style"
          placeholder="风格"
          value={filters.style}
          onChange={(event) => setFilters((current) => ({ ...current, style: event.target.value }))}
        />
        <input
          data-testid="chat-filter-season"
          placeholder="季节"
          value={filters.season}
          onChange={(event) => setFilters((current) => ({ ...current, season: event.target.value }))}
        />
        <input
          data-testid="chat-filter-budget"
          placeholder="预算上限"
          value={filters.budgetMax}
          onChange={(event) => setFilters((current) => ({ ...current, budgetMax: event.target.value }))}
          inputMode="numeric"
        />
      </div>
      <div className="message-list message-list--noir" data-testid="chat-message-list">
        {messages.map((message, index) => (
          <div key={`${message.role}-${index}`} className={`message ${message.role}`} data-testid={`chat-message-${message.role}`}>
            {message.content || "正在生成..."}
          </div>
        ))}
      </div>
      {error && <p className="error-text">{error}</p>}
      <form className="chat-input chat-input--noir" onSubmit={submit}>
        <textarea
          data-testid="ai-chat-input"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="例如：明天面试，想要显瘦、预算 500 以内"
        />
        {isStreaming ? (
          <button data-testid="ai-chat-stop" type="button" onClick={() => abortRef.current?.abort()} title="停止生成">
            <Square size={18} />
          </button>
        ) : (
          <button className="primary-button" data-testid="ai-chat-submit" type="submit" title="发送">
            <Send size={18} />
          </button>
        )}
      </form>
    </section>
  );
}
