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
    if (!recommendationMeta?.hasAiResult
        || !recommendationMeta.recommendationId
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
    return recommendationMeta?.recommendedItems?.some((item) =>
      item.spuId === candidate.spuId && (item.skuId === undefined || item.skuId === candidate.skuId)
    ) ?? false;
  }

  function actionMetadataFor(candidate: RecommendationCandidate) {
    return recommendationMeta?.hasAiResult && isAttributedCandidate(candidate)
    ? {
        source: "ASSISTANT_RECOMMENDATION" as const,
        threadId: chatState.threadId,
        recommendationId: recommendationMeta.recommendationId
      }
    : undefined;
  }
  const recordEvent = (event: { eventType: BehaviorEventType; candidate: RecommendationCandidate; metadata?: Record<string, unknown> }) =>
    recordRecommendationEvent(event.eventType, event.candidate, event.metadata);
  const status = recommendationMeta?.recommendationStatus;
  const isOutfit = recommendationMeta?.resolvedIntent?.requestType === "OUTFIT_ADVICE";
  const mentionedKeys = new Set(
    recommendationMeta?.mentionedItems?.map((item) => `${item.spuId}:${item.skuId}`) ?? []
  );
  // AI 完成后只展示 Java 明确返回的商品身份；没有结构化 ID 时绝不从回答文本猜商品。
  const boundCandidates = recommendations.filter((candidate) =>
    mentionedKeys.has(`${candidate.spuId}:${candidate.skuId}`)
  );
  const displayCandidates = recommendationMeta?.hasAiResult ? boundCandidates : recommendations;
  const outfitGroups = [
    ["TOP", "上装"],
    ["BOTTOM", "下装"],
    ["OUTER", "外搭"],
    ["SHOES", "鞋履"],
    ["ACCESSORY", "配饰"]
  ] as const;

  function isMentionedCandidate(candidate: RecommendationCandidate) {
    return mentionedKeys.has(`${candidate.spuId}:${candidate.skuId}`);
  }

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
          <span>
            {isRecommendationsLoading
              ? "正在筛选"
              : recommendationMeta?.hasAiResult
                ? `已绑定 ${boundCandidates.length} 件 · 候选 ${recommendations.length} 件`
                : `${recommendations.length} 件`}
          </span>
        </div>
        {status === "STRONG_MATCH" && <p className="recommendation-notice">已按当前需求推荐。</p>}
        {status === "WEAK_FALLBACK" && boundCandidates.length > 0 && (
          <p className="recommendation-notice">暂无强匹配，以下商品已由 Java 精确绑定为对话提及，不作 AI 推荐归因。</p>
        )}
        {status === "EMPTY" && <p className="recommendation-notice">当前条件下没有候选商品，可以尝试放宽一个条件。</p>}
        {status === "ERROR" && <p className="error-text">候选快照读取失败，请重试；未沿用上一次结果。</p>}
        {isRecommendationsLoading && <div className="recommendation-stage__skeleton" aria-label="推荐商品加载中" />}
        {!isRecommendationsLoading && !recommendationMeta?.hasAiResult && recommendations.length === 0 && (
          <p className="recommendation-stage__empty">告诉 AI 你的场景、风格或预算，专属推荐会在这里出现。</p>
        )}
        {!isRecommendationsLoading && status === "WEAK_FALLBACK" && boundCandidates.length === 0 && (
          <p className="recommendation-stage__empty recommendation-stage__empty--bound">
            本轮文字建议未绑定真实商品，暂不提供加购。你可以补充品类、预算或尺码后重新推荐。
          </p>
        )}
        {isOutfit && recommendationMeta?.hasAiResult && (
          <div className="outfit-groups" data-testid="outfit-groups">
            {outfitGroups.map(([role, label]) => {
              const items = boundCandidates.filter((candidate) => candidate.outfitRole === role);
              return (
                <section key={role} className="outfit-group" aria-label={label}>
                  <h3>{label}</h3>
                  <p>{items.length > 0 ? items.map((candidate) => candidate.name).join(" / ") : "暂未绑定商品"}</p>
                </section>
              );
            })}
          </div>
        )}
        {recommendationMeta?.hasAiResult && displayCandidates.length > 0 && (
          <section className="bound-products" aria-labelledby="bound-products-heading">
            <div className="bound-products__heading">
              <p className="eyebrow">BOUND PRODUCTS</p>
              <h3 id="bound-products-heading">本轮推荐单品</h3>
            </div>
            <div className="product-grid bound-products__grid">
              {displayCandidates.map((candidate, index) => (
                <ProductCard
                  key={`${candidate.spuId}-${candidate.skuId}`}
                  candidate={candidate}
                  onAction={onAction}
                  actionMetadata={actionMetadataFor(candidate)}
                  position={index + 1}
                  onBehaviorEvent={recordEvent}
                  recommendationStatus={status}
                  isAttributed={isAttributedCandidate(candidate)}
                  isMentioned={isMentionedCandidate(candidate)}
                />
              ))}
            </div>
          </section>
        )}
        {!recommendationMeta?.hasAiResult && displayCandidates.length > 0 && (
          <div className="recommendation-stage__grid">
            <div className="recommendation-stage__featured">
              <ProductCard candidate={displayCandidates[0]} variant="featured" onAction={onAction} actionMetadata={actionMetadataFor(displayCandidates[0])} position={1} onBehaviorEvent={recordEvent} recommendationStatus={status} isAttributed={isAttributedCandidate(displayCandidates[0])} />
            </div>
            <div className="recommendation-stage__supporting">
              {displayCandidates.slice(1, 3).map((candidate, index) => (
                <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} variant="supporting" onAction={onAction} actionMetadata={actionMetadataFor(candidate)} position={index + 2} onBehaviorEvent={recordEvent} recommendationStatus={status} isAttributed={isAttributedCandidate(candidate)} />
              ))}
            </div>
          </div>
        )}
        {!recommendationMeta?.hasAiResult && displayCandidates.length > 3 && (
          <div className="product-grid recommendation-stage__remaining">
            {displayCandidates.slice(3).map((candidate, index) => (
              <ProductCard key={`${candidate.spuId}-${candidate.skuId}`} candidate={candidate} onAction={onAction} actionMetadata={actionMetadataFor(candidate)} position={index + 4} onBehaviorEvent={recordEvent} recommendationStatus={status} isAttributed={isAttributedCandidate(candidate)} />
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
