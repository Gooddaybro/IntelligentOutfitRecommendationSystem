import { useEffect, useRef } from "react";
import { ChatPanel } from "../features/assistant/ChatPanel";
import type { ChatPanelState, RecommendationResultMeta } from "../features/assistant/ChatPanel";
import { ProductCard } from "../features/catalog/ProductCard";
import type { PendingCommerceAction } from "../features/commerce-action/commerceActions";
import { api } from "../shared/api/client";
import type { BehaviorEventType, RecommendationCandidate } from "../shared/api/types";
import type { Dispatch, SetStateAction } from "react";

type AiShoppingPageProps = {
  chatState: ChatPanelState;
  recommendations: RecommendationCandidate[];
  setRecommendations: Dispatch<SetStateAction<RecommendationCandidate[]>>;
  recommendationMeta?: RecommendationResultMeta;
  setRecommendationMeta: Dispatch<SetStateAction<RecommendationResultMeta | undefined>>;
  recommendationsLoaded: boolean;
  setRecommendationsLoaded: Dispatch<SetStateAction<boolean>>;
  isRecommendationsLoading: boolean;
  setIsRecommendationsLoading: Dispatch<SetStateAction<boolean>>;
  onAction: (action: PendingCommerceAction) => void;
  onRefreshCart: () => Promise<void>;
};

export function AiShoppingPage({
  chatState,
  recommendations,
  setRecommendations,
  recommendationMeta,
  setRecommendationMeta,
  recommendationsLoaded,
  setRecommendationsLoaded,
  isRecommendationsLoading,
  setIsRecommendationsLoading,
  onAction,
  onRefreshCart
}: AiShoppingPageProps) {
  const exposureKeysRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    async function loadInitialRecommendations() {
      if (recommendationsLoaded) {
        await onRefreshCart();
        return;
      }

      setIsRecommendationsLoading(true);
      try {
        setRecommendations(await api.recommendationCandidates({}));
        setRecommendationMeta(undefined);
        setRecommendationsLoaded(true);
        await onRefreshCart();
      } finally {
        setIsRecommendationsLoading(false);
      }
    }

    void loadInitialRecommendations();
  }, [
    onRefreshCart,
    recommendationsLoaded,
    setIsRecommendationsLoading,
    setRecommendations,
    setRecommendationMeta,
    setRecommendationsLoaded
  ]);

  function behaviorEventId(eventType: BehaviorEventType) {
    return `${eventType.toLowerCase()}:${Date.now().toString(36)}:${Math.random().toString(36).slice(2, 8)}`;
  }

  function recordRecommendationEvent(
    eventType: BehaviorEventType,
    candidate: RecommendationCandidate,
    metadata?: Record<string, unknown>
  ) {
    if (!recommendationMeta?.hasAiResult || !chatState.threadId) {
      return;
    }
    void api
      .recordBehaviorEvent({
        eventId: behaviorEventId(eventType),
        eventType,
        spuId: candidate.spuId,
        skuId: candidate.skuId,
        threadId: chatState.threadId,
        metadata
      })
      .catch(() => undefined);
  }

  useEffect(() => {
    if (!recommendationMeta?.hasAiResult || !chatState.threadId) {
      return;
    }
    recommendations.forEach((candidate, index) => {
      const key = `${chatState.threadId}:${candidate.spuId}:${candidate.skuId}:exposed`;
      if (exposureKeysRef.current.has(key)) {
        return;
      }
      exposureKeysRef.current.add(key);
      recordRecommendationEvent("RECOMMENDATION_EXPOSED", candidate, { position: index + 1 });
    });
  }, [chatState.threadId, recommendationMeta?.hasAiResult, recommendations]);

  const actionMetadata = recommendationMeta?.hasAiResult
    ? { source: "ASSISTANT_RECOMMENDATION" as const, threadId: chatState.threadId }
    : undefined;
  const recordEvent = (event: { eventType: BehaviorEventType; candidate: RecommendationCandidate; metadata?: Record<string, unknown> }) =>
    recordRecommendationEvent(event.eventType, event.candidate, event.metadata);

  return (
    <main className="workbench outfit-workbench noir-workbench" data-testid="ai-workbench" data-layout="editorial-stage">
      <section className="workbench-chat-column">
        <ChatPanel
          onRecommendations={(items, meta) => {
            setRecommendations(items);
            setRecommendationMeta(meta);
          }}
          state={chatState}
        />
      </section>

      <section className="recommendation-stage" data-testid="recommendation-panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">CURATED / AI</p>
            <h2>为你策展的单品</h2>
          </div>
          <span>{isRecommendationsLoading ? "正在筛选" : `${recommendations.length} 件`}</span>
        </div>
        {recommendationMeta?.hasAiResult && !recommendationMeta.hasStrongMatch && (
          <p className="recommendation-notice">AI 暂时没有选出强匹配商品，你可以继续浏览当前候选。</p>
        )}
        {isRecommendationsLoading && <div className="recommendation-stage__skeleton" aria-label="推荐商品加载中" />}
        {!isRecommendationsLoading && recommendations.length === 0 && (
          <p className="recommendation-stage__empty">告诉 AI 你的场景、风格或预算，专属推荐会在这里出现。</p>
        )}
        {recommendations.length > 0 && (
          <div className="recommendation-stage__grid">
            <div className="recommendation-stage__featured">
              <ProductCard candidate={recommendations[0]} variant="featured" onAction={onAction} actionMetadata={actionMetadata} position={1} onBehaviorEvent={recordEvent} />
            </div>
            <div className="recommendation-stage__supporting">
              {recommendations.slice(1, 3).map((candidate, index) => (
                <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} variant="supporting" onAction={onAction} actionMetadata={actionMetadata} position={index + 2} onBehaviorEvent={recordEvent} />
              ))}
            </div>
          </div>
        )}
        {recommendations.length > 3 && (
          <div className="product-grid recommendation-stage__remaining">
            {recommendations.slice(3).map((candidate, index) => (
              <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} actionMetadata={actionMetadata} position={index + 4} onBehaviorEvent={recordEvent} />
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
