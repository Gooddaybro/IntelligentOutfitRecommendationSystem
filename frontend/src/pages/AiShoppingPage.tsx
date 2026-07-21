import { useEffect } from "react";
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
    if (!recommendationMeta?.recommendationId
        || !chatState.threadId
        || !isAttributedCandidate(candidate)) {
      return;
    }
    void api
      .recordBehaviorEvent({
        eventId: behaviorEventId(eventType),
        eventType,
        spuId: candidate.spuId,
        skuId: candidate.skuId,
        threadId: chatState.threadId,
        recommendationId: recommendationMeta.recommendationId,
        metadata
      })
      .catch(() => undefined);
  }

  function isAttributedCandidate(candidate: RecommendationCandidate) {
    const attributionEligible = status === "STRONG_MATCH" || status === "PARTIAL_MATCH";
    return attributionEligible && (recommendationMeta?.recommendedItems?.some((item) =>
      item.spuId === candidate.spuId && (item.skuId === undefined || item.skuId === candidate.skuId)
    ) ?? false);
  }

  function actionMetadataFor(candidate: RecommendationCandidate) {
    return isAttributedCandidate(candidate)
    ? {
        source: "ASSISTANT_RECOMMENDATION" as const,
        threadId: chatState.threadId,
        recommendationId: recommendationMeta?.recommendationId
      }
    : undefined;
  }
  const recordEvent = (event: { eventType: BehaviorEventType; candidate: RecommendationCandidate; metadata?: Record<string, unknown> }) =>
    recordRecommendationEvent(event.eventType, event.candidate, event.metadata);
  const status = recommendationMeta?.recommendationStatus;
  const isOutfit = recommendationMeta?.resolvedIntent?.requestType === "OUTFIT_ADVICE";
  const groupedOutfit = isOutfit
    && (status === "STRONG_MATCH" || status === "PARTIAL_MATCH")
    && recommendations.some((candidate) => candidate.outfitRole);
  const visibleRecommendations = status === "EMPTY" || status === "FAILED" ? [] : recommendations;
  const outfitGroups = [
    ["TOP", "上装"],
    ["BOTTOM", "下装"],
    ["OUTER", "外搭"],
    ["SHOES", "鞋履"],
    ["ACCESSORY", "配饰"]
  ] as const;

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
          <span>{isRecommendationsLoading ? "正在筛选" : `${visibleRecommendations.length} 件`}</span>
        </div>
        {status === "STRONG_MATCH" && <p className="recommendation-notice">已按当前需求推荐。</p>}
        {status === "PARTIAL_MATCH" && <p className="recommendation-notice">已展示部分真实匹配，其余搭配位置请参考文字建议。</p>}
        {status === "BROWSE_FALLBACK" && <p className="recommendation-notice">暂无强匹配，以下为同一候选快照中的可浏览商品，不作 AI 归因。</p>}
        {status === "EMPTY" && <p className="recommendation-notice">当前条件下没有候选商品，可以尝试放宽一个条件。</p>}
        {status === "FAILED" && <p className="error-text">候选快照读取失败，请重试；未沿用上一次结果。</p>}
        {isRecommendationsLoading && <div className="recommendation-stage__skeleton" aria-label="推荐商品加载中" />}
        {!isRecommendationsLoading && visibleRecommendations.length === 0 && (
          <p className="recommendation-stage__empty">告诉 AI 你的场景、风格或预算，专属推荐会在这里出现。</p>
        )}
        {groupedOutfit && visibleRecommendations.length > 0 && (
          <div className="outfit-groups" data-testid="outfit-groups">
            {outfitGroups.map(([role, label]) => {
              const items = visibleRecommendations.filter((candidate) => candidate.outfitRole === role);
              return (
                <section key={role} className="outfit-group" aria-label={label}>
                  <h3>{label}</h3>
                  {items.length === 0 ? <p>本组暂无真实匹配商品，请参考对话中的文字搭配建议。</p> : (
                    <div className="product-grid">
                      {items.map((candidate, index) => (
                        <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} actionMetadata={actionMetadataFor(candidate)} position={index + 1} onBehaviorEvent={recordEvent} recommendationStatus={status} isAttributed={isAttributedCandidate(candidate)} />
                      ))}
                    </div>
                  )}
                </section>
              );
            })}
          </div>
        )}
        {status === "BROWSE_FALLBACK" && visibleRecommendations.length > 0 && (
          <div className="product-grid recommendation-stage__browse-fallback">
            {visibleRecommendations.map((candidate, index) => (
              <ProductCard
                key={`${candidate.spuId}-${candidate.skuId}`}
                candidate={candidate}
                onAction={onAction}
                position={index + 1}
                onBehaviorEvent={recordEvent}
                recommendationStatus={status}
                isAttributed={false}
              />
            ))}
          </div>
        )}
        {!groupedOutfit && status !== "BROWSE_FALLBACK" && visibleRecommendations.length > 0 && (
          <div className="recommendation-stage__grid">
            <div className="recommendation-stage__featured">
              <ProductCard candidate={visibleRecommendations[0]} variant="featured" onAction={onAction} actionMetadata={actionMetadataFor(visibleRecommendations[0])} position={1} onBehaviorEvent={recordEvent} recommendationStatus={status} isAttributed={isAttributedCandidate(visibleRecommendations[0])} />
            </div>
            <div className="recommendation-stage__supporting">
              {visibleRecommendations.slice(1, 3).map((candidate, index) => (
                <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} variant="supporting" onAction={onAction} actionMetadata={actionMetadataFor(candidate)} position={index + 2} onBehaviorEvent={recordEvent} recommendationStatus={status} isAttributed={isAttributedCandidate(candidate)} />
              ))}
            </div>
          </div>
        )}
        {!groupedOutfit && status !== "BROWSE_FALLBACK" && visibleRecommendations.length > 3 && (
          <div className="product-grid recommendation-stage__remaining">
            {visibleRecommendations.slice(3).map((candidate, index) => (
              <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} actionMetadata={actionMetadataFor(candidate)} position={index + 4} onBehaviorEvent={recordEvent} recommendationStatus={status} isAttributed={isAttributedCandidate(candidate)} />
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
