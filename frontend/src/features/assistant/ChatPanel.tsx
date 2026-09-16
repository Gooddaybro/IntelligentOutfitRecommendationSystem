import { Send, SlidersHorizontal, Square } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "../../shared/api/client";
import { streamAssistantChat } from "../../shared/api/assistantStream";
import type { AssistantProgressEvent } from "../../shared/api/assistantStream";
import type { AgentMode, AssistantChatRequest, DemandIntent, RecommendationCandidate, RecommendationStatus, RecommendedItem } from "../../shared/api/types";
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
  agentMode?: AgentMode;
  setAgentMode?: Dispatch<SetStateAction<AgentMode>>;
  activeRunId?: string;
  setActiveRunId?: Dispatch<SetStateAction<string | undefined>>;
  progress?: AssistantProgressEvent[];
  setProgress?: Dispatch<SetStateAction<AssistantProgressEvent[]>>;
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
  agentMode?: AgentMode;
  runId?: string;
  requirements?: unknown[];
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

/** 将 Java done 中的已核验事实转换成共用商品卡片所需的展示模型。 */
export function validatedRecommendedItemsToCandidates(items: RecommendedItem[] = []): RecommendationCandidate[] {
  const seen = new Set<string>();
  return items.flatMap((item) => {
    const spuId = Number(item.spuId);
    const skuId = item.skuId === undefined ? NaN : Number(item.skuId);
    const salePrice = item.salePrice === undefined ? NaN : Number(item.salePrice);
    const name = item.name?.trim();
    if (!Number.isInteger(spuId) || spuId <= 0 || !Number.isInteger(skuId) || skuId <= 0
        || !name || !Number.isFinite(salePrice)) {
      return [];
    }

    const key = `${spuId}:${skuId}`;
    if (seen.has(key)) {
      return [];
    }
    seen.add(key);

    return [{
      spuId,
      skuId,
      spuCode: `PRO-${spuId}`,
      name,
      categoryName: "已核验商品",
      mainImageUrl: item.mainImageUrl,
      color: item.color,
      size: item.size,
      salePrice,
      availableStock: item.availableStock,
      stockStatus: item.availableStock === undefined
        ? undefined
        : item.availableStock > 0 ? "有货" : "暂时无货",
      recommendationReason: item.reason,
      rankScore: item.rankScore,
      outfitRole: item.outfitRole
    }];
  });
}

type ChatPanelProps = {
  onRecommendations: (items: RecommendationCandidate[], meta?: RecommendationResultMeta) => void;
  onRecommendationsReset?: () => void;
  state?: ChatPanelState;
};

export function ChatPanel({ onRecommendations, onRecommendationsReset, state }: ChatPanelProps) {
  const [internalMessages, setInternalMessages] = useState<ChatMessage[]>(initialChatMessages);
  const [internalDraft, setInternalDraft] = useState("");
  const [internalFilters, setInternalFilters] = useState<ChatFilters>(initialChatFilters);
  const [internalAgentMode, setInternalAgentMode] = useState<AgentMode>("lite");
  const [internalActiveRunId, setInternalActiveRunId] = useState<string | undefined>();
  const [internalProgress, setInternalProgress] = useState<AssistantProgressEvent[]>([]);
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
  const agentMode = state?.agentMode ?? internalAgentMode;
  const setAgentMode = state?.setAgentMode ?? setInternalAgentMode;
  const activeRunId = state?.activeRunId ?? internalActiveRunId;
  const setActiveRunId = state?.setActiveRunId ?? setInternalActiveRunId;
  const progress = state?.progress ?? internalProgress;
  const setProgress = state?.setProgress ?? setInternalProgress;
  const threadId = state?.threadId ?? internalThreadId;
  const setThreadId = state?.setThreadId ?? setInternalThreadId;
  const isStreaming = state?.isStreaming ?? internalIsStreaming;
  const setIsStreaming = state?.setIsStreaming ?? setInternalIsStreaming;
  const error = state?.error ?? internalError;
  const setError = state?.setError ?? setInternalError;
  const abortRef = state?.abortRef ?? internalAbortRef;
  const activeRunIdRef = useRef<string | undefined>(activeRunId);

  useEffect(() => {
    activeRunIdRef.current = activeRunId;
  }, [activeRunId]);

  useEffect(() => () => {
    requestSequenceRef.current += 1;
    abortRef.current?.abort();
    abortRef.current = null;
  }, [abortRef]);

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
    localRequestId?: number,
    resultMode: AgentMode = "lite",
    resultRunId?: string,
    requirements?: unknown[]
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
        resolvedIntent: intentSnapshot,
        agentMode: resultMode,
        runId: resultRunId,
        requirements
      });
    } catch {
      if (localRequestId !== undefined && localRequestId !== requestSequenceRef.current) return;
      setError("候选快照读取失败，请重试");
      onRecommendations([], {
        recommendedItems: [],
        recommendationId,
        recommendationStatus: "FAILED",
        resolvedIntent: intentSnapshot,
        agentMode: resultMode,
        runId: resultRunId,
        requirements
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
    setResolvedIntent(undefined);
    setMeasurementNotice("");
    setProgress([]);
    setActiveRunId(undefined);
    activeRunIdRef.current = undefined;
    onRecommendationsReset?.();
    setMessages((current) => [...current, { role: "user", content: message }, { role: "assistant", content: "" }]);
    setIsStreaming(true);
    const localAbortController = new AbortController();
    abortRef.current = localAbortController;
    const effectiveRequestFilters = requestFilters;
    const requestMode = agentMode;
    const request = requestMode === "pro"
      ? { ...effectiveRequestFilters, threadId, message, agentMode: "pro" as const }
      : { ...effectiveRequestFilters, threadId, message };
    const localRequestId = ++requestSequenceRef.current;

    try {
      await streamAssistantChat(
        request,
        async (event) => {
          if (localRequestId !== requestSequenceRef.current) return;
          if (event.type === "thread") {
            setThreadId(event.threadId);
            if (event.runId) {
              activeRunIdRef.current = event.runId;
              setActiveRunId(event.runId);
            }
            if (event.agentMode && event.agentMode !== requestMode) return;
          }
          if (event.type === "progress") {
            if (requestMode !== "pro" || event.runId !== activeRunIdRef.current) {
              return;
            }
            setProgress((current) => {
              const latest = current[current.length - 1];
              if (latest && event.sequence <= latest.sequence) {
                return current;
              }
              return [...current, event].slice(-12);
            });
            return;
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
            if ((event.agentMode && event.agentMode !== requestMode)
                || (requestMode === "pro" && event.runId && event.runId !== activeRunIdRef.current)) {
              return;
            }
            if (event.threadId) {
              setThreadId(event.threadId);
            }
            if (event.runId) {
              activeRunIdRef.current = event.runId;
              setActiveRunId(event.runId);
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
            if (requestMode === "pro") {
              onRecommendations(validatedRecommendedItemsToCandidates(event.recommendedItems), {
                recommendedItems: event.recommendedItems,
                recommendationId: event.recommendationId,
                recommendationStatus: event.recommendationStatus ?? "FAILED",
                resolvedIntent: event.resolvedIntent,
                agentMode: "pro",
                runId: event.runId,
                requirements: event.requirements
              });
            } else {
              await updateRecommendations(
                event.spuIds,
                event.recommendedItems,
                event.recommendationId,
                event.recommendationStatus ?? "BROWSE_FALLBACK",
                event.resolvedIntent,
                localRequestId,
                requestMode,
                event.runId,
                event.requirements
              );
            }
          }
          if (event.type === "error") {
            if (event.runId && activeRunIdRef.current && event.runId !== activeRunIdRef.current) return;
            setError(event.message);
          }
        },
        localAbortController.signal,
        requestMode
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
      if (requestMode === "pro") {
        return;
      }
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
      if (localRequestId === requestSequenceRef.current && abortRef.current === localAbortController) {
        setIsStreaming(false);
        abortRef.current = null;
      }
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
        <label className="assistant-mode-control" htmlFor="assistant-agent-mode">
          <span>AI 模式</span>
          <select
            id="assistant-agent-mode"
            data-testid="agent-mode-selector"
            value={agentMode}
            onChange={(event) => {
              if (isStreaming) {
                event.currentTarget.value = agentMode;
                return;
              }
              const nextMode = event.currentTarget.value;
              if (nextMode === "lite" || nextMode === "pro") {
                setAgentMode(nextMode);
              } else {
                event.currentTarget.value = agentMode;
              }
            }}
            disabled={isStreaming}
          >
            <option value="lite">Lite · 快速推荐</option>
            <option value="pro">Pro · 动态调度</option>
          </select>
        </label>
      </div>
      <p className="assistant-mode-hint" data-testid="agent-mode-hint">
        {agentMode === "pro"
          ? "Pro 会按中间结果动态调用工具，完成 Java 商品事实校验后展示卡片。"
          : "Lite 使用现有固定流程，适合快速获取基础推荐。"}
      </p>
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
      {agentMode === "pro" && progress.length > 0 && (
        <ol className="assistant-progress" data-testid="assistant-progress" aria-live="polite">
          {progress.map((item) => (
            <li key={`${item.runId}-${item.sequence}`} data-testid="assistant-progress-item">
              <span>{item.stage === "completed" ? "已完成" : "进行中"}</span>
              <span>{item.message}</span>
            </li>
          ))}
        </ol>
      )}
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
